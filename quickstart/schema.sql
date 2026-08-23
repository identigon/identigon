-- Identigon quickstart example: a small, self-contained PostgreSQL schema for trying the
-- discover -> scaffold -> run workflow end to end in a few minutes.
--
-- Not a benchmark fixture (see incognito/src/test/resources/benchmarks/ for those) and not
-- third-party data -- authored for this repository, so there's no licence/provenance bookkeeping
-- and no Docker/Testcontainers dependency. Load this into BOTH the "source" and "target"
-- databases -- schema only, identical on both sides. Load seed-data.sql into the "source"
-- database only, afterwards. See README.md in this directory for the full walkthrough.

CREATE TABLE customers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    nino            VARCHAR(13)  NOT NULL,  -- UK National Insurance number, "AB 12 34 56 C"
    bank_account    VARCHAR(20)  NOT NULL,  -- sort code + account number, "12-34-56 12345678"
    date_of_birth   DATE         NOT NULL,
    postcode        VARCHAR(10)  NOT NULL,
    marketing_opt_in BOOLEAN     NOT NULL DEFAULT false,
    signed_up_on    DATE         NOT NULL
);

CREATE TABLE orders (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    ordered_on      DATE NOT NULL,
    shipped_on      DATE,                   -- nullable: not every order has shipped yet
    total_amount    NUMERIC(10,2) NOT NULL,
    status          VARCHAR(20) NOT NULL
);

CREATE TABLE support_tickets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customers(id),
    opened_at       TIMESTAMP NOT NULL,
    category        VARCHAR(20) NOT NULL,   -- 'billing' | 'technical' | 'account' -- low-cardinality
    notes           TEXT                    -- free text an agent typed; may contain anything
);
