-- Allow MOOLRE as an external_provider alongside PAYSTACK.
ALTER TABLE transaction.transactions
    DROP CONSTRAINT transactions_external_provider_check;

ALTER TABLE transaction.transactions
    ADD CONSTRAINT transactions_external_provider_check
        CHECK (external_provider IS NULL
            OR external_provider IN ('PAYSTACK', 'MOOLRE'));
