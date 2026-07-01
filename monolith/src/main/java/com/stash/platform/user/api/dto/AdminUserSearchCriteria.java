package com.stash.platform.user.api.dto;

/**
 * Search/filter parameters for the admin user list endpoint.
 * All fields are optional; null means "no filter applied".
 */
public record AdminUserSearchCriteria(
        String search,
        String kycStatus,
        String accountStatus
) {}
