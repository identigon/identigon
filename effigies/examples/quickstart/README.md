# Quickstart example

A small, self-contained schema for trying Identigon end to end in a few minutes: three tables
(`customers`, `orders`, `support_tickets`), a handful of rows, no third-party data, no Docker or
Testcontainers dependency — just PostgreSQL. Point `effigies` at it and you'll see fabricated
names, e-mails, phone numbers, a National Insurance number (NINO), coherent date jitter, and a
redacted free-text field, all in one pass.

Three files: [`schema.sql`](schema.sql) (DDL only — load into both databases), [`seed-data.sql`](seed-data.sql)
(sample rows — load into the source only), and [`policy.yaml`](policy.yaml) (the finished
classification for every column).

This is separate from the benchmark fixtures under
[`incognito/src/test/resources/benchmarks/`](../../../incognito/src/test/resources/benchmarks/) —
those are third-party sample databases used as integration-test fixtures and require Docker. This
one is authored for this repository specifically so there's nothing to install beyond Postgres
itself.

For the narrative walkthrough of *why* each step exists (fail-closed, salt modes, the DPIA report),
see [Getting Started](https://identigon.org/getting-started) on the project site — this directory
is the copy-pasteable version of the same steps.

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
where you'd hand the draft to the [Agent Skill](../../.agents/skills/identigon-policy-author/) to
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
