package com.fsp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SPEC-001")
@Tag("M0")
class ArchitectureTests {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fsp");
    }

    @Test
    void domainPackagesMustNotDependOnSpring() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .allowEmptyShould(true)
                .because("AGENTS.md #5 & SPEC-001 §7.2: domain packages must remain pure Java without Spring dependencies")
                .check(importedClasses);
    }

    @Test
    void domainAndApplicationMustNotDependOnAdapters() {
        noClasses()
                .that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAPackage("..adapter..")
                .allowEmptyShould(true)
                .because("Hexagonal architecture enforces inward dependency direction: adapter -> application -> domain")
                .check(importedClasses);
    }

    @Test
    void aggregateRootsMustResideInDomainPackages() {
        classes()
                .that().areAnnotatedWith(AggregateRoot.class)
                .should().resideInAPackage("..domain..")
                .allowEmptyShould(true)
                .because("Aggregate roots belong strictly in domain packages")
                .check(importedClasses);
    }
}

