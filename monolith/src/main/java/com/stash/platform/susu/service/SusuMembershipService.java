package com.stash.platform.susu.service;

import com.stash.platform.susu.domain.SusuMembershipEntity;
import com.stash.platform.susu.repository.SusuMembershipRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Admin-facing susu membership query service.
 */
@Service
public class SusuMembershipService {

    private final SusuMembershipRepository susuMembershipRepository;

    public SusuMembershipService(SusuMembershipRepository susuMembershipRepository) {
        this.susuMembershipRepository = susuMembershipRepository;
    }

    public List<SusuMembershipEntity> listActiveMembershipsForUser(UUID userId) {
        return susuMembershipRepository.findActiveMembershipsByUser(userId);
    }
}
