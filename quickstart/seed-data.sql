-- Sample data for the Identigon quickstart example. Load into the "source" database only, after
-- schema.sql - the "target" database stays empty (schema-identical) until `run` loads it. See
-- README.md in this directory for the full walkthrough.
--
-- Every value below is already fictional/placeholder data - Identigon anonymising it further just
-- demonstrates the mechanics, it isn't standing in for anything real.

INSERT INTO customers
    (full_name, email, phone, nino, bank_account, date_of_birth, postcode, marketing_opt_in, signed_up_on)
VALUES
    ('Alice Whitfield',   'alice.whitfield@mailbox.test',  '020 7946 0958', 'AB 12 34 56 C', '12-34-56 12345678', '1987-03-14', 'SW1A 1AA', true,  '2021-06-01'),
    ('Ben Okafor',        'ben.okafor@mailbox.test',       '020 7946 0112', 'CD 65 43 21 A', '20-11-88 87654321', '1991-11-02', 'EC1A 1BB', false, '2022-01-17'),
    ('Charlotte Nguyen',  'charlotte.nguyen@mailbox.test', '020 7946 0733', 'EF 98 76 54 B', '40-55-19 11223344', '1979-07-23', 'W1D 3QU',  true,  '2020-09-30'),
    ('Dominic Farrell',   'dominic.farrell@mailbox.test',  '020 7946 0284', 'GH 11 22 33 D', '60-02-71 99887766', '1995-02-11', 'M1 1AE',   false, '2023-03-08'),
    ('Elena Petrov',      'elena.petrov@mailbox.test',     '020 7946 0501', 'JK 44 55 66 A', '30-90-08 55667788', '1983-12-30', 'B1 1HQ',   true,  '2021-11-22');

INSERT INTO orders (customer_id, ordered_on, shipped_on, total_amount, status) VALUES
    (1, '2024-01-05', '2024-01-07', 49.99,  'delivered'),
    (1, '2024-03-12', '2024-03-14', 120.00, 'delivered'),
    (2, '2024-02-20', NULL,         15.50,  'processing'),
    (3, '2024-01-28', '2024-01-30', 89.00,  'delivered'),
    (3, '2024-04-02', NULL,         33.25,  'cancelled'),
    (4, '2024-03-19', '2024-03-22', 210.75, 'delivered'),
    (5, '2024-02-08', '2024-02-09', 12.00,  'delivered'),
    (5, '2024-04-15', NULL,         67.40,  'processing');

INSERT INTO support_tickets (customer_id, opened_at, category, notes) VALUES
    (1, '2024-01-08 09:15:00', 'billing',   'Customer disputes a duplicate charge on order #1.'),
    (2, '2024-02-21 14:02:00', 'technical', 'Login fails intermittently; sent screenshot of error.'),
    (3, '2024-04-03 11:47:00', 'account',   'Requested email address on file be updated.'),
    (4, '2024-03-20 16:30:00', 'billing',   'Asked whether a refund had been processed yet.');
