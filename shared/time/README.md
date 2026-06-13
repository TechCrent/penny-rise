# shared/time

UTC time helpers and injectable `Clock` abstraction for the Stash platform.

## The rule: UTC everywhere

All timestamps in Stash are stored as `TIMESTAMPTZ` in UTC and processed as
`java.time.Instant` in Java. This is a hard convention from the Schema doc:
"Times in UTC. All timestamps are TIMESTAMPTZ stored in UTC."

Never use `LocalDateTime`, `Date`, or `Calendar` in business logic.
Never call `Instant.now()` directly — use the injected `Clock` instead.

## The injectable Clock

The `Clock` interface decouples business logic from the system clock, making
time-dependent code fully deterministic in tests.

### Production

```java
// Register as a Spring bean once per service:
@Configuration
public class TimeConfig {
    @Bean
    public Clock clock() {
        return SystemClock.INSTANCE;
    }
}

// Inject into your service:
@Service
public class VaultService {
    private final Clock clock;

    public VaultService(Clock clock) {
        this.clock = clock;
    }

    public void doSomething() {
        Instant now = clock.now(); // always UTC
    }
}
```

### Tests

```java
Clock fixed = FixedClock.of("2025-06-15T12:00:00Z");
VaultService service = new VaultService(fixed);

// clock.now() is deterministically 2025-06-15T12:00:00Z in every run
```

## Ghana local time — presentation only

Ghana Standard Time (GST) is UTC+0. Ghana does not observe daylight saving time.
`TimeUtils.toGhanaTime()` and `TimeUtils.formatForDisplay()` are provided for
the UI layer only — never use them in business logic, DB storage, or event payloads.

```java
// Display only:
String display = TimeUtils.formatForDisplay(vault.getCreatedAt()); // "15 Jun 2025, 12:00 PM"

// Wrong — never pass this to business logic:
ZonedDateTime local = TimeUtils.toGhanaTime(instant); // then store it
```

## Maven dependency

```xml
<dependency>
    <groupId>com.stash</groupId>
    <artifactId>time</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```