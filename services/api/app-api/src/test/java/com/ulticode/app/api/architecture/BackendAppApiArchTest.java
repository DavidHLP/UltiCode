package com.ulticode.app.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural gate for {@code backend-app-api} (P1-API-001).
 *
 * <p>Mirrors {@code BackendCommonArchTest} and adds the contract
 * module's own constraints: no Entity / Mapper / ServiceImpl /
 * Repository, no MyBatis annotation surface, no Spring bean
 * scanning, no Spring Security context, and no transitive dependency
 * on any implementation module. The allowlist is used in place of a
 * blanket forbid so the rule does not collide with the module's own
 * package. The {@code backend-submission-api} dependency and its
 * allowlist entry were removed after the unused Submission result
 * payload seam was relocated (P3-CONTRACT-006).
 */
@AnalyzeClasses(
        packages = "com.ulticode.app.api",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class BackendAppApiArchTest {

    private static final String APP_API_PKG = "com.ulticode.app.api..";

    /* ===== dependency rules ============================================ */

    @ArchTest
    static final ArchRule APP_API_MUST_NOT_DEPEND_ON_MYBATIS =
            noClasses()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.baomidou..",
                            "org.apache.ibatis..",
                            "org.mybatis..")
                    .because("backend-app-api is a contract module; "
                            + "MyBatis belongs to the implementation.");

    @ArchTest
    static final ArchRule APP_API_MUST_NOT_DEPEND_ON_SPRING_BEANS =
            noClasses()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.stereotype..",
                            "org.springframework.context.annotation..",
                            "org.springframework.beans.factory.annotation..")
                    .because("Spring bean scanning (@Component, @Service, "
                            + "@Configuration, @Bean) belongs to the "
                            + "implementation module, not the contract.");

    @ArchTest
    static final ArchRule APP_API_MUST_NOT_DEPEND_ON_SPRING_SECURITY =
            noClasses()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.security..")
                    .because("Spring Security context must stay inside "
                            + "backend-app implementation; the contract "
                            + "only exposes pure Java interfaces.");

    /* ===== class-naming rules ========================================== */

    @ArchTest
    static final ArchRule APP_API_MUST_NOT_DEFINE_ENTITY_CLASSES =
            noClasses()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().haveSimpleNameEndingWith("Entity")
                    .because("backend-app-api holds no persistent "
                            + "entities; only DTOs / commands / enums.");

    @ArchTest
    static final ArchRule APP_API_MUST_NOT_DEFINE_MAPPER_CLASSES =
            noClasses()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().haveSimpleNameEndingWith("Mapper")
                    .because("backend-app-api holds no MyBatis mappers.");

    @ArchTest
    static final ArchRule APP_API_MUST_NOT_DEFINE_SERVICE_IMPL_CLASSES =
            noClasses()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().haveSimpleNameEndingWith("ServiceImpl")
                    .because("backend-app-api holds no business service "
                            + "implementations; only contract interfaces.");

    @ArchTest
    static final ArchRule APP_API_MUST_NOT_DEFINE_REPOSITORY_CLASSES =
            noClasses()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().haveSimpleNameEndingWith("Repository")
                    .because("backend-app-api does not own persistence "
                            + "abstractions.");

    /* ===== implementation-leakage allowlist =========================== */

    /**
     * The contract module may only depend on its own package, the
     * shared {@code backend-common} contract, the Submission-owned
     * result payload used by the App push seam, and JDK / standard
     * library packages. Any transitively-pulled dependency on an
     * Entity / Mapper / ServiceImpl / Repository / controller
     * package under {@code com.ulticode.*} (including
     * {@code com.ulticode.app.entity..},
     * {@code com.ulticode.app.mapper..},
     * {@code com.ulticode.modules..}, {@code com.ulticode.security..},
     * {@code com.ulticode.auth..},
     * {@code com.ulticode.admin..},
     * {@code com.ulticode.legacy..}) is a violation.
     *
     * <p>An allowlist (rather than a blanket forbid) is used so the
     * rule does not collide with the module's own package
     * {@code com.ulticode.app.api..}.
     */
    @ArchTest
    static final ArchRule APP_API_ONLY_DEPENDS_ON_CONTRACT_AND_COMMON =
            classes()
                    .that().resideInAPackage(APP_API_PKG)
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "com.ulticode.app.api..",
                            "com.ulticode.common..",
                            "com.ulticode.domain..",
                            "java..",
                            "javax..",
                            "jakarta..",
                            "lombok..",
                            "com.fasterxml.jackson.annotation..",
                            "io.swagger.v3.oas.annotations..")
                    .because("contract module may only depend on its "
                            + "own package, backend-common, and standard "
                            + "annotation libraries (Lombok compile-time "
                            + "codegen, Jackson serialization annotations, "
                            + "Swagger OpenAPI documentation); no "
                            + "impl/Entity/legacy leakage.");
}
