package com.stash.platform.subscription.service;

import com.stash.platform.subscription.api.dto.FrozenSusuGroupPreview;
import com.stash.platform.susu.domain.SusuGroupEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Computes and applies susu-group freezing on subscription downgrade —
 * same oldest-first convention as VaultFreezingService.
 *
 * <p>Freezing here means the GROUP transitions to FROZEN (susu_groups.status,
 * V44) — the organiser's role/membership itself is untouched, only new
 * contribution prompts are blocked on the group (enforced in v0.5-030).
 */
@Service
public class SusuFreezingService {

    private final SusuGroupRepository susuGroupRepository;

    public SusuFreezingService(SusuGroupRepository susuGroupRepository) {
        this.susuGroupRepository = susuGroupRepository;
    }

    /** Read-only — computes what WOULD be frozen, commits nothing. */
    public List<FrozenSusuGroupPreview> previewExcessGroups(UUID userId, int organiserLimit) {
        return excessGroups(userId, organiserLimit).stream()
                .map(g -> new FrozenSusuGroupPreview(g.getId().toString(), g.getName()))
                .toList();
    }

    /** Actually flips status — called only from the downgrade commit path. */
    public void freezeExcessGroups(UUID userId, int organiserLimit) {
        for (SusuGroupEntity group : excessGroups(userId, organiserLimit)) {
            susuGroupRepository.freezeIfActive(group.getId());
        }
    }

    private List<SusuGroupEntity> excessGroups(UUID userId, int organiserLimit) {
        List<SusuGroupEntity> activeOldestFirst =
                susuGroupRepository.findActiveByOrganiserOrderByCreatedAtAsc(userId);
        if (activeOldestFirst.size() <= organiserLimit) {
            return List.of();
        }
        int excessCount = activeOldestFirst.size() - organiserLimit;
        return activeOldestFirst.subList(0, excessCount);
    }
}
