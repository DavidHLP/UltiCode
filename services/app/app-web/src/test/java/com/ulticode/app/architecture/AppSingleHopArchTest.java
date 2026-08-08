package com.ulticode.app.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * P4-RPC-002: single-hop RPC chain prevention for {@code backend-app}.
 *
 * <p>Per migration guide &sect;6.5, {@code backend-app} is a Provider that
 * the Admin BFF calls. It must not itself consume Admin's Dubbo contract,
 * which would form a forbidden two-hop chain
 * ({@code controller → dubbo App → dubbo Admin}). App <i>may</i> consume
 * Auth's Identity/Authorization services for cache-miss identity lookups
 * (per &sect;4.3 "Auth identity snapshot 仅用于 cache miss"), so Auth API
 * dependencies are <b>not</b> forbidden here &mdash; only Admin API is.
 *
 * <p>Two rules enforce this:
 * <ol>
 *   <li><b>Module dependency leak:</b> no App class may depend on
 *       {@code com.ulticode.admin..}.</li>
 *   <li><b>Provider-also-Consumer:</b> a {@code @DubboService} class
 *       must not declare a {@code @DubboReference} field.</li>
 * </ol>
 */
@AnalyzeClasses(
        packages = "com.ulticode",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class AppSingleHopArchTest {

    /**
     * No class in the app module may depend on Admin API packages.
     * <p>This is the &sect;6.5 rule: "禁止 App 依赖 admin API".
     */
    @ArchTest
    static final ArchRule APP_MUST_NOT_DEPEND_ON_ADMIN_API =
            noClasses()
                    .that().resideInAPackage("com.ulticode.app..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ulticode.admin..")
                    .because("§6.5: backend-app must not consume Admin's "
                            + "Dubbo contract, which would form a forbidden "
                            + "multi-hop chain. App may consume Auth API "
                            + "(for identity cache-miss), but never Admin.");

    /**
     * No class annotated with {@code @DubboService} may declare a field
     * annotated with {@code @DubboReference}.
     */
    @ArchTest
    static final ArchRule DUBBO_PROVIDER_MUST_NOT_ALSO_BE_CONSUMER =
            noClasses()
                    .that().areAnnotatedWith("org.apache.dubbo.config.annotation.DubboService")
                    .should().beAnnotatedWith("org.apache.dubbo.config.annotation.DubboReference")
                    .because("§6.5: a Dubbo Provider class must not also hold a "
                            + "@DubboReference, which would make it a Consumer "
                            + "and create a controller → dubbo A → dubbo B chain.");

    /**
     * P2-RBAC-001 (consumer-side guard): App classes must not import
     * Auth's internal role/permission admin surface. Callers must use
     * the published Dubbo contract {@code AccountAdministrationService}
     * in backend-auth-api. This complements the provider-side rule in
     * {@code AuthSingleHopArchTest}.
     */
    @ArchTest
    static final ArchRule APP_MUST_NOT_IMPORT_AUTH_RBAC_INTERNALS =
            noClasses()
                    .that().resideInAPackage("com.ulticode.app..")
                    .or().resideInAPackage("com.ulticode.modules..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ulticode.auth.adapter.in.web..",
                            "com.ulticode.auth.permission.mapper..",
                            "com.ulticode.auth.permission.adapter..",
                            "com.ulticode.auth.permission.service..")
                    .because("P2-RBAC-001: Auth's internal role/permission classes "
                            + "are not part of the published API. Use "
                            + "AccountAdministrationService via Dubbo RPC.");
}
