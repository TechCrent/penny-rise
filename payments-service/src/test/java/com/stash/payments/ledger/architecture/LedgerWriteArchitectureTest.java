package com.stash.payments.ledger.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces that only LedgerService writes to the ledger repositories.
 * Any production class outside com.stash.payments.ledger.service that calls
 * LedgerTransactionRepository or LedgerEntryRepository will fail this test.
 *
 * Test classes are excluded — they mock the repos directly by design.
 */
class LedgerWriteArchitectureTest {

    // Exclude test classes: test mocks import the repos by design and are
    // not production code that could bypass LedgerService in the live system.
    private static final JavaClasses ALL_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.stash.payments");

    @Test
    void only_ledger_service_may_access_ledger_transaction_repository() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.stash.payments.ledger.service..")
                .should().accessClassesThat()
                .haveFullyQualifiedName(
                        "com.stash.payments.ledger.repository.LedgerTransactionRepository")
                .because("LedgerTransactionRepository is package-private and may only be " +
                         "accessed from com.stash.payments.ledger.service. " +
                         "All ledger writes must go through LedgerService.writeTransaction().");

        rule.check(ALL_CLASSES);
    }

    @Test
    void only_ledger_service_may_access_ledger_entry_repository() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.stash.payments.ledger.service..")
                .should().accessClassesThat()
                .haveFullyQualifiedName(
                        "com.stash.payments.ledger.repository.LedgerEntryRepository")
                .because("LedgerEntryRepository is package-private and may only be " +
                         "accessed from com.stash.payments.ledger.service. " +
                         "All ledger writes must go through LedgerService.writeTransaction().");

        rule.check(ALL_CLASSES);
    }
}
