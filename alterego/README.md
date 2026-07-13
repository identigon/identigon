# AlterEgo

AlterEgo is a zero-dependency Java 25 library for deterministic pseudonymisation. It replaces
personal or sensitive values (names, addresses, dates, reference numbers) with realistic-looking
substitutes, such that the same input always produces the same output for a given configuration.
It is designed for use in Java streams, is extensible with custom transformations, and generates
fictional-by-default output (reserved email domains, Ofcom drama phone numbers, impossible
postcodes) so pseudonymised data never accidentally references something real.

This project is not yet released. See [`SPECIFICATION.md`](SPECIFICATION.md) for the full
behavioural contract and [`PLAN.md`](PLAN.md) for the implementation milestones.
