---
status: "proposed"
# proposed | accepted | rejected | deprecated
date: {YYYY-MM-DD, of the latest change of status}
decision-makers: {the owner - required once the status is not "proposed"}
approval: {the owner's words, verbatim - required once the status is not "proposed"}
---

# 0. The question this record answers

<!--
Copy to NNNN-kebab-case-title.md, four-digit zero-padded, unique and never reused. The heading
number matches the filename, and the title names the question, not the answer, so that a changed
answer leaves it true. Keep this file as 0000; it is the template, and the rules for every record
live here.

Follows the MADR minimal template (https://adr.github.io/madr/).

Rules - absolute:

- An agent may create a record as "proposed", by copying this file - never an existing record - to
  the next free number, and may change a "proposed" record.
- Every other change needs the owner's explicit permission, asked for first: proposed -> accepted
  (accepting), proposed -> rejected (rejecting), accepted -> proposed (reopening to change it),
  accepted -> deprecated (deprecating), and deleting a record of any status.
- On accepting, rejecting or deprecating, set decision-makers (their name), approval (their words)
  and date (today), then commit.
- A correction that makes a record say what it always meant - a wrong path, command or citation -
  fixes the text without a status change, `date` or `approval`. A change to the decision, or to what
  confirms it, is an amendment.
- If the owner rejects a change to a reopened record, restore its last accepted version instead of
  marking it rejected.

How records work:

- Only `accepted` binds. An accepted record is the current answer to its question: reopen and amend
  it, even to reverse it. When it no longer applies, deprecate or delete it - deleting is usually
  simpler - and link from it to any record that replaces it. A later choice between real options is
  its own record, linked from this one.
- Cite records as ADR-NNNN, and another repository's record by a link. A change, deletion included,
  fixes in the same commit every citation it makes untrue; commit messages are left alone.
- Formatting applied by a tool is not a change.

Write it when the decision is made, not later. A record reconstructed years afterwards is usually an
argument for what you already do, and it dilutes the ones written contemporaneously.

Delete this comment, and every other HTML comment in this template, in the copy: they are
guidance for the author, not part of the record.
-->

## Context and Problem Statement

What forces were at play, and what made this decision non-obvious. What was known at the time - and,
more usefully, what was **not**. If nothing here would surprise a newcomer, this may not need a
record.

## Considered Options

- {option 1}
- {option 2}
- {option 3}

<!--
This section is what makes the file a decision rather than a statement. A record with only one
option to consider is usually a specification entry that has been misfiled.

Include the option you rejected even when it now looks obviously wrong - especially then. The next
person will think of it too, and this is what stops them re-litigating it.

Delete this comment in the copy.
-->

## Decision Outcome

Chosen option: "{option}", because {justification in one sentence}.

Then, if needed, a paragraph on what the decision does _not_ settle.

### Consequences

- Good, because {what becomes easier}.
- Bad, because {what becomes harder, or what is given up}.
- Neutral: {what changes without being better or worse}.

<!--
The honest ones are the useful ones. A record listing only benefits tells a future reader nothing
about whether the trade-off still holds - which is the question they came here to answer.

Delete this comment in the copy.
-->

## Confirmation

<!--
Optional. Describe how compliance with this decision is confirmed or tested.
For example: a test case, architectural fitness function, or automated checker rule.

Delete this comment in the copy.
-->

{How compliance is confirmed, e.g. a test or checker command.}
