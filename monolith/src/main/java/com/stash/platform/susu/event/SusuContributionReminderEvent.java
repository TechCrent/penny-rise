package com.stash.platform.susu.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Published AFTER_COMMIT when the reminder job emits a contribution reminder.
 * The v0.5 notification worker consumes this event to dispatch push/email/in-app.
 *
 * Not at-least-once in v0.4 (monolith outbox deferred to v0.5). If the JVM
 * crashes between the reminder row INSERT and the AFTER_COMMIT publish, the row
 * exists but the event was not published. On the next hourly job run, the
 * reminder row already exists so the INSERT is skipped — no double event —
 * but the event is also not retried. Acceptable for v0.4; fixed in v0.5 by
 * the monolith transactional outbox.
 */
public class SusuContributionReminderEvent extends ApplicationEvent {

    public static final String TYPE_48H = "48H";
    public static final String TYPE_24H = "24H";

    private final UUID    groupId;
    private final UUID    roundId;
    private final UUID    contributionId;
    private final UUID    memberUserId;
    private final String  reminderType;
    private final long    amountPesewas;
    private final Instant dueDate;
    private final String  correlationId;
    private final Instant emittedAt;

    public SusuContributionReminderEvent(Object source, UUID groupId, UUID roundId,
                                          UUID contributionId, UUID memberUserId,
                                          String reminderType, long amountPesewas,
                                          Instant dueDate, String correlationId,
                                          Instant emittedAt) {
        super(source);
        this.groupId        = groupId;
        this.roundId        = roundId;
        this.contributionId = contributionId;
        this.memberUserId   = memberUserId;
        this.reminderType   = reminderType;
        this.amountPesewas  = amountPesewas;
        this.dueDate        = dueDate;
        this.correlationId  = correlationId;
        this.emittedAt      = emittedAt;
    }

    public UUID    getGroupId()        { return groupId; }
    public UUID    getRoundId()        { return roundId; }
    public UUID    getContributionId() { return contributionId; }
    public UUID    getMemberUserId()   { return memberUserId; }
    public String  getReminderType()   { return reminderType; }
    public long    getAmountPesewas()  { return amountPesewas; }
    public Instant getDueDate()        { return dueDate; }
    public String  getCorrelationId()  { return correlationId; }
    public Instant getEmittedAt()      { return emittedAt; }
}
