package com.ulticode.common.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architectural gate for {@code backend-common} (P1-INFRA-002).
 *
 * <p>The shared contract module is intentionally dependency-free; if a
 * dependency on business semantics creeps in (MyBatis, JPA, Spring bean
 * scanning, business {@code Entity}/{@code Mapper}/{@code ServiceImpl}),
 * this test fails immediately, preventing the {@code backend-*-api} modules
 * from being polluted on their next pass.
 *
 * <p>The rules below mirror the contract spelled out in
 * {@code docs/architecture/modules.md} and the task spec:
 * <ul>
 *   <li>no MyBatis (no {@code com.baomidou..}, {@code org.apache.ibatis..},
 *       {@code org.mybatis..});</li>
 *   <li>no Spring beans ({@code org.springframework.stereotype..},
 *       {@code org.springframework.context.annotation..},
 *       {@code org.springframework.beans.factory.annotation..});</li>
 *   <li>no business implementation classes (anything named
 *       {@code *Entity}, {@code *Mapper}, {@code *ServiceImpl},
 *       {@code *Repository});</li>
 *   <li>no internal modules leaking in ({@code com.ulticode.modules..},
 *       {@code com.ulticode.security..}).</li>
 * </ul>
 *
 * <p>The analysis scope is the module's own production sources
 * ({@code com.ulticode.common..}); ArchUnit's {@code DoNotIncludeTests}
 * import option keeps test sources out of the production-side rule
 * evaluation so test fixtures can legitimately violate naming patterns.
 */
@AnalyzeClasses(
        packages = "com.ulticode.common",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class BackendCommonArchTest {

    private static final String COMMON_PKG = "com.ulticode.common..";

    /* ===== dependency rules ============================================ */

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEPEND_ON_MYBATIS =
            noClasses()
                    .that().resideInAPackage(COMMON_PKG)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.baomidou..",
                            "org.apache.ibatis..",
                            "org.mybatis..")
                    .because("backend-common must stay free of MyBatis/MyBatis-Plus "
                            + "so it can be reused by every Dubbo contract module.");

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEPEND_ON_SPRING_BEAN_ANNOTATIONS =
            noClasses()
                    .that().resideInAPackage(COMMON_PKG)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework.stereotype..",
                            "org.springframework.context.annotation..",
                            "org.springframework.beans.factory.annotation..")
                    .because("backend-common must stay free of Spring bean "
                            + "annotations (@Component, @Service, @Configuration, "
                            + "@Bean, @ComponentScan). Spring wiring belongs to "
                            + "the implementation modules, not the contract.");

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEPEND_ON_INTERNAL_MODULES =
            noClasses()
                    .that().resideInAPackage(COMMON_PKG)
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ulticode.modules..",
                            "com.ulticode.security..")
                    .because("backend-common must not depend on internal modules. "
                            + "It is the leaf contract shared by every service.");

    /* ===== class-naming rules ========================================== */

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEFINE_ENTITY_CLASSES =
            noClasses()
                    .that().resideInAPackage(COMMON_PKG)
                    .should().haveSimpleNameEndingWith("Entity")
                    .because("backend-common is the contract layer; persistent "
                            + "entities live with the implementation modules.");

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEFINE_MAPPER_CLASSES =
            noClasses()
                    .that().resideInAPackage(COMMON_PKG)
                    .should().haveSimpleNameEndingWith("Mapper")
                    .because("backend-common holds no MyBatis mappers.");

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEFINE_SERVICE_IMPL_CLASSES =
            noClasses()
                    .that().resideInAPackage(COMMON_PKG)
                    .should().haveSimpleNameEndingWith("ServiceImpl")
                    .because("backend-common holds no business service "
                            + "implementations; only contracts and DTOs.");

    @ArchTest
    static final ArchRule COMMON_MUST_NOT_DEFINE_REPOSITORY_CLASSES =
            noClasses()
                    .that().resideInAPackage(COMMON_PKG)
                    .should().haveSimpleNameEndingWith("Repository")
                    .because("backend-common does not own persistence "
                            + "abstractions.");

    /* ===== presence rules ============================================== */

    /** The HTTP {@code Result} envelope must be public so consumers can deserialize it. */
    @ArchTest
    static final ArchRule RESULT_ENVELOPE_IS_PUBLIC =
            classes()
                    .that().resideInAPackage(COMMON_PKG)
                    .and().haveSimpleName("Result")
                    .should().bePublic();

    /** The RPC envelope must be public so consumers can deserialize it. */
    @ArchTest
    static final ArchRule RPC_ENVELOPE_IS_PUBLIC =
            classes()
                    .that().resideInAPackage(COMMON_PKG)
                    .and().haveSimpleName("RpcResult")
                    .should().bePublic();

    /* ===== sanity net ================================================== */

    /**
     * Final safety net: fail fast when the migration lost a moved class.
     * If any of the moved atoms or newly-introduced contract types is
     * missing, this test breaks the build before the ArchUnit rules
     * below it get a chance to lie.
     */
    @Test
    void moved_and_new_contract_types_are_present() {
        Set<String> required = Set.of(
                "com.ulticode.common.response.Result",
                "com.ulticode.common.response.PageResult",
                "com.ulticode.common.response.PaginationRequest",
                "com.ulticode.common.util.TraceIdUtil",
                "com.ulticode.common.time.TimeSource",
                "com.ulticode.common.time.TimeSourceHolder",
                "com.ulticode.common.time.FakeTimeSource",
                "com.ulticode.common.rpc.RpcResult",
                "com.ulticode.common.error.NamespacedErrorCode",
                "com.ulticode.common.error.BaseErrorCode",
                "com.ulticode.common.tracing.TraceMetadata",
                "com.ulticode.common.tracing.IdMetadata",
                "com.ulticode.common.command.ActorDelegation",
                "com.ulticode.common.command.WriteCommand",
                "com.ulticode.common.dto.DifficultyCountDTO",
                "com.ulticode.common.auth.AccountInfo",
                "com.ulticode.common.auth.JwtPayload",
                "com.ulticode.common.security.AccountReadPort",
                "com.ulticode.common.security.JwtValidationPort",
                "com.ulticode.common.security.DelegationAssertionContract");
        Set<String> missing = new HashSet<>();
        ClassLoader cl = getClass().getClassLoader();
        for (String fqcn : required) {
            try {
                Class.forName(fqcn, false, cl);
            } catch (ClassNotFoundException e) {
                missing.add(fqcn);
            }
        }
        assertThat(missing)
                .as("backend-common must keep every moved atom and new contract type")
                .isEmpty();
    }
}
