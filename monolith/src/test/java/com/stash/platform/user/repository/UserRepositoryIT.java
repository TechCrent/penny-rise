package com.stash.platform.user.repository;

import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
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

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("monolith_db")
                    .withUsername("stash")
                    .withPassword("stash_secret");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    private User buildUser(String email, String phone) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName("Test User");
        user.setPhone(phone);
        user.setKycStatus(KycStatus.PENDING);
        user.setSubscriptionTier(SubscriptionTier.FREE);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setPasswordHash("$2a$12$placeholder");
        return user;
    }

    @Test
    void insert_and_findByEmail_returns_user() {
        userRepository.save(buildUser("kwame@example.com", "+233201234567"));

        Optional<User> result = userRepository.findByEmail("kwame@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("kwame@example.com");
    }

    @Test
    void findByPhone_returns_correct_user() {
        userRepository.save(buildUser("ama@example.com", "+233209876543"));

        Optional<User> result = userRepository.findByPhone("+233209876543");

        assertThat(result).isPresent();
        assertThat(result.get().getPhone()).isEqualTo("+233209876543");
    }

    @Test
    void soft_deleted_user_is_not_returned_by_findByEmail() {
        User user = buildUser("kofi@example.com", "+233200000001");
        userRepository.save(user);

        // Soft-delete the user
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        Optional<User> result = userRepository.findByEmail("kofi@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void soft_deleted_user_is_not_returned_by_findByPhone() {
        User user = buildUser("akua@example.com", "+233200000002");
        userRepository.save(user);

        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        Optional<User> result = userRepository.findByPhone("+233200000002");

        assertThat(result).isEmpty();
    }

    @Test
    void active_user_is_returned_while_different_user_is_soft_deleted() {
        userRepository.save(buildUser("active@example.com", "+233200000003"));

        User deleted = buildUser("deleted@example.com", "+233200000004");
        userRepository.save(deleted);
        deleted.setDeletedAt(Instant.now());
        userRepository.save(deleted);

        assertThat(userRepository.findByEmail("active@example.com")).isPresent();
        assertThat(userRepository.findByEmail("deleted@example.com")).isEmpty();
    }
}