package com.stash.platform.user.service;

import com.stash.platform.user.api.dto.UpdateUserProfileRequest;
import com.stash.platform.user.api.dto.UserProfileResponse;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles the authenticated user's own profile read and update.
 *
 * <p>Field exposure contract: {@link UserProfileResponse} is a hand-curated
 * projection of {@link User}. It intentionally omits password_hash and
 * ghana_card_number — there is no risk of accidentally leaking them because
 * the response DTO simply has no field for them, regardless of what's on
 * the entity.
 *
 * <p>Field write contract: {@link UpdateUserProfileRequest} only has fields
 * for display_name and phone. There is no code path by which kyc_status,
 * account_status, or subscription_tier could be set through this service —
 * the request DTO structurally cannot carry those values.
 *
 * <p>Phone normalisation: local format (0501234567) is converted to E.164
 * (+233501234567) before storage, matching the format stored at signup
 * (none — phone starts NULL) and matching the unique-index column type.
 *
 * <p>Per System Design v0.2 scope: phone is "set once and frozen" — once
 * a non-null phone is set, subsequent PATCH calls with a phone value are
 * rejected. This is a deliberate v0.2 limitation; phone change with
 * re-verification ships in a later milestone.
 */
@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the authenticated user's profile.
     *
     * @param userId the authenticated user's ID from the JWT
     * @return the profile projection — never includes password_hash or ghana_card_number
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.NOT_FOUND, "User not found.", HttpStatus.NOT_FOUND));

        return toResponse(user);
    }

    /**
     * Updates the authenticated user's display_name and/or phone.
     *
     * <p>Any other fields the client may have submitted (kyc_status,
     * account_status, subscription_tier, etc.) are structurally impossible
     * to reach this method — {@link UpdateUserProfileRequest} has no fields
     * for them. No explicit "silently ignore" logic is needed; the type
     * system enforces it.
     *
     * @param userId  the authenticated user's ID from the JWT
     * @param request fields to update — null fields are left unchanged
     * @return the updated profile projection
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest request) {
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.NOT_FOUND, "User not found.", HttpStatus.NOT_FOUND));

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().strip());
        }

        if (request.phone() != null && !request.phone().isBlank()) {
            if (user.getPhone() != null) {
                // v0.2 scope: phone is set once and frozen.
                log.debug("Phone update rejected: already set, frozen for v0.2 userId={}", userId);
                throw new StashApiException(
                        ErrorCode.CONFLICT,
                        "Phone number is already set and cannot be changed at this time.",
                        HttpStatus.CONFLICT
                );
            }
            user.setPhone(normaliseGhanaPhone(request.phone()));
        }

        userRepository.save(user);

        log.info("Profile updated userId={}", userId);
        return toResponse(user);
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPhone(),
                user.getKycStatus().name(),
                user.getSubscriptionTier().name(),
                user.getAccountStatus().name(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt()
        );
    }

    /**
     * Normalises a Ghanaian phone number to E.164 format.
     * "0501234567" -> "+233501234567"
     * "+233501234567" -> unchanged
     */
    private String normaliseGhanaPhone(String phone) {
        String trimmed = phone.strip();
        if (trimmed.startsWith("+233")) {
            return trimmed;
        }
        if (trimmed.startsWith("0")) {
            return "+233" + trimmed.substring(1);
        }
        return trimmed; // unreachable given the @Pattern validation, but defensive
    }
}
