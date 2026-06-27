package com.stash.payments.paystack.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces that only PaystackClient issues HTTP calls to Paystack.
 * Any class outside com.stash.payments.paystack.client that uses WebClient
 * directly against Paystack's domain will fail this test.
 */
class PaystackClientArchitectureTest {

    private static final JavaClasses ALL_CLASSES =
            new ClassFileImporter().importPackages("com.stash.payments");

    @Test
    void only_paystack_client_may_use_webClient_for_paystack_calls() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.stash.payments.paystack.client..")
                .should().accessClassesThat()
                .haveSimpleName("WebClient")
                .because("All Paystack HTTP calls must go through PaystackClient. " +
                         "No other class may use WebClient directly.");

        // Note: this rule is intentionally broad — WebClient usage for non-Paystack
        // purposes (internal HTTP calls) would need a more targeted rule.
        // For v0.3 the Payments Service has no other WebClient users.
        rule.check(ALL_CLASSES);
    }
}
