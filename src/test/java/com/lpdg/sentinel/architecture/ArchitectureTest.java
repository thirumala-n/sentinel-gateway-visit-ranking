package com.lpdg.sentinel.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture tests enforcing layer boundaries in the Sentinel codebase.
 *
 * <p>These rules ensure the hexagonal/clean architecture is maintained:
 * <ul>
 *   <li>Domain must not depend on web, infrastructure, or Spring.</li>
 *   <li>Application must not depend on web or infrastructure.</li>
 * </ul>
 *
 * <p>{@code allowEmptyShould(true)} is used during Phase 1 because the
 * domain and application packages do not yet contain classes. Once classes
 * are added in Phase 2+, these rules will actively enforce boundaries.</p>
 */
@AnalyzeClasses(
        packages = "com.lpdg.sentinel",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ── Domain isolation ─────────────────────────────────────

    @ArchTest
    static final ArchRule domain_must_not_depend_on_web =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domain_must_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .allowEmptyShould(true);

    // ── Application layer isolation ──────────────────────────

    @ArchTest
    static final ArchRule application_must_not_depend_on_web =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..web..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .allowEmptyShould(true);
}
