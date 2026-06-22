package com.stash.platform.user.service;



import com.stash.platform.notification.service.EmailSender;

import com.stash.platform.user.api.dto.SignupRequest;

import com.stash.platform.user.api.dto.UpdateUserProfileRequest;

import com.stash.platform.user.api.dto.UserProfileResponse;

import com.stash.platform.user.domain.User;

import com.stash.platform.user.repository.EmailVerificationTokenRepository;

import com.stash.platform.user.repository.UserRepository;

import com.stash.shared.apierrors.StashApiException;

import org.junit.jupiter.api.*;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.http.HttpStatus;

import org.springframework.test.context.DynamicPropertyRegistry;

import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;

import org.testcontainers.junit.jupiter.Container;

import org.testcontainers.junit.jupiter.Testcontainers;



import static org.assertj.core.api.Assertions.*;



@SpringBootTest

@Testcontainers

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)

@DisplayName("UserProfileService")

class UserProfileServiceTest {



    @Container

    static PostgreSQLContainer<?> postgres =

            new PostgreSQLContainer<>("postgres:16")

                    .withDatabaseName("monolith_test")

                    .withUsername("test")

                    .withPassword("test");



    @DynamicPropertySource

    static void configure(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url",      postgres::getJdbcUrl);

        registry.add("spring.datasource.username", postgres::getUsername);

        registry.add("spring.datasource.password", postgres::getPassword);

    }



    @Autowired UserProfileService userProfileService;

    @Autowired SignupService signupService;

    @Autowired UserRepository userRepository;

    @Autowired EmailVerificationTokenRepository tokenRepository;

    @MockBean  EmailSender emailSender;



    private User user;



    @BeforeEach

    void setUp() {

        tokenRepository.deleteAll();

        userRepository.deleteAll();



        signupService.signup(new SignupRequest(

                "profile-test@example.com", "Str0ng!Pass", "Original Name", null));

        user = userRepository.findByEmail("profile-test@example.com").orElseThrow();

    }



    private UpdateUserProfileRequest req(String displayName, String phone) {

        return new UpdateUserProfileRequest(displayName, phone);

    }



// ── GET ───────────────────────────────────────────────────────────────



    @Nested

    @DisplayName("getProfile()")

    class GetProfile {



        @Test

        @DisplayName("returns all expected fields")

        void returns_expected_fields() {

            UserProfileResponse profile = userProfileService.getProfile(user.getId());



            assertThat(profile.id()).isEqualTo(user.getId());

            assertThat(profile.email()).isEqualTo("profile-test@example.com");

            assertThat(profile.displayName()).isEqualTo("Original Name");

            assertThat(profile.kycStatus()).isEqualTo("PENDING");

            assertThat(profile.subscriptionTier()).isEqualTo("FREE");

            assertThat(profile.accountStatus()).isEqualTo("ACTIVE");

            assertThat(profile.createdAt()).isNotNull();

        }



        @Test

        @DisplayName("response object has no field for ghana_card_number")

        void no_ghana_card_field() {

            UserProfileResponse profile = userProfileService.getProfile(user.getId());

// Structural guarantee: the record has no ghanaCardNumber accessor at all.

// This test documents the contract — compile-time enforced, not runtime.

            assertThat(profile.toString()).doesNotContain("ghana");

        }



        @Test

        @DisplayName("response object has no field for password")

        void no_password_field() {

            UserProfileResponse profile = userProfileService.getProfile(user.getId());

            assertThat(profile.toString())

                    .doesNotContain("password")

                    .doesNotContain("$2a$");

        }

    }



// ── PATCH happy path ─────────────────────────────────────────────────



    @Nested

    @DisplayName("updateProfile() — happy path")

    class UpdateHappy {



        @Test

        @DisplayName("updates display_name")

        void updates_display_name() {

            UserProfileResponse updated = userProfileService.updateProfile(

                    user.getId(), req("New Name", null));



            assertThat(updated.displayName()).isEqualTo("New Name");

        }



        @Test

        @DisplayName("sets phone (E.164 format input)")

        void sets_phone_e164() {

            UserProfileResponse updated = userProfileService.updateProfile(

                    user.getId(), req(null, "+233501234567"));



            assertThat(updated.phone()).isEqualTo("+233501234567");

        }



        @Test

        @DisplayName("sets phone (local format input) normalised to E.164")

        void sets_phone_local_format_normalised() {

            UserProfileResponse updated = userProfileService.updateProfile(

                    user.getId(), req(null, "0501234567"));



            assertThat(updated.phone()).isEqualTo("+233501234567");

        }

    }



// ── PATCH: forbidden fields silently ignored ───────────────────────────



    @Nested

    @DisplayName("updateProfile() — forbidden fields are structurally absent")

    class UpdateForbiddenFields {



        @Test

        @DisplayName("kyc_status remains unchanged regardless of any update attempt")

        void kyc_status_unchanged() {

// UpdateUserProfileRequest has no field for kyc_status — there is

// no way to even attempt setting it through this DTO. We verify

// the invariant holds after a normal update.

            userProfileService.updateProfile(user.getId(), req("Whatever Name", null));



            User reloaded = userRepository.findByIdIncludingDeleted(user.getId()).orElseThrow();

            assertThat(reloaded.getKycStatus().name()).isEqualTo("PENDING"); // unchanged

        }



        @Test

        @DisplayName("account_status remains unchanged regardless of any update attempt")

        void account_status_unchanged() {

            userProfileService.updateProfile(user.getId(), req("Whatever Name", null));



            User reloaded = userRepository.findByIdIncludingDeleted(user.getId()).orElseThrow();

            assertThat(reloaded.getAccountStatus().name()).isEqualTo("ACTIVE"); // unchanged

        }



        @Test

        @DisplayName("subscription_tier remains unchanged regardless of any update attempt")

        void subscription_tier_unchanged() {

            userProfileService.updateProfile(user.getId(), req("Whatever Name", null));



            User reloaded = userRepository.findByIdIncludingDeleted(user.getId()).orElseThrow();

            assertThat(reloaded.getSubscriptionTier().name()).isEqualTo("FREE"); // unchanged

        }

    }



// ── Phone frozen after first set ────────────────────────────────────────



    @Nested

    @DisplayName("updateProfile() — phone frozen after first set (v0.2 scope)")

    class PhoneFrozen {



        @Test

        @DisplayName("second phone update attempt returns 409")

        void second_phone_update_returns_409() {

            userProfileService.updateProfile(user.getId(), req(null, "+233501234567"));



            assertThatExceptionOfType(StashApiException.class)

                    .isThrownBy(() -> userProfileService.updateProfile(

                            user.getId(), req(null, "+233509999999")))

                    .satisfies(ex ->

                            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT));

        }



        @Test

        @DisplayName("phone is unchanged after a rejected second attempt")

        void phone_unchanged_after_rejection() {

            userProfileService.updateProfile(user.getId(), req(null, "+233501234567"));



            try {

                userProfileService.updateProfile(user.getId(), req(null, "+233509999999"));

            } catch (StashApiException ignored) {}



            User reloaded = userRepository.findByIdIncludingDeleted(user.getId()).orElseThrow();

            assertThat(reloaded.getPhone()).isEqualTo("+233501234567");

        }

    }



// ── Logging ───────────────────────────────────────────────────────────



    @Test

    @DisplayName("ghana_card_number never appears in any log output during profile read/update")

    void ghana_card_number_not_in_logs() {

        ch.qos.logback.classic.Logger logger =

                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(UserProfileService.class);

        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =

                new ch.qos.logback.core.read.ListAppender<>();

        appender.start();

        logger.addAppender(appender);



        userProfileService.getProfile(user.getId());

        userProfileService.updateProfile(user.getId(), req("Logged Name", null));



        for (var event : appender.list) {

            assertThat(event.getFormattedMessage()).doesNotContain("ghana");

        }



        logger.detachAppender(appender);

    }

}