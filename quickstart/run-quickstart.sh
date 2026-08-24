#!/bin/sh
# Evaluates Identigon end to end. POSIX sh (no Bash-isms) -- run with any conforming /bin/sh
# (dash, ash/busybox, ksh, bash, zsh in sh-emulation, ...). A native PowerShell port with identical
# behaviour is also in this directory: run-quickstart.ps1, for Windows without a POSIX shell.
#
# Two ways to use this:
#
#   ./run-quickstart.sh
#       One-shot demo: starts a throwaway PostgreSQL container, loads schema.sql + seed-data.sql,
#       builds the CLI jar if needed, runs discover -> scaffold -> run against the finished
#       policy.yaml already checked into this directory, and prints the fabricated rows. Nothing to
#       decide, nothing to author -- just to see the tool work.
#
#   ./run-quickstart.sh setup
#   ./run-quickstart.sh run
#       The real authoring workflow: `setup` gets you a scaffolded draft and stops there --
#       classify it yourself with the identigon-policy-author Agent Skill (or by hand), then `run`
#       anonymises against whatever you ended up with and shows the DPIA report. This is the
#       workflow effigies is actually built around; the one-shot demo above skips it by using the
#       finished policy.yaml directly.
#
#   ./run-quickstart.sh clean
#       Stops and removes the throwaway container and any generated draft/report files.
#
# Requires: Docker, Java 25. Nothing else -- psql isn't required on the host, every SQL statement
# runs through `docker exec` against the container's own psql.

set -eu
CDPATH=''   # avoid `cd` printing an unexpected match to stdout if the caller's CDPATH is set

SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
WORK_DIR="$SCRIPT_DIR/.quickstart-work"

CONTAINER_NAME=identigon-quickstart
PG_IMAGE=postgres:18-alpine
PG_PASSWORD=postgres
PG_PORT=55432   # non-default, to avoid colliding with a Postgres you may already have on 5432
SOURCE_DB=quickstart_source
TARGET_DB=quickstart_target
SOURCE_URL="jdbc:postgresql://localhost:$PG_PORT/$SOURCE_DB"
TARGET_URL="jdbc:postgresql://localhost:$PG_PORT/$TARGET_DB"

JAR="$REPO_ROOT/effigies/build/libs/identigon.jar"

log() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }
die() { printf '\n\033[1;31mFAILED: %s\033[0m\n' "$1" >&2; exit 1; }

# Installed as an EXIT trap (POSIX-portable; ERR traps are a Bash/ksh/zsh extension) by cmd_demo /
# cmd_setup / cmd_run once they're about to touch the container, so any failure after that point
# -- including one caused by `set -e` -- prints where the throwaway container was left, instead of
# just an unexplained failed-command message.
on_exit() {
    code=$?
    if [ "$code" -ne 0 ]; then
        printf '\nSomething went wrong (exit %s). The throwaway container (%s) was left running for inspection - remove it with: ./run-quickstart.sh clean\n' \
            "$code" "$CONTAINER_NAME" >&2
    fi
}

require_tools() {
    command -v docker >/dev/null 2>&1 || die "Docker is required - install it and make sure the daemon is running."
    command -v java >/dev/null 2>&1 || die "Java 25 is required."
    docker info >/dev/null 2>&1 || die "Docker doesn't seem to be running - start Docker and try again."
}

container_running() {
    # Not `docker inspect -f '{{.State.Running}}'`: MSYS2/Git-Bash's argv translation for native
    # Windows executables mangles `{{ }}` Go-template arguments (a well-known Windows-only quirk,
    # nothing to do with POSIX compliance) - this check needs no template at all.
    [ -n "$(docker ps -q -f "name=^${CONTAINER_NAME}\$" 2>/dev/null)" ]
}

psql_in_container() { docker exec -i "$CONTAINER_NAME" psql -v ON_ERROR_STOP=1 -U postgres "$@"; }

# Starts a fresh container and loads schema + seed data. Always wipes any previous container first
# - this is only called when there's no existing state worth preserving (see call sites).
start_fresh_container() {
    log "Starting a throwaway PostgreSQL container ($CONTAINER_NAME, port $PG_PORT)"
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
    docker run --rm -d --name "$CONTAINER_NAME" \
        -e POSTGRES_PASSWORD="$PG_PASSWORD" -p "$PG_PORT:5432" "$PG_IMAGE" >/dev/null

    log "Waiting for PostgreSQL to accept connections"
    # Requires TWO consecutive successful checks, not one: the official image briefly starts a
    # temporary internal instance (for initdb) that also answers pg_isready on the same socket,
    # then stops it and restarts the real listener -- a single success can land inside that
    # window, right before the socket briefly disappears during the restart.
    ready_count=0
    i=0
    while [ "$i" -lt 60 ]; do
        if docker exec "$CONTAINER_NAME" pg_isready -U postgres >/dev/null 2>&1; then
            ready_count=$((ready_count + 1))
            [ "$ready_count" -ge 2 ] && break
        else
            ready_count=0
        fi
        sleep 1
        i=$((i + 1))
    done
    [ "$ready_count" -ge 2 ] || die "PostgreSQL never became ready."

    log "Creating the source and target databases"
    psql_in_container -c "CREATE DATABASE $SOURCE_DB" >/dev/null
    psql_in_container -c "CREATE DATABASE $TARGET_DB" >/dev/null

    log "Loading the schema into both databases, and the sample data into the source only"
    psql_in_container -d "$SOURCE_DB" < "$SCRIPT_DIR/schema.sql" >/dev/null
    psql_in_container -d "$TARGET_DB" < "$SCRIPT_DIR/schema.sql" >/dev/null
    psql_in_container -d "$SOURCE_DB" < "$SCRIPT_DIR/seed-data.sql" >/dev/null
}

ensure_jar() {
    if [ ! -f "$JAR" ]; then
        log "Building the Identigon CLI jar (first run only - this can take a minute)"
        ( cd "$REPO_ROOT" && ./gradlew :effigies:assemble -q )
        [ -f "$JAR" ] || die "Build finished but $JAR wasn't produced - see the Gradle output above."
    fi
}

show_results() {
    report_dir="$1"
    log "Result: a few rows from the anonymised target database"
    psql_in_container -d "$TARGET_DB" -c \
        "SELECT full_name, email, nino, bank_account, date_of_birth FROM customers;"
    psql_in_container -d "$TARGET_DB" -c \
        "SELECT ordered_on, shipped_on FROM orders ORDER BY id;"
    psql_in_container -d "$TARGET_DB" -c \
        "SELECT category, notes FROM support_tickets;"

    cat <<EOF

DPIA report (open the .html for a presentation-ready view): $report_dir/dpia-report.html

The throwaway container is still running so you can poke around further:
  docker exec -it $CONTAINER_NAME psql -U postgres -d $TARGET_DB

When you're done: ./run-quickstart.sh clean
EOF
}

cmd_demo() {
    require_tools
    [ -e "$WORK_DIR" ] && die "$WORK_DIR exists from a previous 'setup' - run './run-quickstart.sh run' to finish that, or './run-quickstart.sh clean' first if you want the one-shot demo instead."
    demo_dir="$(mktemp -d)"
    trap on_exit EXIT

    start_fresh_container
    ensure_jar

    export IDENTIGON_SOURCE_PASSWORD="$PG_PASSWORD"
    export IDENTIGON_TARGET_PASSWORD="$PG_PASSWORD"

    log "Step 1/3 - discover: reading the source schema (metadata only, no row values)"
    java -jar "$JAR" discover --source-url "$SOURCE_URL" --source-user postgres

    log "Step 2/3 - scaffold: what Identigon can classify on its own"
    java -jar "$JAR" scaffold --source-url "$SOURCE_URL" --source-user postgres \
        --out "$demo_dir/policy.draft.yaml"
    # printf %s, not echo: POSIX `echo` is free to interpret backslashes as escapes (dash's does,
    # by default) -- unsafe for a path that may contain them, as every Windows temp path does.
    printf '%s\n' "(Written to $demo_dir/policy.draft.yaml - open it to see the suggestions and TODOs."
    printf '%s\n' " This one-shot demo uses the finished policy.yaml in this directory instead of the draft;"
    printf '%s\n' " run './run-quickstart.sh setup' if you want to author the draft yourself.)"

    log "Step 3/3 - run: anonymising the clone"
    ( cd "$demo_dir" && java -jar "$JAR" run \
        --policy "$SCRIPT_DIR/policy.yaml" \
        --source-url "$SOURCE_URL" --source-user postgres \
        --target-url "$TARGET_URL" --target-user postgres )

    show_results "$demo_dir"
    cat <<EOF

Worth comparing against seed-data.sql: every name/e-mail/phone/NINO/bank-account is fabricated,
dates on each order are shifted together (shipped_on stays on-or-after ordered_on), and every
support-ticket note is cleared while its category survives unchanged.
EOF
}

cmd_setup() {
    require_tools
    [ -e "$WORK_DIR" ] && die "$WORK_DIR already exists - run './run-quickstart.sh clean' first if you want to start over, or './run-quickstart.sh run' if a draft is already there and you're ready to anonymise."
    trap on_exit EXIT

    start_fresh_container
    ensure_jar
    mkdir -p "$WORK_DIR"

    export IDENTIGON_SOURCE_PASSWORD="$PG_PASSWORD"

    log "discover: reading the source schema (metadata only, no row values)"
    java -jar "$JAR" discover --source-url "$SOURCE_URL" --source-user postgres

    log "scaffold: writing a draft policy for you to classify"
    java -jar "$JAR" scaffold --source-url "$SOURCE_URL" --source-user postgres \
        --out "$WORK_DIR/policy.draft.yaml"

    cat <<EOF

Draft policy written to:
  $WORK_DIR/policy.draft.yaml

Next: open this repo in an AI coding assistant that supports Agent Skills (Claude Code,
Antigravity, GitHub Copilot, ...) and ask it to use the identigon-policy-author skill
(.agents/skills/identigon-policy-author/SKILL.md) to interview you and classify every column -
for example:

  Use the identigon-policy-author skill to help me classify
  quickstart/.quickstart-work/policy.draft.yaml

The skill assigns roles (DIRECT_ID, QUASI_ID, SENSITIVE, ...) through interview; it doesn't pick
strategies (directIdStrategy, quasiIdStrategy, distinguishing, redactionStrategy) for you, so ask
it to fill those in too, or hand-edit them yourself - the checked-in policy.yaml in this directory
is a finished worked example of the full strategy vocabulary if you want something to compare
against.

Once every column has a role (and a strategy, where one's needed), run:

  ./run-quickstart.sh run

to anonymise using your own policy and see the DPIA report. (Rerunning 'run' fails closed with a
clear error if any column is still unclassified - that's the tool working as intended, not a bug;
go back and finish the draft.)
EOF
}

cmd_run() {
    require_tools
    policy="${1:-}"
    if [ -z "$policy" ]; then
        if [ -f "$WORK_DIR/policy.draft.yaml" ]; then
            policy="$WORK_DIR/policy.draft.yaml"
        else
            policy="$SCRIPT_DIR/policy.yaml"
        fi
    fi
    [ -f "$policy" ] || die "Policy file not found: $policy"
    policy="$(cd -- "$(dirname -- "$policy")" && pwd)/$(basename -- "$policy")"   # absolute: we cd before using it

    report_dir="$WORK_DIR"
    mkdir -p "$report_dir"

    trap on_exit EXIT

    if container_running; then
        log "Reusing the already-running container ($CONTAINER_NAME)"
    else
        start_fresh_container
    fi
    ensure_jar

    export IDENTIGON_SOURCE_PASSWORD="$PG_PASSWORD"
    export IDENTIGON_TARGET_PASSWORD="$PG_PASSWORD"

    log "run: anonymising against $policy"
    ( cd "$report_dir" && java -jar "$JAR" run \
        --policy "$policy" \
        --source-url "$SOURCE_URL" --source-user postgres \
        --target-url "$TARGET_URL" --target-user postgres )

    show_results "$report_dir"
}

cmd_clean() {
    log "Removing the throwaway container ($CONTAINER_NAME) and any generated files, if present"
    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
    rm -rf "$WORK_DIR"
    echo "Done."
}

cmd="${1:-demo}"
case "$cmd" in
    demo) cmd_demo ;;
    setup) cmd_setup ;;
    run) cmd_run "${2:-}" ;;
    clean|--clean) cmd_clean ;;
    *) die "Unknown command: $cmd (expected: demo, setup, run [policy], clean)" ;;
esac
