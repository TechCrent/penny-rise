package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Admin-facing susu membership query service.
 */
@Service
public class SusuMembershipService {

    private final SusuGroupRepository      susuGroupRepository;
    private final SusuMembershipRepository susuMembershipRepository;

    public SusuMembershipService(SusuGroupRepository susuGroupRepository,
                                  SusuMembershipRepository susuMembershipRepository) {
        this.susuGroupRepository      = susuGroupRepository;
        this.susuMembershipRepository = susuMembershipRepository;
    }

    public List<SusuMembershipEntity> listActiveMembershipsForUser(UUID userId) {
        return susuMembershipRepository.findActiveMembershipsByUser(userId);
    }

    public boolean groupExists(UUID groupId) {
        return susuGroupRepository.existsById(groupId);
    }

    /** True if the user has or had a membership in the group at any status (ACTIVE, LEFT, REMOVED). */
    public boolean hasOrHadMembership(UUID groupId, UUID userId) {
        return susuMembershipRepository.existsByGroupIdAndUserId(groupId, userId);
    }
}
