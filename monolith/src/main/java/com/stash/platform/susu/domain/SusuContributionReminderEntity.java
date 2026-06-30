package com.stash.platform.susu.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "susu_contribution_reminders", schema = "susu")
public class SusuContributionReminderEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "contribution_id", nullable = false) private UUID    contributionId;
    @Column(name = "reminder_type",   nullable = false) private String  reminderType;
    @Column(name = "emitted_at",      nullable = false) private Instant emittedAt;

    protected SusuContributionReminderEntity() {}

    public static SusuContributionReminderEntity of(UUID contributionId,
                                                     String reminderType,
                                                     Instant now) {
        SusuContributionReminderEntity e = new SusuContributionReminderEntity();
        e.contributionId = contributionId;
        e.reminderType   = reminderType;
        e.emittedAt      = now;
        return e;
    }

    public UUID    getId()             { return id; }
    public UUID    getContributionId() { return contributionId; }
    public String  getReminderType()   { return reminderType; }
    public Instant getEmittedAt()      { return emittedAt; }
}
