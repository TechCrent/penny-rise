# shared/uuid-v7

UUID v7 generator for the Stash platform.

## Why UUID v7 and not UUID v4?

All primary keys in Stash use UUID v7 instead of UUID v4. The reason is database index performance.

UUID v4 is completely random. When a new row is inserted, Postgres must place it in a random position in the B-tree index. At scale this causes **index fragmentation** — pages fill up in unpredictable locations, forcing splits and leaving gaps. The result is larger indexes, slower inserts, and increased I/O.

UUID v7 embeds a millisecond-precision Unix timestamp in the most significant 48 bits. New IDs are always greater than old IDs, so new rows are always appended to the rightmost leaf of the B-tree. This gives **sequential index locality** — the same access pattern as an auto-increment integer, with the distribution and uniqueness guarantees of a UUID.

The Schema doc mandates UUID v7 for every primary key across every service.

## Usage

```java
import com.stash.shared.uuidv7.UuidV7Generator;

UUID id = UuidV7Generator.generate();
```

The generator is a static utility class — no instantiation or Spring bean required.

## Monotonicity guarantee

Within the same millisecond, the 12-bit `rand_a` field acts as a monotonic counter. Up to 4096 IDs can be generated within a single millisecond while preserving strict sort order. If more than 4096 IDs are requested within one millisecond, the generator spin-waits for the next millisecond before continuing.

## Thread safety

`UuidV7Generator` is fully thread-safe. The monotonic counter state is managed with an `AtomicLong` and a compare-and-swap loop — no locking required.

## Maven dependency

```xml
<dependency>
    <groupId>com.stash</groupId>
    <artifactId>uuid-v7</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```