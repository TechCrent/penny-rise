package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.repository.SusuContributionRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SusuPotIntegrityChecker {

    private static final Logger log = LoggerFactory.getLogger(SusuPotIntegrityChecker.class);

    private final SusuContributionRepository contributionRepo;
    private final SusuGroupRepository        groupRepo;
    private final SusuPotBalanceClient       balanceClient;
    private final Counter                    driftCounter;

    public SusuPotIntegrityChecker(
            SusuContributionRepository contributionRepo,
            SusuGroupRepository groupRepo,
            SusuPotBalanceClient balanceClient,
            MeterRegistry meterRegistry) {
        this.contributionRepo = contributionRepo;
        this.groupRepo        = groupRepo;
        this.balanceClient    = balanceClient;
        this.driftCounter     = Counter.builder("susu_pot_integrity_drift_count")
                .description("Number of susu pot integrity drift incidents detected")
                .register(meterRegistry);
    }

    public void check(UUID groupId, UUID roundId) {
        SusuGroupEntity group = groupRepo.findById(groupId).orElse(null);
        if (group == null || group.getLedgerAccountId() == null) {
            log.warn("SusuPotIntegrityChecker: group={} has no SUSU_POT ledger account. " +
                     "Check skipped (group may not be activated yet).", groupId);
            return;
        }

        long expectedPesewas = contributionRepo.sumCollectedAmountForRound(roundId);
        long actualPesewas   = balanceClient.getBalance(group.getLedgerAccountId());

        if (expectedPesewas != actualPesewas) {
            long drift = actualPesewas - expectedPesewas;

            log.error("[P0_ALERT] SusuPotIntegrityChecker: DRIFT DETECTED. " +
                      "group={} round={} potAccount={} " +
                      "expected={}p actual={}p drift={}p",
                    groupId, roundId, group.getLedgerAccountId(),
                    expectedPesewas, actualPesewas, drift);

            driftCounter.increment();

            throw new SusuIntegrityCheckException(
                    String.format("[P0] SUSU_POT integrity drift detected: " +
                            "group=%s round=%s expected=%dp actual=%dp drift=%dp",
                            groupId, roundId, expectedPesewas, actualPesewas, drift),
                    expectedPesewas, actualPesewas
            );
        }

        log.debug("SusuPotIntegrityChecker: group={} round={} pot={}p balanced",
                groupId, roundId, actualPesewas);
    }

    public boolean checkQuietly(UUID groupId, UUID roundId) {
        try {
            check(groupId, roundId);
            return true;
        } catch (SusuIntegrityCheckException e) {
            return false;
        }
    }
}
