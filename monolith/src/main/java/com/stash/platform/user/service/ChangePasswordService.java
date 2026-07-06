package com.stash.platform.user.service;

import com.stash.platform.user.api.dto.ChangePasswordRequest;
import com.stash.platform.user.domain.User;
import com.stash.platform.user.repository.UserRepository;
import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Authenticated "change my password" flow — distinct from the token-based
 * forgot/reset-password pair, which is for a user who's locked out. This
 * requires the current password, same as any standard account-settings
 * change-password form.
 */
@Service
public class ChangePasswordService {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordService.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public ChangePasswordService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new StashApiException(
                        ErrorCode.NOT_FOUND, "User not found.", HttpStatus.NOT_FOUND));

        if (!passwordHasher.verify(request.currentPassword(), user.getPasswordHash())) {
            log.debug("Change-password rejected: current password mismatch userId={}", userId);
            throw new StashApiException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Current password is incorrect.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        user.setPasswordHash(passwordHasher.hash(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        log.info("Password changed userId={}", userId);
    }
}
