package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.domain.SusuRoundEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuRoundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SusuPotIntegrityJob {

    private static final Logger log = LoggerFactory.getLogger(SusuPotIntegrityJob.class);
    private static final int    BATCH_SIZE = 50;

    private final SusuGroupRepository        groupRepo;
    private final SusuRoundRepository        roundRepo;
    private final SusuPotIntegrityChecker    checker;

    public SusuPotIntegrityJob(SusuGroupRepository groupRepo,
                                SusuRoundRepository roundRepo,
                                SusuPotIntegrityChecker checker) {
        this.groupRepo = groupRepo;
        this.roundRepo = roundRepo;
        this.checker   = checker;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void sweep() {
        log.info("SusuPotIntegrityJob: starting nightly sweep");

        int checked = 0, drifted = 0, skipped = 0;
        int page = 0;
        List<SusuGroupEntity> batch;

        do {
            batch = groupRepo.findByStatus("ACTIVE", PageRequest.of(page++, BATCH_SIZE));

            for (SusuGroupEntity group : batch) {
                try {
                    Integer currentRound = group.getCurrentRoundNumber();
                    if (currentRound == null) {
                        skipped++;
                        continue;
                    }

                    SusuRoundEntity round = roundRepo
                            .findByGroupAndRoundNumber(group.getId(), currentRound)
                            .orElse(null);

                    if (round == null) {
                        log.warn("SusuPotIntegrityJob: group={} currentRound={} not found. Skipping.",
                                group.getId(), currentRound);
                        skipped++;
                        continue;
                    }

                    if ("PENDING".equals(round.getStatus())) {
                        skipped++;
                        continue;
                    }

                    boolean balanced = checker.checkQuietly(group.getId(), round.getId());
                    if (balanced) {
                        checked++;
                    } else {
                        drifted++;
                    }

                } catch (Exception e) {
                    skipped++;
                    log.error("SusuPotIntegrityJob: unexpected error for group={}: {}",
                            group.getId(), e.getMessage());
                }
            }
        } while (batch.size() == BATCH_SIZE);

        log.info("SusuPotIntegrityJob: complete. checked={} drifted={} skipped={}",
                checked, drifted, skipped);

        if (drifted > 0) {
            log.error("[P0_ALERT] SusuPotIntegrityJob: {} group(s) have pot drift. " +
                      "Immediate ops review required.", drifted);
        }
    }
}
