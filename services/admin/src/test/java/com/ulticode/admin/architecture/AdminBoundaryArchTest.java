package com.ulticode.admin.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Admin-shell architecture rules.
 *
 * <p>Replaces the legacy {@code OwnerBoundaryArchTest} rules that were mooted
 * by the physical relocation of every business family to backend-app. The
 * legacy rules protected package boundaries that no longer exist in the
 * admin module (contest/submission/queue/user/permission packages moved to
 * app or were deleted).
 *
 * <p>This rule set enforces the post-legacy invariant: no admin class may
 * depend on the concrete legacy-only types that were deleted with the module.
 * The deleted types lived in {@code com.ulticode.modules.user.entity},
 * {@code com.ulticode.modules.user.mapper}, {@code com.ulticode.modules.user.port},
 * {@code com.ulticode.modules.auth.account}, and
 * {@code com.ulticode.modules.admin.client}. Common types like
 * {@code BusinessException} that were promoted to backend-common are NOT
 * in scope.
 */
@AnalyzeClasses(
    packages = "com.ulticode",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
class AdminBoundaryArchTest {

    /**
     * No admin class may depend on concrete legacy-only types.
     *
     * <p>These packages contained classes that existed only in backend-legacy
     * (User, UserMapper, UserProfilePort, AuthAccountPort,
     * BackendAuthRoleAdminClient, etc.). They are now physically deleted;
     * this rule prevents any future change from re-introducing a dependency
     * on them via copy-paste or stale imports.
     */
    @ArchTest
    static final ArchRule admin_must_not_depend_on_deleted_legacy_types =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.ulticode.admin..")
            .or().resideInAPackage("com.ulticode.modules.admin..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.ulticode.modules.user.entity..",
                "com.ulticode.modules.user.mapper..",
                "com.ulticode.modules.user.port..",
                "com.ulticode.modules.auth.account..",
                "com.ulticode.modules.admin.client..")
            .because("backend-legacy is deleted; admin must use backend-auth-api "
                + "Dubbo contracts and backend-app types exclusively. "
                + "Common types (BusinessException, BaseErrorCode) in "
                + "com.ulticode.common.exception are NOT affected.");
    /**
     * P2-RBAC-001 (consumer-side guard): Admin classes must not import
     * Auth's internal role/permission admin surface. Callers must use
     * the published Dubbo contract {@code AccountAdministrationService}
     * in backend-auth-api. This complements the provider-side rule in
     * {@code AuthSingleHopArchTest}.
     */
    @ArchTest
    static final ArchRule admin_must_not_import_auth_rbac_internals =
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.ulticode.admin..")
            .or().resideInAPackage("com.ulticode.modules.admin..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.ulticode.auth.adapter.in.web..",
                "com.ulticode.auth.permission.mapper..",
                "com.ulticode.auth.permission.adapter..",
                "com.ulticode.auth.permission.service..")
            .because("P2-RBAC-001: Auth's internal role/permission classes "
                + "are not part of the published API. Use "
                + "AccountAdministrationService via Dubbo RPC.");

}
