package com.stash.platform.user.repository;

import com.stash.platform.user.domain.BetaAllowlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BetaAllowlistRepository extends JpaRepository<BetaAllowlistEntry, UUID> {

    /**
     * Case-insensitive lookup — the allowlist check must not be bypassable by
     * using a different capitalisation of the invited email.
     */
    @Query("""
            SELECT e FROM BetaAllowlistEntry e
            WHERE lower(e.email) = lower(:email)
            """)
    Optional<BetaAllowlistEntry> findByEmailIgnoreCase(@Param("email") String email);

    @Query("""
            SELECT COUNT(e) > 0 FROM BetaAllowlistEntry e
            WHERE lower(e.email) = lower(:email)
            """)
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
