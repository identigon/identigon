<#
.SYNOPSIS
    Evaluates Identigon end to end. Native PowerShell port of run-quickstart.sh (POSIX sh) for
    Windows without a POSIX shell handy — behaviour matches that script exactly; see it for the
    fuller narrative comments.

.DESCRIPTION
    Two ways to use this:

      .\run-quickstart.ps1
          One-shot demo: starts a throwaway PostgreSQL container, loads schema.sql + seed-data.sql,
          builds the CLI jar if needed, runs discover -> scaffold -> run against the finished
          policy.yaml already checked into this directory, and prints the fabricated rows. Nothing
          to decide, nothing to author -- just to see the tool work.

      .\run-quickstart.ps1 setup
      .\run-quickstart.ps1 run
          The real authoring workflow: 'setup' gets you a scaffolded draft and stops there --
          classify it yourself with the identigon-policy-author Agent Skill (or by hand), then
          'run' anonymises against whatever you ended up with and shows the DPIA report.

      .\run-quickstart.ps1 clean
          Stops and removes the throwaway container and any generated draft/report files.

    Requires: Docker, Java 25. Nothing else -- psql isn't required on the host, every SQL statement
    runs through `docker exec` against the container's own psql.

.PARAMETER Command
    demo (default), setup, run, or clean.

.PARAMETER Policy
    Only meaningful with 'run': path to the policy.yaml to anonymise with. Defaults to
    .quickstart-work\policy.draft.yaml if 'setup' left one there, else the checked-in policy.yaml.
#>
param(
    [Parameter(Position = 0)]
    [ValidateSet('demo', 'setup', 'run', 'clean')]
    [string]$Command = 'demo',

    [Parameter(Position = 1)]
    [string]$Policy
)

$ErrorActionPreference = 'Stop'

$ScriptDir = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir '..')).Path
$WorkDir = Join-Path $ScriptDir '.quickstart-work'

$ContainerName = 'identigon-quickstart'
$PgImage = 'postgres:18-alpine'
$PgPassword = 'postgres'
$PgPort = 55432   # non-default, to avoid colliding with a Postgres you may already have on 5432
$SourceDb = 'quickstart_source'
$TargetDb = 'quickstart_target'
$SourceUrl = "jdbc:postgresql://localhost:$PgPort/$SourceDb"
$TargetUrl = "jdbc:postgresql://localhost:$PgPort/$TargetDb"

$Jar = Join-Path $RepoRoot 'effigies\build\libs\identigon.jar'

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor White
}

function Fail([string]$Message) {
    Write-Host ""
    Write-Host "FAILED: $Message" -ForegroundColor Red
    exit 1
}

# Runs a native command (docker/java/gradlew) and fails the script the same way a failing command
# under `set -e` would in the .sh version -- PowerShell does NOT stop on a nonzero exit code from a
# native executable by itself, even with $ErrorActionPreference = 'Stop'.
#
# Deliberately a SIMPLE function (no param() block, no CmdletBinding) using the raw $args array --
# an advanced function (one with a [Parameter(...)]-attributed param block) gains PowerShell's
# common parameters, whose prefix matching then intercepts short native flags before they ever
# reach the real command: docker's own `-p` ambiguously prefix-matches both -ProgressAction and
# -PipelineVariable, `-d` silently binds to -Debug, `-e` ambiguously matches -ErrorAction/
# -ErrorVariable, and so on. A simple function does none of that -- $args is positional only.
function Invoke-Checked {
    $exe = $args[0]
    $rest = if ($args.Count -gt 1) { $args[1..($args.Count - 1)] } else { @() }
    & $exe @rest
    if ($LASTEXITCODE -ne 0) {
        throw "$exe $($rest -join ' ') exited with code $LASTEXITCODE"
    }
}

function Test-RequiredTools {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Fail "Docker is required — install it and make sure the daemon is running."
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Fail "Java 25 is required."
    }
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        Fail "Docker doesn't seem to be running — start Docker and try again."
    }
}

function Test-ContainerRunning {
    $id = docker ps -q -f "name=^$ContainerName`$" 2>$null
    return [bool]$id
}

function Invoke-PsqlInContainer {
    # Not $Args: that's PowerShell's own reserved automatic-variable name, and a parameter that
    # reuses it silently breaks splatting it onward (confirmed live -- @Args vanished into the
    # next call instead of expanding). Any other name works correctly.
    param([string[]]$PsqlArgs)
    Invoke-Checked docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U postgres @PsqlArgs
}

# Starts a fresh container and loads schema + seed data. Always wipes any previous container first
# — this is only called when there's no existing state worth preserving (see call sites).
function Start-FreshContainer {
    Write-Step "Starting a throwaway PostgreSQL container ($ContainerName, port $PgPort)"
    docker rm -f $ContainerName *> $null
    Invoke-Checked docker run --rm -d --name $ContainerName `
        -e "POSTGRES_PASSWORD=$PgPassword" -p "${PgPort}:5432" $PgImage | Out-Null

    Write-Step "Waiting for PostgreSQL to accept connections"
    # Requires TWO consecutive successful checks, not one: the official image briefly starts a
    # temporary internal instance (for initdb) that also answers pg_isready on the same socket,
    # then stops it and restarts the real listener -- a single success can land inside that
    # window, right before the socket briefly disappears during the restart.
    $ready = $false
    $readyCount = 0
    for ($i = 0; $i -lt 60; $i++) {
        docker exec $ContainerName pg_isready -U postgres *> $null
        if ($LASTEXITCODE -eq 0) {
            $readyCount++
            if ($readyCount -ge 2) { $ready = $true; break }
        } else {
            $readyCount = 0
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { Fail "PostgreSQL never became ready." }

    Write-Step "Creating the source and target databases"
    Invoke-PsqlInContainer @('-c', "CREATE DATABASE $SourceDb") | Out-Null
    Invoke-PsqlInContainer @('-c', "CREATE DATABASE $TargetDb") | Out-Null

    Write-Step "Loading the schema into both databases, and the sample data into the source only"
    Get-Content -Raw (Join-Path $ScriptDir 'schema.sql') | docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U postgres -d $SourceDb | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "loading schema.sql into $SourceDb failed" }
    Get-Content -Raw (Join-Path $ScriptDir 'schema.sql') | docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U postgres -d $TargetDb | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "loading schema.sql into $TargetDb failed" }
    Get-Content -Raw (Join-Path $ScriptDir 'seed-data.sql') | docker exec -i $ContainerName psql -v ON_ERROR_STOP=1 -U postgres -d $SourceDb | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "loading seed-data.sql into $SourceDb failed" }
}

function Build-JarIfMissing {
    if (-not (Test-Path $Jar)) {
        Write-Step "Building the Identigon CLI jar (first run only — this can take a minute)"
        Push-Location $RepoRoot
        try {
            Invoke-Checked (Join-Path $RepoRoot 'gradlew.bat') ':effigies:assemble' '-q'
        } finally {
            Pop-Location
        }
        if (-not (Test-Path $Jar)) { Fail "Build finished but $Jar wasn't produced — see the Gradle output above." }
    }
}

function Show-Results([string]$ReportDir) {
    Write-Step "Result: a few rows from the anonymised target database"
    Invoke-PsqlInContainer @('-d', $TargetDb, '-c', 'SELECT full_name, email, nino, bank_account, date_of_birth FROM customers;')
    Invoke-PsqlInContainer @('-d', $TargetDb, '-c', 'SELECT ordered_on, shipped_on FROM orders ORDER BY id;')
    Invoke-PsqlInContainer @('-d', $TargetDb, '-c', 'SELECT category, notes FROM support_tickets;')

    Write-Host ""
    Write-Host "DPIA report (open the .html for a presentation-ready view): $ReportDir\dpia-report.html"
    Write-Host ""
    Write-Host "The throwaway container is still running so you can poke around further:"
    Write-Host "  docker exec -it $ContainerName psql -U postgres -d $TargetDb"
    Write-Host ""
    Write-Host "When you're done: .\run-quickstart.ps1 clean"
}

function Invoke-Demo {
    Test-RequiredTools
    if (Test-Path $WorkDir) {
        Fail "$WorkDir exists from a previous 'setup' — run '.\run-quickstart.ps1 run' to finish that, or '.\run-quickstart.ps1 clean' first if you want the one-shot demo instead."
    }
    $demoDir = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName())
    New-Item -ItemType Directory -Path $demoDir | Out-Null

    try {
        Start-FreshContainer
        Build-JarIfMissing

        $env:IDENTIGON_SOURCE_PASSWORD = $PgPassword
        $env:IDENTIGON_TARGET_PASSWORD = $PgPassword

        Write-Step "Step 1/3 — discover: reading the source schema (metadata only, no row values)"
        Invoke-Checked java -jar $Jar discover --source-url $SourceUrl --source-user postgres

        Write-Step "Step 2/3 — scaffold: what Identigon can classify on its own"
        Invoke-Checked java -jar $Jar scaffold --source-url $SourceUrl --source-user postgres --out (Join-Path $demoDir 'policy.draft.yaml')
        Write-Host "(Written to $demoDir\policy.draft.yaml — open it to see the suggestions and TODOs."
        Write-Host " This one-shot demo uses the finished policy.yaml in this directory instead of the draft;"
        Write-Host " run '.\run-quickstart.ps1 setup' if you want to author the draft yourself.)"

        Write-Step "Step 3/3 — run: anonymising the clone"
        Push-Location $demoDir
        try {
            Invoke-Checked java -jar $Jar run --policy (Join-Path $ScriptDir 'policy.yaml') `
                --source-url $SourceUrl --source-user postgres `
                --target-url $TargetUrl --target-user postgres
        } finally {
            Pop-Location
        }

        Show-Results $demoDir
        Write-Host ""
        Write-Host "Worth comparing against seed-data.sql: every name/e-mail/phone/NINO/bank-account is fabricated,"
        Write-Host "dates on each order are shifted together (shipped_on stays on-or-after ordered_on), and every"
        Write-Host "support-ticket note is cleared while its category survives unchanged."
    } catch {
        Write-Host ""
        Write-Host "Something went wrong: $_" -ForegroundColor Red
        Write-Host "The throwaway container ($ContainerName) was left running for inspection — remove it with: .\run-quickstart.ps1 clean" -ForegroundColor Red
        exit 1
    }
}

function Invoke-Setup {
    Test-RequiredTools
    if (Test-Path $WorkDir) {
        Fail "$WorkDir already exists — run '.\run-quickstart.ps1 clean' first if you want to start over, or '.\run-quickstart.ps1 run' if a draft is already there and you're ready to anonymise."
    }

    try {
        Start-FreshContainer
        Build-JarIfMissing
        New-Item -ItemType Directory -Path $WorkDir | Out-Null

        $env:IDENTIGON_SOURCE_PASSWORD = $PgPassword

        Write-Step "discover: reading the source schema (metadata only, no row values)"
        Invoke-Checked java -jar $Jar discover --source-url $SourceUrl --source-user postgres

        Write-Step "scaffold: writing a draft policy for you to classify"
        Invoke-Checked java -jar $Jar scaffold --source-url $SourceUrl --source-user postgres --out (Join-Path $WorkDir 'policy.draft.yaml')

        Write-Host ""
        Write-Host "Draft policy written to:"
        Write-Host "  $WorkDir\policy.draft.yaml"
        Write-Host ""
        Write-Host "Next: open this repo in an AI coding assistant that supports Agent Skills (Claude Code,"
        Write-Host "Antigravity, GitHub Copilot, ...) and ask it to use the identigon-policy-author skill"
        Write-Host "(.agents/skills/identigon-policy-author/SKILL.md) to interview you and classify every column —"
        Write-Host "for example:"
        Write-Host ""
        Write-Host "  Use the identigon-policy-author skill to help me classify"
        Write-Host "  quickstart/.quickstart-work/policy.draft.yaml"
        Write-Host ""
        Write-Host "The skill assigns roles (DIRECT_ID, QUASI_ID, SENSITIVE, ...) through interview; it doesn't pick"
        Write-Host "strategies (directIdStrategy, quasiIdStrategy, distinguishing, redactionStrategy) for you, so ask"
        Write-Host "it to fill those in too, or hand-edit them yourself — the checked-in policy.yaml in this directory"
        Write-Host "is a finished worked example of the full strategy vocabulary if you want something to compare"
        Write-Host "against."
        Write-Host ""
        Write-Host "Once every column has a role (and a strategy, where one's needed), run:"
        Write-Host ""
        Write-Host "  .\run-quickstart.ps1 run"
        Write-Host ""
        Write-Host "to anonymise using your own policy and see the DPIA report. (Rerunning 'run' fails closed with a"
        Write-Host "clear error if any column is still unclassified — that's the tool working as intended, not a bug;"
        Write-Host "go back and finish the draft.)"
    } catch {
        Write-Host ""
        Write-Host "Something went wrong: $_" -ForegroundColor Red
        Write-Host "The throwaway container ($ContainerName) was left running for inspection — remove it with: .\run-quickstart.ps1 clean" -ForegroundColor Red
        exit 1
    }
}

function Invoke-Run([string]$PolicyArg) {
    Test-RequiredTools
    $resolvedPolicy = $PolicyArg
    if (-not $resolvedPolicy) {
        $draft = Join-Path $WorkDir 'policy.draft.yaml'
        $resolvedPolicy = if (Test-Path $draft) { $draft } else { Join-Path $ScriptDir 'policy.yaml' }
    }
    if (-not (Test-Path $resolvedPolicy)) { Fail "Policy file not found: $resolvedPolicy" }
    $resolvedPolicy = (Resolve-Path $resolvedPolicy).Path   # absolute: we Push-Location before using it

    $reportDir = $WorkDir
    New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

    try {
        if (Test-ContainerRunning) {
            Write-Step "Reusing the already-running container ($ContainerName)"
        } else {
            Start-FreshContainer
        }
        Build-JarIfMissing

        $env:IDENTIGON_SOURCE_PASSWORD = $PgPassword
        $env:IDENTIGON_TARGET_PASSWORD = $PgPassword

        Write-Step "run: anonymising against $resolvedPolicy"
        Push-Location $reportDir
        try {
            Invoke-Checked java -jar $Jar run --policy $resolvedPolicy `
                --source-url $SourceUrl --source-user postgres `
                --target-url $TargetUrl --target-user postgres
        } finally {
            Pop-Location
        }

        Show-Results $reportDir
    } catch {
        Write-Host ""
        Write-Host "Something went wrong: $_" -ForegroundColor Red
        Write-Host "The throwaway container ($ContainerName) was left running for inspection — remove it with: .\run-quickstart.ps1 clean" -ForegroundColor Red
        exit 1
    }
}

function Invoke-Clean {
    Write-Step "Removing the throwaway container ($ContainerName) and any generated files, if present"
    docker rm -f $ContainerName *> $null
    if (Test-Path $WorkDir) { Remove-Item -Recurse -Force $WorkDir }
    Write-Host "Done."
}

switch ($Command) {
    'demo' { Invoke-Demo }
    'setup' { Invoke-Setup }
    'run' { Invoke-Run $Policy }
    'clean' { Invoke-Clean }
}
