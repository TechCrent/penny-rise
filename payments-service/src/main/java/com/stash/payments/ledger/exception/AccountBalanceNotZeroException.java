package com.stash.payments.ledger.exception;

import java.util.UUID;

public class AccountBalanceNotZeroException extends RuntimeException {

    private final UUID accountId;
    private final long balance;

    public AccountBalanceNotZeroException(UUID accountId, long balance) {
        super(String.format(
                "Cannot close account %s: balance is %dp, must be 0.", accountId, balance));
        this.accountId = accountId;
        this.balance = balance;
    }

    public UUID getAccountId() { return accountId; }
    public long getBalance()   { return balance; }
}
