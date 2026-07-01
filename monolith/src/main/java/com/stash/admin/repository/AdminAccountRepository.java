package com.stash.admin.repository;

import com.stash.admin.domain.AdminAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, UUID> {

    @Query("""
            SELECT a FROM AdminAccountEntity a
            WHERE lower(a.email) = lower(:email)
            """)
    Optional<AdminAccountEntity> findByEmailIgnoreCase(@Param("email") String email);

    @Query("""
            SELECT COUNT(a) > 0 FROM AdminAccountEntity a
            WHERE a.accountType = 'SUPER'
            """)
    boolean superAdminExists();
}
