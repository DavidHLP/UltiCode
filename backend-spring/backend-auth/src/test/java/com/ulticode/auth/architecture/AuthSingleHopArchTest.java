package com.ulticode.auth.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * P4-RPC-002: single-hop RPC chain prevention for {@code backend-auth}.
 *
 * <p>Per migration guide &sect;6.5, {@code backend-auth} is a leaf Provider:
 * it exposes Dubbo services but must never act as a Consumer of another
 * service's Dubbo contract. This guard catches two anti-patterns at
 * compile time:
 * <ol>
 *   <li><b>Module dependency leak:</b> any class in the auth module
 *       that depends on {@code com.ulticode.app.api..} or
 *       {@code com.ulticode.admin..} would let an Auth Provider call
 *       an App/Admin Dubbo reference &mdash; forming a two-hop chain.</li>
 *   <li><b>Provider-also-Consumer:</b> a class annotated with
 *       {@code @DubboService} that also declares a
 *       {@code @DubboReference} field &mdash; the most direct way to
 *       create a forbidden chain inside one class.</li>
 * </ol>
 *
 * <p>These rules are compile-time guards; the runtime hop-count detection
 * (trace-based alerting per &sect;6.5 "同步服务跳数 &gt; 1 告警") is a
 * monitoring concern, not an ArchUnit test.
 */
@AnalyzeClasses(
        packages = "com.ulticode",
        importOptions = {ImportOption.DoNotIncludeTests.class})
public class AuthSingleHopArchTest {

    /**
     * No class in the auth module may depend on App or Admin API packages.
     * <p>This is the &sect;6.5 rule: "禁止 backend-auth 依赖 app/admin API".
     */
    @ArchTest
    static final ArchRule AUTH_MUST_NOT_DEPEND_ON_APP_OR_ADMIN_API =
            noClasses()
                    .that().resideInAPackage("com.ulticode.auth..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.ulticode.app.api..",
                            "com.ulticode.app.dubbo..",
                            "com.ulticode.admin..")
                    .because("§6.5: backend-auth is a leaf Dubbo Provider; "
                            + "it must not consume App or Admin contracts, "
                            + "which would form a forbidden multi-hop chain.");

    /**
     * No class annotated with {@code @DubboService} may declare a field
     * annotated with {@code @DubboReference}. A Provider that is also a
     * Consumer is the most direct single-hop violation.
     */
    @ArchTest
    static final ArchRule DUBBO_PROVIDER_MUST_NOT_ALSO_BE_CONSUMER =
            noClasses()
                    .that().areAnnotatedWith("org.apache.dubbo.config.annotation.DubboService")
                    .should().beAnnotatedWith("org.apache.dubbo.config.annotation.DubboReference")
                    .because("§6.5: a Dubbo Provider class must not also hold a "
                            + "@DubboReference, which would make it a Consumer "
                            + "and create a controller → dubbo A → dubbo B chain.");
}
