package com.stash.platform.user.security;

import com.stash.platform.user.service.AccessTokenClaims;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security {@link org.springframework.security.core.Authentication}
 * implementation backed by validated JWT claims.
 *
 * <p>Populated by {@link JwtAuthenticationFilter} after successful token
 * verification. Available in any controller/service via
 * {@code SecurityContextHolder.getContext().getAuthentication()}.
 */
public class AuthenticatedUser extends AbstractAuthenticationToken {

    private final UUID userId;
    private final AccessTokenClaims claims;

    public AuthenticatedUser(AccessTokenClaims claims) {
        super(authorities());
        this.userId = claims.userId();
        this.claims = claims;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public UUID getUserId() {
        return userId;
    }

    public AccessTokenClaims getClaims() {
        return claims;
    }

    @Override
    public Object getCredentials() {
        return null; // No credentials retained after authentication
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }
}