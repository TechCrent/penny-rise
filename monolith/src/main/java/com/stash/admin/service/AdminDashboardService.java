package com.stash.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stash.admin.api.dto.AdminDashboardSummaryResponse;
import com.stash.admin.client.KycAdminClient;
import com.stash.admin.repository.DisputeRepository;
import com.stash.platform.susu.repository.SusuGroupRepository;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.repository.UserRepository;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

/**
 * Aggregates the counts admins need at a glance — gap-analysis finding:
 * there was no dashboard/home page at all; login landed straight on
 * /kyc-queue with no summary view of what needs attention across KYC,
 * disputes, and susu groups.
 */
@Service
public class AdminDashboardService {

    private static final String OPEN_DISPUTE_STATUS = "OPEN";

    private final KycAdminClient kycAdminClient;
    private final DisputeRepository disputeRepository;
    private final SusuGroupRepository susuGroupRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AdminDashboardService(KycAdminClient kycAdminClient,
                                 DisputeRepository disputeRepository,
                                 SusuGroupRepository susuGroupRepository,
                                 UserRepository userRepository,
                                 ObjectMapper objectMapper) {
        this.kycAdminClient = kycAdminClient;
        this.disputeRepository = disputeRepository;
        this.susuGroupRepository = susuGroupRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public AdminDashboardSummaryResponse getSummary(String bearerToken) {
        return new AdminDashboardSummaryResponse(
                fetchPendingKycCount(bearerToken),
                userRepository.countByKycStatus(KycStatus.RESUBMISSION_REQUIRED),
                disputeRepository.countByStatus(OPEN_DISPUTE_STATUS),
                susuGroupRepository.countByFlaggedForReview(true)
        );
    }

    private long fetchPendingKycCount(String bearerToken) {
        var response = kycAdminClient.forward(
                HttpMethod.GET, "/api/v1/kyc/admin/queue/count", bearerToken, null);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return 0L;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("count").asLong(0L);
        } catch (Exception e) {
            return 0L;
        }
    }
}
