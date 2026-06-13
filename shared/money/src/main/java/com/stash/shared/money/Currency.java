package com.stash.shared.money;

/**
 * Currencies supported by the Stash platform.
 *
 * <p>GHS (Ghana Cedi) is the only currency at v1.0. The enum is declared
 * now so that {@link Money} instances carry currency identity from day one,
 * making multi-currency support a non-breaking addition in a future version.
 */
public enum Currency {

    /**
     * Ghana Cedi. Subunit: pesewa (1 GHS = 100 pesewas).
     * All monetary amounts are stored as pesewas (BIGINT) in the database.
     */
    GHS(100L, "GHS", "₵");

    /** Number of subunits (pesewas) per major unit (cedi). */
    public final long subunitsPerUnit;

    /** ISO 4217 currency code. */
    public final String code;

    /** Display symbol for UI formatting. */
    public final String symbol;

    Currency(long subunitsPerUnit, String code, String symbol) {
        this.subunitsPerUnit = subunitsPerUnit;
        this.code = code;
        this.symbol = symbol;
    }
}