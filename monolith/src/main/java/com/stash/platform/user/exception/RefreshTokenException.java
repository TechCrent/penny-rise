package com.stash.platform.user.exception;

import com.stash.shared.apierrors.ErrorCode;
import com.stash.shared.apierrors.StashApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a refresh token is invalid, expired, revoked, or is a replay.
 */
public class RefreshTokenException extends StashApiException {

    public RefreshTokenException(String message) {
        super(ErrorCode.AUTH_TOKEN_INVALID, message, HttpStatus.UNAUTHORIZED);
    }
}