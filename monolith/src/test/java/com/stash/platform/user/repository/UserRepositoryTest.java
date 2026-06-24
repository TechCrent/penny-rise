package com.stash.platform.user.repository;

import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link UserRepository}.
 *
 * Uses Testcontainers to spin up a real Postgres 16 instance.
 * Flyway migrations run automatically via DataJpaTest + the migration
 * directory on the classpath — the same migrations that run in production.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository")
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("monolith_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UserRepository repository;

    // ── Helpers ───────────────────────────────────────────────────────────

    private User buildUser(String email) {
        return new User(email, "$2a$12$hashedpassword", "Test User");
    }

    private User buildUserWithPhone(String email, String phone) {
        User u = buildUser(email);
        u.setPhone(phone);
        return u;
    }

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
    }

    // ── Insert ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("insert")
    class Insert {

        @Test
        @DisplayName("persists a new user with UUID v7 primary key")
        void persists_with_uuid_v7() {
            User saved = repository.save(buildUser("alice@stash.com"));
            assertThat(saved.getId()).isNotNull();
            // UUID v7 version bits: version nibble is '7'
            assertThat(saved.getId().toString().charAt(14)).isEqualTo('7');
        }

        @Test
        @DisplayName("defaults to PENDING kyc_status, FREE tier, ACTIVE status")
        void correct_defaults() {
            User saved = repository.save(buildUser("bob@stash.com"));
            assertThat(saved.getKycStatus()).isEqualTo(KycStatus.PENDING);
            assertThat(saved.getSubscriptionTier()).isEqualTo(SubscriptionTier.FREE);
            assertThat(saved.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        @DisplayName("createdAt is populated on save")
        void created_at_populated() {
            User saved = repository.save(buildUser("carol@stash.com"));
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
        }
    }

    // ── findByEmail ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("returns user when email matches and user is active")
        void finds_active_user() {
            repository.save(buildUser("dave@stash.com"));
            Optional<User> found = repository.findByEmail("dave@stash.com");
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("dave@stash.com");
        }

        @Test
        @DisplayName("returns empty when email does not exist")
        void returns_empty_for_unknown_email() {
            assertThat(repository.findByEmail("nobody@stash.com")).isEmpty();
        }

        @Test
        @DisplayName("returns empty for a soft-deleted user")
        void returns_empty_for_soft_deleted() {
            User user = repository.save(buildUser("eve@stash.com"));
            user.softDelete();
            repository.save(user);

            assertThat(repository.findByEmail("eve@stash.com")).isEmpty();
        }
    }

    // ── findByPhone ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByPhone")
    class FindByPhone {

        @Test
        @DisplayName("returns user when phone matches and user is active")
        void finds_by_phone() {
            repository.save(buildUserWithPhone("frank@stash.com", "+233501234567"));
            Optional<User> found = repository.findByPhone("+233501234567");
            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("returns empty for a soft-deleted user")
        void returns_empty_for_soft_deleted() {
            User user = repository.save(buildUserWithPhone("grace@stash.com", "+233509876543"));
            user.softDelete();
            repository.save(user);

            assertThat(repository.findByPhone("+233509876543")).isEmpty();
        }

        @Test
        @DisplayName("multiple users with null phone do not conflict")
        void multiple_null_phones_allowed() {
            repository.save(buildUser("henry@stash.com"));
            repository.save(buildUser("iris@stash.com"));
            // Both have phone = null — partial unique index allows this
            assertThat(repository.findAll()).hasSize(2);
        }
    }

    // ── findByGhanaCardNumber ─────────────────────────────────────────────

    @Nested
    @DisplayName("findByGhanaCardNumber")
    class FindByGhanaCardNumber {

        @Test
        @DisplayName("returns user when card number matches")
        void finds_by_ghana_card() {
            User user = buildUser("james@stash.com");
            user.setGhanaCardNumber("GHA-1234567890");
            user.setKycStatus(KycStatus.APPROVED);
            repository.save(user);

            Optional<User> found = repository.findByGhanaCardNumber("GHA-1234567890");
            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("returns empty for soft-deleted user")
        void returns_empty_for_soft_deleted() {
            User user = buildUser("karen@stash.com");
            user.setGhanaCardNumber("GHA-9876543210");
            user.setKycStatus(KycStatus.APPROVED);
            user = repository.save(user);
            user.softDelete();
            repository.save(user);

            assertThat(repository.findByGhanaCardNumber("GHA-9876543210")).isEmpty();
        }
    }

    // ── Soft delete ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("soft delete")
    class SoftDelete {

        @Test
        @DisplayName("softDelete sets deletedAt and closes the account")
        void soft_delete_sets_fields() {
            User user = repository.save(buildUser("leo@stash.com"));
            user.softDelete();
            repository.save(user);

            Optional<User> byId = repository.findByIdIncludingDeleted(user.getId());
            assertThat(byId).isPresent();
            assertThat(byId.get().getDeletedAt()).isNotNull();
            assertThat(byId.get().getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
        }

        @Test
        @DisplayName("findById (default JPA) does NOT filter deleted users — use findByEmail instead")
        void standard_find_by_id_returns_deleted() {
            // Note: JPA's default findById does not apply our soft-delete filter.
            // Use the explicit query methods on this repository for user-facing queries.
            User user = repository.save(buildUser("maya@stash.com"));
            user.softDelete();
            repository.save(user);

            // findById still returns the row (no filter)
            assertThat(repository.findById(user.getId())).isPresent();
            // But findByEmail correctly hides it
            assertThat(repository.findByEmail("maya@stash.com")).isEmpty();
        }

        @Test
        @DisplayName("findByIdIncludingDeleted returns deleted users for admin use")
        void find_by_id_including_deleted_works() {
            User user = repository.save(buildUser("noah@stash.com"));
            user.softDelete();
            repository.save(user);

            Optional<User> found = repository.findByIdIncludingDeleted(user.getId());
            assertThat(found).isPresent();
            assertThat(found.get().isDeleted()).isTrue();
        }
    }

    // ── existsByEmail ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("existsByEmail")
    class ExistsByEmail {

        @Test
        @DisplayName("returns true for an existing active user")
        void returns_true_for_existing() {
            repository.save(buildUser("olivia@stash.com"));
            assertThat(repository.existsByEmail("olivia@stash.com")).isTrue();
        }

        @Test
        @DisplayName("returns false for a soft-deleted user")
        void returns_false_for_deleted() {
            User user = repository.save(buildUser("peter@stash.com"));
            user.softDelete();
            repository.save(user);

            assertThat(repository.existsByEmail("peter@stash.com")).isFalse();
        }
    }
}
