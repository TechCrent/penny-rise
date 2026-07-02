package com.stash.audit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class AuditAdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final AdminJwtVerifier verifier;

    public AuditAdminJwtAuthenticationFilter(AdminJwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            var claims      = verifier.parseAndValidate(header.substring(7));
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.accountType()));
            var auth        = new UsernamePasswordAuthenticationToken(claims.adminAccountId(), null, authorities);
            auth.setDetails(claims);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
