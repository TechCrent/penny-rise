package com.stash.shared.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter — persists {@link Money} as a {@code BIGINT} (pesewas).
 *
 * <p>Usage on a JPA entity field:
 * <pre>
 *   {@literal @}Convert(converter = MoneyAttributeConverter.class)
 *   {@literal @}Column(name = "amount", nullable = false)
 *   private Money amount;
 * </pre>
 *
 * <p>The converter always uses {@link Currency#GHS}. Multi-currency support
 * will require a separate currency column and a composite converter — that is
 * a v2.0 concern.
 *
 * <p>Null safety: a null {@code Money} persists as SQL NULL; a null DB value
 * deserialises as null. Entity fields that represent balances should be
 * initialised to {@code Money.zero(Currency.GHS)} and declared NOT NULL in the
 * migration to prevent null balances at rest.
 */
@Converter
public class MoneyAttributeConverter implements AttributeConverter<Money, Long> {

    @Override
    public Long convertToDatabaseColumn(Money money) {
        if (money == null) return null;
        return money.getPesewas();
    }

    @Override
    public Money convertToEntityAttribute(Long pesewas) {
        if (pesewas == null) return null;
        // Balance columns are always non-negative; ledger columns are signed.
        // We use ofPesewas (signed) here so the converter works for both cases.
        // Entity code that enforces the non-negative invariant does so at the
        // service layer, not the converter layer.
        return Money.ofPesewas(pesewas, Currency.GHS);
    }
}