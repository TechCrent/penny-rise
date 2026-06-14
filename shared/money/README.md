# shared/money

The `Money` value type for the Stash platform — BIGINT-backed, pesewa-precision.

## The rule: never DECIMAL, never FLOAT

All monetary amounts in Stash are stored as `BIGINT` in the database, representing
**pesewas** (the subunit of the Ghana Cedi). 1 GHS = 100 pesewas.

This is a hard convention mandated by the Schema doc ("Conventions used throughout").

**Why not DECIMAL or FLOAT?**

Floating-point arithmetic is not associative. `(0.1 + 0.2) != 0.3` in IEEE-754.
At scale, rounding errors accumulate silently and corrupt balances. A single misplaced
`double` in a financial calculation is a production incident.

Integer arithmetic on pesewas is exact. There is no rounding. 100 + 200 = 300, always.

## Usage

```java
// Ledger entries (signed — debits are negative)
Money debit  = Money.ofPesewas(-5000L, Currency.GHS);  // -50 GHS
Money credit = Money.ofPesewas(5000L, Currency.GHS);   // +50 GHS

// Balance fields (unsigned — negative is a domain error)
Money balance = Money.ofPositivePesewas(10000L, Currency.GHS); // 100 GHS

// Arithmetic — all operations return Money, never a primitive
Money total = credit.plus(Money.ofPesewas(200L, Currency.GHS)); // 5200 pesewas

// Display only — NEVER use toCedis() result in business logic
String display = MoneyFormatter.format(balance); // "₵100.00"
```

## JPA usage

```java
@Convert(converter = MoneyAttributeConverter.class)
@Column(name = "amount", nullable = false)
private Money amount;
```

The converter maps `Money` ↔ `BIGINT` (pesewas). No manual conversion needed in entity code.

## Maven dependency

```xml
<dependency>
    <groupId>com.stash</groupId>
    <artifactId>money</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```