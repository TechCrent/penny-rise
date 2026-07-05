-- Allow WALLET_DEPOSIT as a ledger business_reference_type for direct wallet top-ups.
ALTER TABLE ledger.ledger_transactions
    DROP CONSTRAINT ledger_transactions_business_reference_type_check;

ALTER TABLE ledger.ledger_transactions
    ADD CONSTRAINT ledger_transactions_business_reference_type_check
        CHECK (business_reference_type IS NULL OR business_reference_type IN (
            'WALLET_DEPOSIT',
            'VAULT_DEPOSIT',
            'VAULT_WITHDRAWAL',
            'VAULT_EARLY_EXIT',
            'PEER_TRANSFER',
            'SUSU_CONTRIBUTION',
            'SUSU_DISBURSEMENT',
            'INTEREST_ACCRUAL',
            'REVERSAL'
        ));
