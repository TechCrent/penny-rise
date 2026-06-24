package com.stash.kyc.config;

import com.stash.kyc.shared.security.PlaceholderAdminAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaceholderSecurityConfig {

    @Bean
    public FilterRegistrationBean<PlaceholderAdminAuthFilter> adminAuthFilterRegistration(
            PlaceholderAdminAuthFilter filter) {
        FilterRegistrationBean<PlaceholderAdminAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/v1/kyc/admin/*");
        return registration;
    }
}
