package com.stash.platform.user.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.stash.shared.uuidv7.UuidV7Generator;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JPA entity for user_module.deletion_requests.
 * See Schema doc §1.4.
 *
 * <p>blockers_at_submission is JSONB — mapped via hypersistence-utils'
 * JsonType so Hibernate can read/write it as a JsonNode without manual
 * serialisation boilerplate.
 */
@Entity
@Table(schema = "user_module", name = "deletion_requests")
@Getter
@Setter
@NoArgsConstructor
public class DeletionRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Type(JsonType.class)
    @Column(name = "blockers_at_submission", columnDefinition = "jsonb")
    private JsonNode blockersAtSubmission;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "scheduled_completion_at", nullable = false, updatable = false)
    private Instant scheduledCompletionAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_FAILED    = "FAILED";

    public DeletionRequest(UUID userId, JsonNode blockersAtSubmission) {
        this.id                    = UuidV7Generator.generate();
        this.userId                = userId;
        this.status                = STATUS_PENDING;
        this.blockersAtSubmission  = blockersAtSubmission;
        this.submittedAt           = Instant.now();
        this.scheduledCompletionAt = Instant.now().plus(30, ChronoUnit.DAYS);
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public void cancel() {
        this.status      = STATUS_CANCELLED;
        this.cancelledAt = Instant.now();
    }

    public void markCompleted(Instant now) {
        this.status      = STATUS_COMPLETED;
        this.completedAt = now;
    }

    public void incrementAttempt() {
        this.attempts++;
    }

    public void markFailed() {
        this.status = STATUS_FAILED;
    }
}
