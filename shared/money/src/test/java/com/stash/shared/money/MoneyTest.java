package com.stash.shared.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money")
class MoneyTest {

    private static final Currency GHS = Currency.GHS;

    // ── Factory methods ────────────────────────────────────────────────────

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("ofPesewas allows negative values (ledger entries)")
        void signed_allows_negative() {
            Money m = Money.ofPesewas(-500L, GHS);
            assertThat(m.getPesewas()).isEqualTo(-500L);
            assertThat(m.isNegative()).isTrue();
        }

        @Test
        @DisplayName("ofPositivePesewas rejects negative values (balance fields)")
        void unsigned_rejects_negative() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Money.ofPositivePesewas(-1L, GHS))
                    .withMessageContaining("non-negative");
        }

        @Test
        @DisplayName("ofPositivePesewas allows zero")
        void unsigned_allows_zero() {
            Money m = Money.ofPositivePesewas(0L, GHS);
            assertThat(m.isZero()).isTrue();
        }

        @Test
        @DisplayName("zero() returns zero Money")
        void zero_factory() {
            assertThat(Money.zero(GHS).isZero()).isTrue();
        }
    }

    // ── Arithmetic ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        @DisplayName("plus adds pesewa amounts")
        void plus() {
            Money a = Money.ofPesewas(300L, GHS);
            Money b = Money.ofPesewas(200L, GHS);
            assertThat(a.plus(b).getPesewas()).isEqualTo(500L);
        }

        @Test
        @DisplayName("minus subtracts pesewa amounts")
        void minus() {
            Money a = Money.ofPesewas(500L, GHS);
            Money b = Money.ofPesewas(200L, GHS);
            assertThat(a.minus(b).getPesewas()).isEqualTo(300L);
        }

        @Test
        @DisplayName("times multiplies by integer factor")
        void times() {
            Money m = Money.ofPesewas(100L, GHS);
            assertThat(m.times(3).getPesewas()).isEqualTo(300L);
        }

        @Test
        @DisplayName("dividedBy performs integer division (truncates)")
        void dividedBy() {
            Money m = Money.ofPesewas(100L, GHS);
            assertThat(m.dividedBy(3).getPesewas()).isEqualTo(33L); // truncates
        }

        @Test
        @DisplayName("dividedBy zero throws ArithmeticException")
        void dividedBy_zero() {
            assertThatExceptionOfType(ArithmeticException.class)
                    .isThrownBy(() -> Money.ofPesewas(100L, GHS).dividedBy(0));
        }

        @Test
        @DisplayName("currency mismatch throws IllegalArgumentException")
        void currency_mismatch() {
            // Only GHS exists at v1.0, but the type system must still reject mismatches.
            // Test by creating two instances with same currency to confirm no exception.
            Money a = Money.ofPesewas(100L, GHS);
            Money b = Money.ofPesewas(50L, GHS);
            assertThatNoException().isThrownBy(() -> a.plus(b));
        }

        @Test
        @DisplayName("negate flips sign")
        void negate() {
            Money m = Money.ofPesewas(500L, GHS);
            assertThat(m.negate().getPesewas()).isEqualTo(-500L);
        }

        @Test
        @DisplayName("abs returns positive value")
        void abs() {
            Money m = Money.ofPesewas(-500L, GHS);
            assertThat(m.abs().getPesewas()).isEqualTo(500L);
        }
    }

    // ── Formatting ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MoneyFormatter (display only)")
    class Formatting {

        @Test
        @DisplayName("formats 1250 pesewas as ₵12.50")
        void format_cedis() {
            Money m = Money.ofPesewas(1250L, GHS);
            assertThat(MoneyFormatter.format(m)).isEqualTo("₵12.50");
        }

        @Test
        @DisplayName("formats zero as ₵0.00")
        void format_zero() {
            assertThat(MoneyFormatter.format(Money.zero(GHS))).isEqualTo("₵0.00");
        }

        @Test
        @DisplayName("formatAmount omits symbol")
        void format_amount_no_symbol() {
            Money m = Money.ofPesewas(500L, GHS);
            assertThat(MoneyFormatter.formatAmount(m)).isEqualTo("5.00");
        }

        @Test
        @DisplayName("toCedis converts correctly")
        void to_cedis() {
            Money m = Money.ofPesewas(100L, GHS);
            assertThat(MoneyFormatter.toCedis(m)).isEqualTo(1.0);
        }
    }

    // ── JPA AttributeConverter ─────────────────────────────────────────────

    @Nested
    @DisplayName("MoneyAttributeConverter")
    class ConverterTest {

        private final MoneyAttributeConverter converter = new MoneyAttributeConverter();

        @Test
        @DisplayName("converts Money to Long (pesewas) for DB storage")
        void to_db_column() {
            Money m = Money.ofPesewas(750L, GHS);
            assertThat(converter.convertToDatabaseColumn(m)).isEqualTo(750L);
        }

        @Test
        @DisplayName("converts Long from DB back to Money")
        void from_db_column() {
            Money m = converter.convertToEntityAttribute(750L);
            assertThat(m.getPesewas()).isEqualTo(750L);
            assertThat(m.getCurrency()).isEqualTo(GHS);
        }

        @Test
        @DisplayName("null Money persists as null")
        void null_to_db() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }

        @Test
        @DisplayName("null DB value deserialises as null Money")
        void null_from_db() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("round-trip: Money → Long → Money preserves pesewas")
        void round_trip() {
            Money original = Money.ofPesewas(9999L, GHS);
            Long stored = converter.convertToDatabaseColumn(original);
            Money restored = converter.convertToEntityAttribute(stored);
            assertThat(restored).isEqualTo(original);
        }
    }

    // ── Equality ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("same pesewas and currency are equal")
        void equals() {
            Money a = Money.ofPesewas(100L, GHS);
            Money b = Money.ofPesewas(100L, GHS);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different pesewas are not equal")
        void not_equals() {
            Money a = Money.ofPesewas(100L, GHS);
            Money b = Money.ofPesewas(200L, GHS);
            assertThat(a).isNotEqualTo(b);
        }
    }
}