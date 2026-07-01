package com.stash.admin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateAdminAccountRequest(
        @NotBlank @Email String email,
        @NotBlank String accountType,  // SUPER | VICE_SUPER | TAB — legality vs. the
                                        // caller's own type is checked by
                                        // AdminAccountCreationService, not @Pattern here
        String roleName                // required only when accountType=TAB; must equal
                                        // an AdminResource name
) {}
