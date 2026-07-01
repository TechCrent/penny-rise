package com.stash.platform.user.service;

import com.stash.platform.user.api.dto.AdminUserSearchCriteria;
import com.stash.platform.user.api.dto.AdminUserView;
import com.stash.platform.user.domain.AccountStatus;
import com.stash.platform.user.domain.KycStatus;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.masking.GhanaCardMasker;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Admin-facing user query service. Handles search and detail lookup with
 * Ghana Card masking applied before any data leaves this layer.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Page<AdminUserView> searchForAdmin(AdminUserSearchCriteria criteria, Pageable pageable) {
        KycStatus kycStatus = parseKycStatus(criteria.kycStatus());
        AccountStatus accountStatus = parseAccountStatus(criteria.accountStatus());
        String search = blankToNull(criteria.search());
        return userRepository.searchForAdmin(search, kycStatus, accountStatus, pageable)
                .map(this::toAdminUserView);
    }

    public Optional<AdminUserView> getAdminViewById(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .map(this::toAdminUserView);
    }

    private AdminUserView toAdminUserView(User u) {
        return new AdminUserView(
                u.getId(),
                u.getDisplayName(),
                u.getEmail(),
                u.getPhone(),
                u.getKycStatus().name(),
                u.getAccountStatus().name(),
                u.getSubscriptionTier().name(),
                u.getCreatedAt(),
                GhanaCardMasker.maskToLastFour(u.getGhanaCardNumber()));
    }

    private static KycStatus parseKycStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return KycStatus.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static AccountStatus parseAccountStatus(String s) {
        if (s == null || s.isBlank()) return null;
        try { return AccountStatus.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
