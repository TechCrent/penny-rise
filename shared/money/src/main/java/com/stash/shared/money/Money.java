package com.stash.shared.money;

import java.util.Objects;

/**
 * Immutable monetary value type — BIGINT-backed, pesewa-precision.
 *
 * <h2>Storage convention</h2>
 * <p>All monetary amounts in Stash are stored as {@code BIGINT} in the database,
 * representing pesewas (subunits of the Ghana Cedi). 1 GHS = 100 pesewas.
 * Never store money as {@code DECIMAL} or {@code FLOAT} — floating-point
 * arithmetic is not associative and will silently corrupt balances at scale.
 * See the Schema doc "Conventions used throughout" for the canonical statement
 * of this rule.
 *
 * <h2>Factory methods</h2>
 * <ul>
 *   <li>{@link #ofPesewas(long, Currency)} — signed; for ledger entries where
 *       debits are negative and credits are positive.</li>
 *   <li>{@link #ofPositivePesewas(long, Currency)} — unsigned; for balance fields
 *       where a negative value is a domain error.</li>
 * </ul>
 *
 * <h2>Arithmetic</h2>
 * <p>All operations return a new {@code Money} instance. No operation returns
 * a primitive or a float. Division truncates toward zero (integer semantics).
 *
 * <h2>Thread safety</h2>
 * <p>Immutable — thread-safe by construction.
 */
public final class Money {

    private final long pesewas;
    private final Currency currency;

    // ── Factory methods ────────────────────────────────────────────────────

    /**
     * Creates a Money value from a signed pesewa amount.
     *
     * <p>Use this for ledger entries where debits are negative.
     *
     * @param pesewas  signed pesewa amount
     * @param currency the currency
     * @return a new Money instance
     */
    public static Money ofPesewas(long pesewas, Currency currency) {
        Objects.requireNonNull(currency, "currency must not be null");
        return new Money(pesewas, currency);
    }

    /**
     * Creates a Money value from an unsigned pesewa amount.
     *
     * <p>Use this for balance fields. Throws if the value is negative —
     * a negative balance is a domain error and should be caught at the boundary.
     *
     * @param pesewas  non-negative pesewa amount
     * @param currency the currency
     * @return a new Money instance
     * @throws IllegalArgumentException if pesewas is negative
     */
    public static Money ofPositivePesewas(long pesewas, Currency currency) {
        Objects.requireNonNull(currency, "currency must not be null");
        if (pesewas < 0) {
            throw new IllegalArgumentException(
                    "Balance Money must be non-negative. Got: " + pesewas + " pesewas. " +
                            "Use ofPesewas() for signed ledger entries.");
        }
        return new Money(pesewas, currency);
    }

    /**
     * Convenience factory: zero amount in the given currency.
     */
    public static Money zero(Currency currency) {
        return ofPesewas(0L, currency);
    }

    // ── Private constructor ────────────────────────────────────────────────

    private Money(long pesewas, Currency currency) {
        this.pesewas = pesewas;
        this.currency = currency;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    /** Raw pesewa value — use this for DB persistence via the JPA converter. */
    public long getPesewas() {
        return pesewas;
    }

    public Currency getCurrency() {
        return currency;
    }

    public boolean isZero() {
        return pesewas == 0;
    }

    public boolean isPositive() {
        return pesewas > 0;
    }

    public boolean isNegative() {
        return pesewas < 0;
    }

    // ── Arithmetic ─────────────────────────────────────────────────────────

    /**
     * Returns {@code this + other}.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public Money plus(Money other) {
        assertSameCurrency(other);
        return new Money(Math.addExact(this.pesewas, other.pesewas), this.currency);
    }

    /**
     * Returns {@code this - other}.
     *
     * @throws IllegalArgumentException if currencies differ
     */
    public Money minus(Money other) {
        assertSameCurrency(other);
        return new Money(Math.subtractExact(this.pesewas, other.pesewas), this.currency);
    }

    /**
     * Returns {@code this * factor} (integer factor, no float conversion).
     */
    public Money times(long factor) {
        return new Money(Math.multiplyExact(this.pesewas, factor), this.currency);
    }

    /**
     * Returns {@code this / divisor}, truncating toward zero.
     *
     * @throws ArithmeticException if divisor is zero
     */
    public Money dividedBy(long divisor) {
        if (divisor == 0) throw new ArithmeticException("Division by zero");
        return new Money(this.pesewas / divisor, this.currency);
    }

    /**
     * Returns the absolute value of this Money.
     */
    public Money abs() {
        return new Money(Math.abs(this.pesewas), this.currency);
    }

    /**
     * Returns the negation of this Money (sign flip).
     */
    public Money negate() {
        return new Money(Math.negateExact(this.pesewas), this.currency);
    }

    // ── Comparison ─────────────────────────────────────────────────────────

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.pesewas > other.pesewas;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        assertSameCurrency(other);
        return this.pesewas >= other.pesewas;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.pesewas < other.pesewas;
    }

    // ── Object overrides ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return pesewas == money.pesewas && currency == money.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pesewas, currency);
    }

    @Override
    public String toString() {
        // Internal representation — NOT for UI display. Use MoneyFormatter for that.
        return pesewas + " " + currency.code + " (pesewas)";
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (this.currency != other.currency) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
    }
}