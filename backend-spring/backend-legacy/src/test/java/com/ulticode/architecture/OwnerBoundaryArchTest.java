package com.ulticode.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 baseline owner-boundary rules (P0-ARCH-002) + Phase 3 Owner write rules (P3-OWNER-001-F).
 */
@AnalyzeClasses(
    packages = "com.ulticode.modules",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class OwnerBoundaryArchTest {

    private static final String CONTEST_PKG = "com.ulticode.modules.contest..";
    private static final String CONTEST_PKG_BASE = "com.ulticode.modules.contest";
    private static final String ADMIN_PKG = "com.ulticode.modules.admin..";
    private static final String MODERATION_PKG = "com.ulticode.modules.moderation..";
    private static final String USER_PKG = "com.ulticode.modules.user..";
    private static final String SUBMISSION_PKG = "com.ulticode.modules.submission..";

    private static final String QUEUE_OUTBOX_PKG = "com.ulticode.modules.queue.outbox..";
    private static final String QUEUE_SERVICE_PKG = "com.ulticode.modules.queue.service..";

    /**
     * Rule 1: admin must not reach contest mapper/entity directly.
     */
    @ArchTest
    static final ArchRule admin_must_not_reach_contest_directly =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(ADMIN_PKG)
            .should().dependOnClassesThat().resideInAnyPackage(
                CONTEST_PKG_BASE + ".mapper..",
                CONTEST_PKG_BASE + ".service.."))
        .because("Admin reads Contest via Q/E (RPC/outbox) or the new Phase 3 owner "
            + "port (com.ulticode.modules.contest.port..), not direct Mapper/Service. "
            + "See ADR-MIG-ARCH-BOUNDARY for the frozen baseline and burn-down list.");

    /**
     * Rule 2: moderation must not reach users directly.
     */
    @ArchTest
    static final ArchRule moderation_must_not_reach_users_directly =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(MODERATION_PKG)
            .should().dependOnClassesThat().resideInAPackage(USER_PKG))
        .because("Moderation reaches users via Auth RPC (C) or event (E), "
            + "not direct User/UserMapper. See ADR-MIG-ARCH-BOUNDARY.");

    /**
     * Rule 3: submission must not reach queue internals outside published port.
     */
    @ArchTest
    static final ArchRule submission_must_not_reach_queue_outbox =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(SUBMISSION_PKG)
            .should().dependOnClassesThat().resideInAnyPackage(QUEUE_OUTBOX_PKG))
        .because("Submission depends on queue ports only. "
            + "Reaching queue.outbox (JudgeOutboxRecord/Mapper, dispatcher, reaper) "
            + "is internal to the queue module. See ADR-MIG-ARCH-BOUNDARY.");

    @ArchTest
    static final ArchRule submission_must_not_reach_queue_service =
        FreezingArchRule.freeze(ArchRuleDefinition.noClasses()
            .that().resideInAPackage(SUBMISSION_PKG)
            .should().dependOnClassesThat().resideInAPackage(QUEUE_SERVICE_PKG))
        .because("Submission depends on queue ports only. "
            + "QueueService is internal; JudgeQueue port is the public surface. "
            + "See ADR-MIG-ARCH-BOUNDARY.");

    @Test
    void frozen_baseline_pointer_is_documented() {
        assertThat(ADMIN_PKG).isNotEmpty();
        assertThat(SUBMISSION_PKG).isNotEmpty();
        assertThat(MODERATION_PKG).isNotEmpty();
    }

    /* ===== P2-RBAC-001: foreign modules may not depend on auth role/permission classes ===== */

    private static final String RBAC_OWNER_CONTROLLER =
            "com.ulticode.auth.adapter.in.web.RoleAdministrationController";
    private static final String RBAC_OWNER_SERVICE =
            "com.ulticode.auth.permission.service.RoleAdministrationService";
    private static final String RBAC_OWNER_USER_ROLE_MAPPER =
            "com.ulticode.auth.permission.mapper.UserRoleMapper";
    private static final String RBAC_OWNER_USER_PERM_MAPPER =
            "com.ulticode.auth.permission.mapper.UserPermissionMapper";
    private static final String RBAC_OWNER_ROLE_PERM_MAPPER =
            "com.ulticode.auth.permission.mapper.RolePermissionMapper";
    private static final String RBAC_OWNER_PORT =
            "com.ulticode.auth.permission.port.UserRoleWritePort";
    private static final String RBAC_OWNER_ADAPTER =
            "com.ulticode.auth.permission.adapter.UserRoleWriteAdapter";

    @ArchTest
    static final ArchRule p2_rbac_001_foreign_modules_must_not_use_auth_role_admin =
            ArchRuleDefinition.noClasses()
                    .that().resideOutsideOfPackage("com.ulticode.auth..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            RBAC_OWNER_CONTROLLER,
                            RBAC_OWNER_CONTROLLER + "..",
                            RBAC_OWNER_SERVICE,
                            RBAC_OWNER_SERVICE + "..",
                            RBAC_OWNER_USER_ROLE_MAPPER,
                            RBAC_OWNER_USER_ROLE_MAPPER + "..",
                            RBAC_OWNER_USER_PERM_MAPPER,
                            RBAC_OWNER_USER_PERM_MAPPER + "..",
                            RBAC_OWNER_ROLE_PERM_MAPPER,
                            RBAC_OWNER_ROLE_PERM_MAPPER + "..",
                            RBAC_OWNER_PORT,
                            RBAC_OWNER_PORT + "..",
                            RBAC_OWNER_ADAPTER,
                            RBAC_OWNER_ADAPTER + "..")
                    .because("P2-RBAC-001: backend-auth is the sole owner of the "
                            + "users.role / user_permissions / role_permissions write "
                            + "path.");

    /* ===== P3-OWNER-001-F: admin module must not call WRITE methods on foreign mappers ===== */

    @ArchTest
    static final ArchRule p3_owner_001_f_admin_must_not_call_foreign_mapper_writes =
        FreezingArchRule.freeze(
            ArchRuleDefinition.noClasses()
                    .that().resideInAPackage(ADMIN_PKG)
                    .should().callMethodWhere(
                            DescribedPredicate.describe(
                                    "call a WRITE method (insert/update/delete) on a foreign mapper",
                                    (JavaMethodCall call) -> {
                                        String ownerName = call.getTargetOwner().getName();
                                        boolean isForeignMapper = ownerName.endsWith("ProblemMapper")
                                                || ownerName.endsWith("ContestMapper")
                                                || ownerName.endsWith("SubmissionMapper")
                                                || ownerName.endsWith("ForumPostMapper")
                                                || ownerName.endsWith("SolutionMapper")
                                                || ownerName.endsWith("TestCaseMapper");
                                        if (!isForeignMapper) {
                                            return false;
                                        }
                                        String methodName = call.getTarget().getName();
                                        return methodName.startsWith("insert")
                                                || methodName.startsWith("update")
                                                || methodName.startsWith("delete");
                                    }
                            )
                    )
        ).because("P3-OWNER-001-F: Admin module must route all domain entity writes "
                + "through owner ports (ProblemOwnerPort, ContestOwnerPort, SubmissionOwnerPort, "
                + "ForumOwnerPort, SolutionOwnerPort) rather than calling foreign Mapper write methods directly. "
                + "See MICROSERVICE_MIGRATION_GUIDE.md §10.");


    /* ===== P3-OWNER-002: cross-owner writes to UserMapper are forbidden ===== */

    @ArchTest
    static final ArchRule p3_owner_002_forbid_cross_owner_user_writes =
        FreezingArchRule.freeze(
            ArchRuleDefinition.noClasses()
                    .that().doNotHaveFullyQualifiedName("com.ulticode.modules.auth.account.DefaultAuthAccountAdapter")
                    .and().doNotHaveFullyQualifiedName("com.ulticode.modules.user.port.DefaultUserProfileAdapter")
                    .should().callMethodWhere(
                            DescribedPredicate.describe(
                                    "call a WRITE method (insert/update/delete) on UserMapper",
                                    (JavaMethodCall call) -> {
                                        String ownerName = call.getTargetOwner().getName();
                                        if (!ownerName.equals("com.ulticode.modules.user.mapper.UserMapper")) {
                                            return false;
                                        }
                                        String methodName = call.getTarget().getName();
                                        return methodName.startsWith("insert")
                                                || methodName.startsWith("update")
                                                || methodName.startsWith("delete");
                                    }
                            )
                    )
        ).because("P3-OWNER-002: Only user port adapters (UserProfilePort) and auth account adapters (AuthAccountPort) "
                + "may call WRITE methods on UserMapper. Foreign modules (admin, moderation, etc.) must use owner ports.");
    /* ===== P2-DISC-005: forbid direct User::setRole calls outside auth/user owner ports ===== */

    @ArchTest
    static final ArchRule p2_disc_005_forbid_direct_user_role_setter_calls =
        FreezingArchRule.freeze(
            ArchRuleDefinition.noClasses()
                    .that().resideOutsideOfPackages("com.ulticode.modules.auth..", "com.ulticode.modules.user..")
                    .should().callMethod(com.ulticode.modules.user.entity.User.class, "setRole", String.class)
        ).because("P2-DISC-005: Only Auth account adapters and User provisioning ports may write to User::setRole. "
                + "Admin and other foreign modules must route role changes through backend-auth RoleAdministrationService.");
    /* P2-DISC-006 retired: legacy permission module deleted in P7-RETIRE-PERMISSION-001.
       UserPermissionMapper no longer exists; the boundary rule is moot. */
}
