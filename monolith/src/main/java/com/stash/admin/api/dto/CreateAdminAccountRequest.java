package com.stash.admin.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAdminAccountRequest(
        @NotBlank @Email String email,

        @NotBlank
        @Size(min = 8, message = "Initial password must be at least 8 characters")
        String password,        // set directly by the creating admin (SUPER/VICE_SUPER) and
                                 // relayed out-of-band — no email/invite-token flow

        @NotBlank String fullName,

        @NotBlank String accountType,  // SUPER | VICE_SUPER | TAB — legality vs. the
                                        // caller's own type is checked by
                                        // AdminAccountCreationService, not @Pattern here
        String roleName                // required only when accountType=TAB; must equal
                                        // an AdminResource name
) {}
