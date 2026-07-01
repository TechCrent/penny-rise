package com.stash.admin.repository;

import com.stash.admin.domain.AdminLoginAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AdminLoginAttemptRepository extends JpaRepository<AdminLoginAttemptEntity, UUID> {

    @Query("""
            SELECT COUNT(a) FROM AdminLoginAttemptEntity a
            WHERE lower(a.email) = lower(:email)
              AND a.succeeded    = false
              AND a.attemptedAt  > :since
            """)
    long countRecentFailures(@Param("email") String email, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(a) FROM AdminLoginAttemptEntity a
            WHERE lower(a.email) = lower(:email)
              AND a.succeeded    = false
              AND a.attemptedAt  > :since
            """)
    long countFailuresSince(@Param("email") String email, @Param("since") Instant since);

    @Query(value = """
            SELECT attempted_at FROM admin.admin_login_attempts
            WHERE lower(email) = lower(:email) AND succeeded = true
            ORDER BY attempted_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Instant findLastSuccessTime(@Param("email") String email);

    @Query(value = """
            SELECT attempted_at FROM admin.admin_login_attempts
            WHERE lower(email) = lower(:email) AND succeeded = false
            ORDER BY attempted_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Instant findMostRecentFailureTime(@Param("email") String email);
}
