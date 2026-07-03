package com.stash.platform.transfer.api;

import com.stash.platform.subscription.policy.SubscriptionPolicy;
import com.stash.platform.transfer.domain.MonthlyTransferQuotaEntity;
import com.stash.platform.transfer.repository.MonthlyTransferQuotaRepository;
import com.stash.platform.user.domain.SubscriptionTier;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferQuotaControllerTest {

    private static final UUID CALLER_ID = UUID.randomUUID();

    private final MonthlyTransferQuotaRepository quotaRepo = mock(MonthlyTransferQuotaRepository.class);
    private final UserRepository                 userRepo  = mock(UserRepository.class);
    private final TransferQuotaController controller =
            new TransferQuotaController(quotaRepo, userRepo, new SubscriptionPolicy());

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now();
        when(quotaRepo.findByUserAndMonth(eq(CALLER_ID), eq(today.getYear()), eq(today.getMonthValue())))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("FREE user: limit is 5, no usage yet — 5 remaining")
    void freeUserNoUsage() {
        when(userRepo.findById(CALLER_ID)).thenReturn(Optional.of(userWithTier(SubscriptionTier.FREE)));

        var result = controller.getQuota(CALLER_ID);

        assertThat(result.freeQuotaLimit()).isEqualTo(5);
        assertThat(result.freeTransfersRemaining()).isEqualTo(5);
        assertThat(result.nextTransferIsFree()).isTrue();
    }

    @Test
    @DisplayName("PREMIUM user: limit is 20, not the FREE tier's 5 (v0.5-030 fix)")
    void premiumUserLimitIs20() {
        when(userRepo.findById(CALLER_ID)).thenReturn(Optional.of(userWithTier(SubscriptionTier.PREMIUM)));

        var result = controller.getQuota(CALLER_ID);

        assertThat(result.freeQuotaLimit()).isEqualTo(20);
        assertThat(result.freeTransfersRemaining()).isEqualTo(20);
    }

    @Test
    @DisplayName("PREMIUM user with 6 used this month: 14 remaining, still free (would be paid on FREE tier)")
    void premiumUserPartialUsage() {
        when(userRepo.findById(CALLER_ID)).thenReturn(Optional.of(userWithTier(SubscriptionTier.PREMIUM)));
        LocalDate today = LocalDate.now();
        MonthlyTransferQuotaEntity quota = MonthlyTransferQuotaEntity.create(CALLER_ID, today.getYear(), today.getMonthValue());
        for (int i = 0; i < 6; i++) quota.consumeOneTransfer(20);
        when(quotaRepo.findByUserAndMonth(CALLER_ID, today.getYear(), today.getMonthValue()))
                .thenReturn(Optional.of(quota));

        var result = controller.getQuota(CALLER_ID);

        assertThat(result.freeTransfersUsed()).isEqualTo(6);
        assertThat(result.freeTransfersRemaining()).isEqualTo(14);
        assertThat(result.nextTransferIsFree()).isTrue();
    }

    @Test
    @DisplayName("FREE user with quota exhausted: next transfer costs GHS 2")
    void freeUserQuotaExhausted() {
        when(userRepo.findById(CALLER_ID)).thenReturn(Optional.of(userWithTier(SubscriptionTier.FREE)));
        LocalDate today = LocalDate.now();
        MonthlyTransferQuotaEntity quota = MonthlyTransferQuotaEntity.create(CALLER_ID, today.getYear(), today.getMonthValue());
        for (int i = 0; i < 5; i++) quota.consumeOneTransfer(5);
        when(quotaRepo.findByUserAndMonth(CALLER_ID, today.getYear(), today.getMonthValue()))
                .thenReturn(Optional.of(quota));

        var result = controller.getQuota(CALLER_ID);

        assertThat(result.freeTransfersRemaining()).isZero();
        assertThat(result.nextTransferIsFree()).isFalse();
        assertThat(result.feeIfTransferNowPesewas()).isEqualTo(200L);
        assertThat(result.feeIfTransferNowCedis()).isEqualTo("2.00");
    }

    private static User userWithTier(SubscriptionTier tier) {
        User u = new User();
        u.setSubscriptionTier(tier);
        return u;
    }
}
