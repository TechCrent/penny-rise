package com.stash.platform.vault.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring application event published after a vault is unlocked.
 * {@link VaultUnlockedEventPublisher} listens and publishes to RabbitMQ
 * AFTER the transaction commits — same pattern as user.created.
 */
public class VaultUnlockedApplicationEvent extends ApplicationEvent {

    private final UUID    vaultId;
    private final UUID    ownerUserId;
    private final Instant unlockedAt;
    private final String  correlationId;

    public VaultUnlockedApplicationEvent(Object source, UUID vaultId,
                                          UUID ownerUserId, Instant unlockedAt,
                                          String correlationId) {
        super(source);
        this.vaultId       = vaultId;
        this.ownerUserId   = ownerUserId;
        this.unlockedAt    = unlockedAt;
        this.correlationId = correlationId;
    }

    public UUID    getVaultId()       { return vaultId; }
    public UUID    getOwnerUserId()   { return ownerUserId; }
    public Instant getUnlockedAt()    { return unlockedAt; }
    public String  getCorrelationId() { return correlationId; }
}
