---
status: "proposed"
# proposed | rejected | accepted | deprecated | superseded by ADR-NNNN
# may carry a pointer, e.g. accepted (refined by ADR-0006) - see DOC-MAP.md
date: {YYYY-MM-DD, when the status last changed}
decision-makers: {who decided - required once the status is not "proposed"}
# consulted / informed: optional, and overhead on a small team
---

# 0. Short title, imperative, naming the problem and the answer

<!--
Copy to NNNN-kebab-case-title.md, four-digit zero-padded, unique and never reused - numbered across
the whole monorepo, not per subproject. The heading number matches the filename. Keep this file as
0000; it is the template.

Follows the MADR minimal template (https://adr.github.io/madr/). DOC-MAP.md carries the rules about
status, immutability and who may change one.

Write it when the decision is made, not later. A record reconstructed years afterwards is usually an
argument for what you already do, and it dilutes the ones written contemporaneously.

Delete this comment in the copy.
-->

## Context and Problem Statement

What forces were at play, and what made this decision non-obvious. What was known at the time -
and, more usefully, what was **not**. If nothing here would surprise a newcomer, this may not need
a record.

## Considered Options

- {option 1}
- {option 2}
- {option 3}

<!--
This section is what makes the file a decision rather than a statement. A record with only one
option to consider is usually a specification entry that has been misfiled.

Include the option you rejected even when it now looks obviously wrong - especially then. The next
person will think of it too, and this is what stops them re-litigating it.
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
-->
