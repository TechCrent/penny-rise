package com.stash.payments.idempotency.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys", schema = "idempotency")
public class IdempotencyKey {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "key_value",   nullable = false, unique = true, length = 255)
    private String keyValue;

    @Column(name = "request_hash",  nullable = false, length = 255)
    private String requestHash;

    @Column(name = "request_path",  nullable = false, length = 500)
    private String requestPath;

    @Column(name = "state",         nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private IdempotencyState state;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_body", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    @Column(name = "created_at",  nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at",  nullable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String keyValue, String requestHash,
                          String requestPath, Instant createdAt, Instant expiresAt) {
        this.id          = UUID.randomUUID();
        this.keyValue    = keyValue;
        this.requestHash = requestHash;
        this.requestPath = requestPath;
        this.state       = IdempotencyState.PROCESSING;
        this.createdAt   = createdAt;
        this.expiresAt   = expiresAt;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public UUID getId()                  { return id; }
    public String getKeyValue()          { return keyValue; }
    public String getRequestHash()       { return requestHash; }
    public String getRequestPath()       { return requestPath; }
    public IdempotencyState getState()   { return state; }
    public Integer getResponseStatusCode() { return responseStatusCode; }
    public String getResponseBody()      { return responseBody; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getExpiresAt()        { return expiresAt; }

    // ── State transitions ─────────────────────────────────────────────────

    public void markCompleted(int statusCode, String body) {
        this.state               = IdempotencyState.COMPLETED;
        this.responseStatusCode  = statusCode;
        this.responseBody        = body;
    }

    public void markFailed(int statusCode, String body) {
        this.state               = IdempotencyState.FAILED;
        this.responseStatusCode  = statusCode;
        this.responseBody        = body;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }
}
