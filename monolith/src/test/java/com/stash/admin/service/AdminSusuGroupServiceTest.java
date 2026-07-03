package com.stash.admin.service;

import com.stash.admin.integration.IntegrationPaymentsClient;
import com.stash.admin.repository.AdminAuditActionRepository;
import com.stash.platform.susu.domain.SusuContributionEntity;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminSusuGroupServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID LEDGER_ACCOUNT_ID = UUID.randomUUID();

    private final SusuGroupRepository groupRepo = mock(SusuGroupRepository.class);
    private final SusuMembershipRepository membershipRepo = mock(SusuMembershipRepository.class);
    private final SusuRoundRepository roundRepo = mock(SusuRoundRepository.class);
    private final SusuContributionRepository contributionRepo = mock(SusuContributionRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final IntegrationPaymentsClient paymentsClient = mock(IntegrationPaymentsClient.class);
    private final AdminAuditActionRepository auditRepo = mock(AdminAuditActionRepository.class);

    private final AdminSusuGroupService service = new AdminSusuGroupService(
            groupRepo, membershipRepo, roundRepo, contributionRepo, userRepo,
            paymentsClient, auditRepo, FIXED_CLOCK);

    @BeforeEach
    void setUp() {
        when(auditRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SusuGroupEntity flaggedGroup() {
        SusuGroupEntity group = mock(SusuGroupEntity.class);
        when(group.getId()).thenReturn(GROUP_ID);
        when(group.getName()).thenReturn("Legon Roommates Susu");
        when(group.getOrganiserUserId()).thenReturn(UUID.randomUUID());
        when(group.isFlaggedForReview()).thenReturn(true);
        when(group.getFlaggedAt()).thenReturn(Instant.parse("2026-06-30T00:05:00Z"));
        when(group.getLedgerAccountId()).thenReturn(LEDGER_ACCOUNT_ID);
        return group;
    }

    @Test
    @DisplayName("listFlagged returns groups with last-shortfall details (waived-penalty contribution) and pot balance")
    void listFlaggedReturnsShortfallDetails() {
        var group = flaggedGroup();
        when(groupRepo.findByFlaggedForReview(true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(group)));

        var waivedContribution = mock(SusuContributionEntity.class);
        UUID memberUserId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        when(waivedContribution.getMemberUserId()).thenReturn(memberUserId);
        when(waivedContribution.getSusuRoundId()).thenReturn(roundId);
        when(waivedContribution.getCreatedAt()).thenReturn(Instant.parse("2026-06-30T00:00:00Z"));
        when(contributionRepo.findWaivedPenaltyContributions(GROUP_ID, PageRequest.of(0, 1)))
                .thenReturn(List.of(waivedContribution));

        var round = mock(SusuRoundEntity.class);
        when(round.getRoundNumber()).thenReturn(3);
        when(roundRepo.findById(roundId)).thenReturn(Optional.of(round));

        when(paymentsClient.getLedgerAccountBalance(LEDGER_ACCOUNT_ID)).thenReturn(120000L);

        var response = service.listFlagged(0, 20);

        assertThat(response.items()).hasSize(1);
        var item = response.items().get(0);
        assertThat(item.lastShortfallRoundNumber()).isEqualTo(3);
        assertThat(item.lastShortfallMemberUserId()).isEqualTo(memberUserId);
        assertThat(item.potBalancePesewas()).isEqualTo(120000L);
    }

    @Test
    @DisplayName("listFlagged with no waived-penalty contribution found returns null shortfall fields, not an error")
    void listFlaggedWithNoShortfallFound() {
        var group = flaggedGroup();
        when(groupRepo.findByFlaggedForReview(true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(group)));
        when(contributionRepo.findWaivedPenaltyContributions(GROUP_ID, PageRequest.of(0, 1)))
                .thenReturn(List.of());
        when(paymentsClient.getLedgerAccountBalance(LEDGER_ACCOUNT_ID)).thenReturn(0L);

        var response = service.listFlagged(0, 20);

        assertThat(response.items().get(0).lastShortfallRoundNumber()).isNull();
        assertThat(response.items().get(0).lastShortfallMemberUserId()).isNull();
    }

    @Test
    @DisplayName("clearFlag on a flagged group succeeds and writes an audit row")
    void clearFlagSucceeds() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(flaggedGroup()));

        service.clearFlag(GROUP_ID, ADMIN_ID);

        verify(groupRepo).clearFlag(GROUP_ID);
        verify(auditRepo).save(argThat(a ->
                "SUSU_GROUP_FLAG_CLEARED".equals(a.getActionType())
                        && "SUSU_GROUP".equals(a.getTargetType())
                        && GROUP_ID.equals(a.getTargetId())
                        && ADMIN_ID.equals(a.getAdminAccountId())));
    }

    @Test
    @DisplayName("clearFlag on an already-unflagged group returns 409, not a silent success")
    void clearFlagOnUnflaggedReturns409() {
        var unflagged = flaggedGroup();
        when(unflagged.isFlaggedForReview()).thenReturn(false);
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(unflagged));

        assertThatThrownBy(() -> service.clearFlag(GROUP_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(groupRepo, never()).clearFlag(any());
        verifyNoInteractions(auditRepo);
    }

    @Test
    @DisplayName("clearFlag on a nonexistent group returns 404")
    void clearFlagNotFoundReturns404() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clearFlag(GROUP_ID, ADMIN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("getDetail on a nonexistent group returns 404")
    void getDetailNotFoundReturns404() {
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(GROUP_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("getDetail with no current round returns an empty contributions list, not an error")
    void getDetailWithNoCurrentRound() {
        var group = flaggedGroup();
        when(group.getCurrentRoundNumber()).thenReturn(null);
        when(groupRepo.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(membershipRepo.findActiveMembersByGroup(GROUP_ID)).thenReturn(List.of());
        when(userRepo.findAllById(any())).thenReturn(List.of());
        when(paymentsClient.getLedgerAccountBalance(LEDGER_ACCOUNT_ID)).thenReturn(0L);

        var detail = service.getDetail(GROUP_ID);

        assertThat(detail.currentRoundContributions()).isEmpty();
    }
}
