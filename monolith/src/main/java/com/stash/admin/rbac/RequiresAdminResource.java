package com.stash.admin.rbac;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply to every protected admin controller method. The only thing each
 * endpoint declares is which AdminResource it belongs to — the actual
 * SUPER/VICE_SUPER/TAB decision lives entirely in AdminRoleMapping.
 *
 * Example:
 *   @RequiresAdminResource(AdminResource.KYC)
 *   @GetMapping("/api/v1/admin/kyc/queue")
 *   public ... listQueue() { ... }
 *
 * NOTE: The {value} placeholder in the @PreAuthorize expression below
 * requires Spring Security 6.4+ (Spring Boot 3.4+) and the
 * AnnotationTemplateExpressionDefaults bean registered in SecurityConfig.
 * This project runs Spring Boot 3.3 (Spring Security 6.3), so the annotation
 * exists as a design contract but is not used yet. Endpoints currently use
 * the explicit form:
 *   @PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).KYC)")
 *
 * When the project upgrades to Spring Boot 3.4+, register:
 *   @Bean
 *   static AnnotationTemplateExpressionDefaults templateExpressionDefaults() {
 *       return new AnnotationTemplateExpressionDefaults();
 *   }
 * …and replace explicit @PreAuthorize calls on admin endpoints with
 * @RequiresAdminResource(AdminResource.X).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@PreAuthorize("@adminAccessEvaluator.check(#root, T(com.stash.admin.rbac.AdminResource).{value})")
public @interface RequiresAdminResource {
    AdminResource value();
}
