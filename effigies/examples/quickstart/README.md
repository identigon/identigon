# Quickstart example

A small, self-contained schema for trying Identigon end to end in a few minutes: three tables
(`customers`, `orders`, `support_tickets`), a handful of rows, no third-party data, no Docker or
Testcontainers dependency — just PostgreSQL. Point `effigies` at it and you'll see fabricated
names, e-mails, phone numbers, a National Insurance number (NINO), coherent date jitter, and a
redacted free-text field, all in one pass.

Five files: [`schema.sql`](schema.sql) (DDL only — load into both databases),
[`seed-data.sql`](seed-data.sql) (sample rows — load into the source only), [`policy.yaml`](policy.yaml)
(a finished classification for every column, to run directly or to compare your own against), and
two behaviourally-identical driver scripts — [`run-quickstart.sh`](run-quickstart.sh) (POSIX `sh`;
macOS/Linux/WSL/Git Bash) and [`run-quickstart.ps1`](run-quickstart.ps1) (native PowerShell; no
POSIX shell needed on Windows) — either running a one-shot demo, or the real
scaffold-then-author-then-run workflow; see below. Use whichever script matches your shell; the
rest of this README shows the `sh` invocations, but `.\run-quickstart.ps1 <command>` takes the same
commands.

This is separate from the benchmark fixtures under
[`incognito/src/test/resources/benchmarks/`](../../../incognito/src/test/resources/benchmarks/) —
those are third-party sample databases used as integration-test fixtures and require Docker. This
one is authored for this repository specifically so there's nothing to install beyond Postgres
itself.

For the narrative walkthrough of *why* each step exists (fail-closed, salt modes, the DPIA report),
see [Getting Started](https://identigon.org/getting-started) on the project site — this directory
is the copy-pasteable version of the same steps.

## Fastest way to try it

Requires Docker and Java 25 — nothing else to install:

```sh
./run-quickstart.sh
```

(Windows without a POSIX shell: `.\run-quickstart.ps1` instead — same commands throughout.)

One command: starts a throwaway Postgres container, loads the schema and sample data, builds the
CLI jar if it isn't already built, runs `discover` → `scaffold` → `run` against the finished
`policy.yaml` already in this directory, and prints the fabricated rows for you to look at, with a
short explanation of what to check. Nothing to author, nothing to decide — just to see the tool
work. Safe to re-run — it always starts from a clean slate. When you're done:
`./run-quickstart.sh clean` removes the container and any generated files.

## Evaluate the actual authoring workflow

The one-shot demo above skips the part of Identigon that involves judgment — it uses the
already-finished `policy.yaml`. To see the real workflow, including the interactive
[Agent Skill](../../../.agents/skills/identigon-policy-author/) that helps you classify a fresh
scaffold, use two commands instead of one:

```sh
./run-quickstart.sh setup
```

Starts the container, loads the schema and data, and runs `discover` + `scaffold` for you, then
stops — leaving a draft policy at `.quickstart-work/policy.draft.yaml` and printing a suggested
prompt for your AI coding assistant. Ask it (Claude Code, Antigravity, Copilot, or any other
Agent-Skill-compatible assistant) to use the `identigon-policy-author` skill on that draft; it
interviews you, batching related columns so you're not answering one question per column, and
never assigns anything without your confirmation. Once every column is classified:

```sh
./run-quickstart.sh run
```

Anonymises using whatever policy you ended up with and prints the same results/DPIA summary as the
one-shot demo. If a column is still unclassified, this fails closed with a clear error instead of
guessing — go back and finish the draft, then run it again.

`./run-quickstart.sh clean` tears down the container and removes `.quickstart-work/` either way.

The rest of this README walks through the same one-shot-demo steps by hand, if you'd rather run
(or understand) each one yourself without the script.

## 1. Create the source and target databases

You need two empty PostgreSQL databases: `quickstart_source` and `quickstart_target`. Any local
Postgres works; if you don't have one handy, a throwaway container is the fastest way to get one:

```sh
docker run --rm -d --name identigon-quickstart -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:18-alpine
```

Then create the two databases and load the schema:

```sh
export PGPASSWORD=postgres
createdb -h localhost -U postgres quickstart_source
createdb -h localhost -U postgres quickstart_target

# both databases get the same schema
psql -h localhost -U postgres -d quickstart_source -f schema.sql
psql -h localhost -U postgres -d quickstart_target -f schema.sql

# only the source gets the sample data -- the target stays empty until `run` loads it
psql -h localhost -U postgres -d quickstart_source -f seed-data.sql
```

## 2. See what discovery and scaffolding produce

Optional, but this is the point of the exercise: see how far Identigon gets on its own before
looking at the finished `policy.yaml` in this directory.

```sh
export IDENTIGON_SOURCE_PASSWORD=postgres

java -jar ../../build/libs/identigon.jar discover \
  --source-url "jdbc:postgresql://localhost:5432/quickstart_source" --source-user postgres

java -jar ../../build/libs/identigon.jar scaffold \
  --source-url "jdbc:postgresql://localhost:5432/quickstart_source" --source-user postgres \
  --out ./policy.draft.yaml
```

Open `policy.draft.yaml`: `scaffold` never assigns a `role` itself — every column is left blank
with a `# TODO classify` comment, because guessing wrong is exactly the mistake fail-closed exists
to prevent (an unclassified column aborts `run` rather than being copied silently). What it *does*
do is suggest one from name-based heuristics where it recognises the column name (`email`, `phone`,
`date_of_birth`, `nino`, …); columns it doesn't recognise (`bank_account`, `notes`, …) get a bare
prompt to classify manually. Either way, you fill in every `role:` yourself. Normally this is
where you'd hand the draft to the [Agent Skill](../../../.agents/skills/identigon-policy-author/) to
finish interactively; this example skips straight to the finished result in `policy.yaml`.

## 3. Run the anonymisation pipeline

```sh
export IDENTIGON_SOURCE_PASSWORD=postgres
export IDENTIGON_TARGET_PASSWORD=postgres

java -jar ../../build/libs/identigon.jar run \
  --policy ./policy.yaml \
  --source-url "jdbc:postgresql://localhost:5432/quickstart_source" --source-user postgres \
  --target-url "jdbc:postgresql://localhost:5432/quickstart_target" --target-user postgres
```

## 4. Look at the results

```sh
psql -h localhost -U postgres -d quickstart_target -c "SELECT full_name, email, nino, bank_account, date_of_birth FROM customers;"
psql -h localhost -U postgres -d quickstart_target -c "SELECT ordered_on, shipped_on FROM orders ORDER BY id;"
psql -h localhost -U postgres -d quickstart_target -c "SELECT category, notes FROM support_tickets;"
```

Worth checking for yourself:

- Every `nino` starts with `QQ ` — the guaranteed-fictional prefix HMRC never allocates.
- `bank_account` values are a different but same-shaped string per row — fabricated, but (unlike
  the NINO) with no fictionality guarantee; see the comment in `policy.yaml`.
- For each order, `shipped_on` is still on-or-after `ordered_on`, and the gap between the two
  dates is preserved — coherent jitter, not two independently-randomised dates.
- `notes` is empty for every ticket; `category` is unchanged (`billing`/`technical`/`account`
  survive — a shared, non-identifying category, kept real per its `distinguishing: false`
  declaration).
- Row counts and every FK relationship match the source exactly.

`run` also writes `dpia-report.html`, `dpia-report.json`, and `dpia-report.md` in the working
directory — open the HTML one for a presentation-ready accountability report covering every
column's classification and a sample of illustrative (never real) fabricated values.

## Cleaning up

```sh
docker stop identigon-quickstart
```

(If you used `./run-quickstart.sh` instead of the manual steps above, use
`./run-quickstart.sh clean` — same effect, same container name.)
