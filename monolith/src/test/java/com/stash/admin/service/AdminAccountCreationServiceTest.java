package com.stash.admin.service;

import com.stash.admin.api.dto.CreateAdminAccountRequest;
import com.stash.admin.domain.AdminAccountEntity;
import com.stash.admin.rbac.AdminRoleMapping;
import com.stash.admin.repository.AdminAccountRepository;
import com.stash.admin.service.AdminJwtService.AdminTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class AdminAccountCreationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private AdminAccountRepository accountRepo;
    private AdminAccountCreationService service;

    @BeforeEach
    void setUp() {
        accountRepo = mock(AdminAccountRepository.class);
        service = new AdminAccountCreationService(new AdminRoleMapping(), accountRepo, FIXED_CLOCK, 4);
    }

    @Test
    @DisplayName("VICE_SUPER creating a TAB succeeds")
    void viceSuperCreatesTab() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "VICE_SUPER", null);
        assertThatCode(() -> service.assertCanCreate(claims, "TAB")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("VICE_SUPER creating a VICE_SUPER returns 403 ADMIN_INSUFFICIENT_ROLE")
    void viceSuperCannotCreateViceSuper() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "VICE_SUPER", null);
        assertThatThrownBy(() -> service.assertCanCreate(claims, "VICE_SUPER"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(FORBIDDEN);
                    assertThat(e.getReason()).contains("ADMIN_INSUFFICIENT_ROLE");
                });
    }

    @Test
    @DisplayName("TAB creating anyone is rejected")
    void tabCannotCreateAnyone() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "TAB", "KYC");
        assertThatThrownBy(() -> service.assertCanCreate(claims, "TAB"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("unparseable target accountType is rejected, not NPE'd")
    void garbageTargetTypeRejected() {
        var claims = new AdminTokenClaims(UUID.randomUUID(), "SUPER", null);
        assertThatThrownBy(() -> service.assertCanCreate(claims, "NOT_A_TYPE"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("create() persists a new admin account with a hashed password, never the raw one")
    void createPersistsHashedPassword() {
        var creator = new AdminTokenClaims(UUID.randomUUID(), "SUPER", null);
        var request = new CreateAdminAccountRequest(
                "new-tab@stash.local", "Str0ng!Pass1", "New TAB", "TAB", "KYC");
        when(accountRepo.findByEmailIgnoreCase("new-tab@stash.local")).thenReturn(Optional.empty());

        var response = service.create(creator, request);

        assertThat(response.email()).isEqualTo("new-tab@stash.local");
        assertThat(response.accountType()).isEqualTo("TAB");
        assertThat(response.isActive()).isTrue();

        var captor = org.mockito.ArgumentCaptor.forClass(AdminAccountEntity.class);
        verify(accountRepo).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("Str0ng!Pass1");
        assertThat(captor.getValue().getPasswordHash()).startsWith("$2a$");
    }

    @Test
    @DisplayName("create() rejects a duplicate email with 409 ADMIN_EMAIL_ALREADY_REGISTERED")
    void createRejectsDuplicateEmail() {
        var creator = new AdminTokenClaims(UUID.randomUUID(), "SUPER", null);
        var request = new CreateAdminAccountRequest(
                "existing@stash.local", "Str0ng!Pass1", "Existing", "TAB", "KYC");
        when(accountRepo.findByEmailIgnoreCase("existing@stash.local"))
                .thenReturn(Optional.of(AdminAccountEntity.create(
                        "existing@stash.local", "hash", "Existing", "KYC", "TAB",
                        UUID.randomUUID(), Instant.now(FIXED_CLOCK))));

        assertThatThrownBy(() -> service.create(creator, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .contains("ADMIN_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("list() maps the repository page to summaries")
    void listMapsToSummaries() {
        var entity = AdminAccountEntity.create(
                "staff@stash.local", "hash", "Staff", "KYC", "TAB",
                UUID.randomUUID(), Instant.now(FIXED_CLOCK));
        when(accountRepo.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        var page = service.list(PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).email()).isEqualTo("staff@stash.local");
    }

    @Test
    @DisplayName("deactivate() sets isActive=false and deactivatedAt")
    void deactivateSetsInactive() {
        var entity = AdminAccountEntity.create(
                "staff@stash.local", "hash", "Staff", "KYC", "TAB",
                UUID.randomUUID(), Instant.now(FIXED_CLOCK));
        when(accountRepo.findById(entity.getId())).thenReturn(Optional.of(entity));

        service.deactivate(entity.getId());

        assertThat(entity.isActive()).isFalse();
        assertThat(entity.getDeactivatedAt()).isEqualTo(Instant.now(FIXED_CLOCK));
        verify(accountRepo).save(entity);
    }

    @Test
    @DisplayName("deactivate() on unknown id returns 404 ADMIN_ACCOUNT_NOT_FOUND")
    void deactivateUnknownIdReturns404() {
        UUID missingId = UUID.randomUUID();
        when(accountRepo.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(missingId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    var e = (ResponseStatusException) ex;
                    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
                });
    }
}
