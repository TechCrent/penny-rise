package com.stash.payments.ledger.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Generates customer-visible transaction references in the format
 * {@code STSH-yyyymm-XXXXXX} where {@code yyyymm} is the current UTC
 * year-month and {@code XXXXXX} is a 6-character alphanumeric suffix drawn
 * from a cryptographically secure random source.
 *
 * <p>Collision avoidance: the suffix space is 36^6 = 2,176,782,336 possible
 * values per calendar month. At 1 million transactions per month (far beyond
 * current projections) the birthday-problem collision probability is ~0.023%.
 * The database UNIQUE constraint on {@code transaction_reference} is the
 * ultimate safety net — if a collision occurs, the caller catches the
 * constraint violation and retries with a fresh reference.
 *
 * <p>The {@link Clock} is injected so tests can fix time deterministically.
 */
@Component
public class TransactionReferenceGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int SUFFIX_LENGTH = 6;
    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMM");

    private final SecureRandom random;
    private final Clock clock;

    public TransactionReferenceGenerator(Clock clock) {
        this.random = new SecureRandom();
        this.clock = clock;
    }

    /**
     * Generates one transaction reference.
     *
     * @return e.g. {@code STSH-202606-4FH92K}
     */
    public String generate() {
        String yearMonth = YearMonth.now(clock).format(MONTH_FORMAT);
        String suffix = randomSuffix();
        return "STSH-" + yearMonth + "-" + suffix;
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
