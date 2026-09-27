# Task: make the scaffolded draft explain itself

Status: not started. Delete once implemented and green.

## 1. The gap

User feedback: the quickstart steps are easy to follow, but the `scaffold` output is intimidating
and it isn't clear what to do next. The questions users asked are exactly the ones the draft doesn't
answer:

- What is a role? What is a strategy?
- Is there anything besides role and strategy to fill in for each column?
- What values can each one take?
- Which combinations are valid, and what does each one do to the data?
- Why do some values start with `ALTEREGO_`? (Renaming them is in
  `incognito-plainer-policy-vocabulary.md`; this task is about explaining whatever the names end up
  being.)

## 2. What the draft looks like today

Built by `ScaffoldCommand.writeScaffold`/`writeRoleStub`. For the quickstart schema it produces
something like this (the long comments are wrapped here; in the file each is one line):

```yaml
# The `type:` shown against each column below is JDBC's own name, not necessarily the
# database's - e.g. PostgreSQL's BOOLEAN reports here as JDBC's BIT, and TEXT as VARCHAR.
# Still a reliable input for choosing a strategy, just not identical to what the DDL says.
tables:
  customers:
    columns:
      id:            # type: BIGINT, pk
        role:              # TODO classify (Suggestion: PRIMARY_KEY, structurally discovered - not a guess)
        surrogateStrategy: # TODO if PRIMARY_KEY (Suggestion: SEQUENTIAL_LONG)
      full_name:            # type: VARCHAR
        role:              # TODO classify (Suggestion: DIRECT_ID based on NAME_PATTERN)
        directIdStrategy:  # TODO if DIRECT_ID (Suggestion: ALTEREGO_NAME)
      marketing_opt_in:            # type: BIT
        role:              # TODO classify - see docs/spec/incognito.md §4.1 for the full
                           # ColumnRole vocabulary; run fails closed until filled
```

What goes wrong for a first-time reader:

- The first thing in the file is a JDBC type-name caveat, not what the file is for or what to do
  with it.
- Nothing lists the roles or says what they mean. The only pointer is a repository path to a spec
  section (or "DirectIdStrategy's Javadoc"), which a CLI user has no reason to have.
- The follow-up keys (`surrogateStrategy`, `directIdStrategy`, `distinguishing`, `references`)
  appear only when a suggestion happens to exist. A column with no suggestion shows none, so the
  user can't tell that e.g. `SENSITIVE` needs `distinguishing` and possibly a `redactionStrategy`,
  or that `QUASI_ID` needs a `quasiIdStrategy`. Other keys (`jitterDays`, `coherenceGroup`,
  `redactionConstant`, `derivedFrom`) never appear at all.
- Where there is a suggestion, it names the internal heuristic (`NAME_PATTERN`) rather than the
  reason ("the column name looks like a person's name").
- Allowed values are never listed. A user has to already know `SYNTHESISE`/`JITTER_DAYS`/... and
  `CLEAR`/`MASK`/`CONSTANT`.
- After writing the file, `scaffold` prints only `Scaffold written to <file>` - no count of what
  still needs deciding, and no next command.

## 3. Direction

Proposed, not designed - settle the details while implementing (with an ADR only if the policy
format itself changes):

- **Start the file with what to do:** one short header - "Give every column a `role`. Some roles
  need one or two more settings, listed below. Then run `identigon validate`; it tells you what is
  still missing. When it passes, run `identigon run`." - followed by a compact legend: each usable
  role in one plain-language line ("DIRECT_ID - identifies a person on its own (name, e-mail, NI
  number); replaced with a fictional value"), the settings it needs, and their allowed values. The
  reserved, not-yet-implemented roles are left out.
- **Per column, show the allowed values for the suggested role in place,** e.g.
  `quasiIdStrategy: # SYNTHESISE | JITTER_DAYS | JITTER_WITHIN_MONTH | JITTER_WITHIN_YEAR`, so the
  user can edit the line they are already looking at.
- **Give reasons, not heuristic names:** "the name looks like an e-mail address", "this is the
  table's primary key".
- **Finish with a summary on the terminal:** how many columns, how many have a suggestion, how many
  need a decision from scratch, and the exact `validate` command to run next.
- **Make `validate` the loop that guides the user:** for each unclassified or incomplete column, say
  what is missing and list the allowed values, rather than only reporting the failure. It already
  collects every issue before failing (§6 of the incognito spec), so one pass lists everything.
- **Link to the column-classification guide** (its own `PLAN.md` entry) at a stable public URL for
  the "which one should I choose" judgement a comment can't hold.

## 4. Constraints

- Suggest, never assign (`effigies` hard invariant 2, ADR 17). Even structurally certain PK/FK
  suggestions stay suggestions; making them pre-filled values is a separate decision needing its own
  ADR, not part of this task.
- Metadata only (hard invariant 1): nothing here may sample a column's values to improve a
  suggestion.
- The legend and allowed-value lists must come from the policy model's enums, not from hand-written
  copies, so they can't drift from what the parser accepts. Pin this with a test that parses a
  scaffold, fills every suggestion, and checks that `validate` accepts it.
- Keep the output valid YAML that fails closed as it does today: a blank `role:` must still abort
  `run`.

## 5. Done when

Someone who has never seen a policy can take the quickstart scaffold to a passing `validate` using
only the draft, the terminal output and the linked guide - no spec, no Javadoc, no finished
`quickstart/policy.yaml` to copy from.
