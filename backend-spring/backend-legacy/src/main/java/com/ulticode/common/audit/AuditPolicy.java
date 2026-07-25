package com.ulticode.common.audit;

import java.util.Objects;

/**
 * Single source of truth for "what does the system audit / ban-check?".
 *
 * <p>Prior to this catalog, the answer required
 * {@code grep -rn '@Audited' backend-spring/} across 11 service files for
 * audit triggers, and a second grep for {@code @CheckBan} across 4 files
 * for ban enforcement. The annotation was the trigger; the policy was
 * invisible — see
 * {@code /tmp/architecture-review-1783341079.html} Card 6.
 *
 * <p>This catalog is a documentation-first, annotation-compatible design:
 * the {@code @Audited} / {@code @CheckBan} annotations stay in place
 * (touching 16 files in a single commit is out of scope), but the catalog
 * lists every audited / ban-checked entry point so a reviewer can answer
 * the compliance question in one file. A test
 * ({@code AuditPolicyCoverageTest}) verifies the catalog's accuracy —
 * adding a new {@code @Audited} method without registering it here fails
 * CI.
 *
 * <p>If a future commit decides to lift annotations into a runtime
 * registry, the catalog entries become the registry declarations and the
 * aspect becomes a thin dispatcher. Until then this is the audit
 * "map"; the annotations are the audit "triggers".
 */
public final class AuditPolicy {

    private AuditPolicy() {
        // utility class
    }

    /**
     * One audited entry point. The {@code declaringClass} +
     * {@code methodName} pair matches a {@code @Audited} annotation site.
     */
    public record AuditEntry(
            String declaringClass,
            String methodName,
            String action,
            String entityType,
            String notes
    ) {
        public AuditEntry {
            Objects.requireNonNull(declaringClass, "declaringClass");
            Objects.requireNonNull(methodName, "methodName");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(entityType, "entityType");
        }
    }

    /**
     * One ban-enforcement entry point. {@code declaringClass} +
     * {@code methodName} matches a {@code @CheckBan} annotation site.
     */
    public record BanEntry(
            String declaringClass,
            String methodName,
            String targetModule,
            String notes
    ) {
        public BanEntry {
            Objects.requireNonNull(declaringClass, "declaringClass");
            Objects.requireNonNull(methodName, "methodName");
            Objects.requireNonNull(targetModule, "targetModule");
        }
    }

    // -----------------------------------------------------------------------
    // Audited entry points — kept in sync with `@Audited` annotations.
    // Last reviewed: 2026-07-06 (architecture review sweep).
    // -----------------------------------------------------------------------

    public static final AuditEntry[] AUDITED = {
            // Admin: user management
            entry("com.ulticode.modules.admin.service.impl.UserManagementServiceImpl",
                    "createUser", "CREATE_USER", "USER",
                    "ADMIN creates a new user account"),
            entry("com.ulticode.modules.admin.service.impl.UserManagementServiceImpl",
                    "updateUser", "UPDATE_USER", "USER",
                    "ADMIN updates user profile fields"),
            entry("com.ulticode.modules.admin.service.impl.UserManagementServiceImpl",
                    "deleteUser", "DELETE_USER", "USER",
                    "ADMIN deletes a user account"),
            entry("com.ulticode.modules.admin.service.impl.UserManagementServiceImpl",
                    "banUser", "BAN_USER", "USER",
                    "ADMIN bans a user — writes audit + ban record"),
            entry("com.ulticode.modules.admin.service.impl.UserManagementServiceImpl",
                    "unbanUser", "UNBAN_USER", "USER",
                    "ADMIN lifts a ban"),
            entry("com.ulticode.modules.admin.service.impl.UserManagementServiceImpl",
                    "resetPassword", "RESET_PASSWORD", "USER",
                    "ADMIN-initiated password reset"),
            entry("com.ulticode.modules.admin.service.impl.UserPermissionServiceImpl",
                    "assignUserPermission", "GRANT_PERMISSION", "PERMISSION", null),
            entry("com.ulticode.modules.admin.service.impl.UserPermissionServiceImpl",
                    "revokeUserPermission", "REVOKE_PERMISSION", "PERMISSION", null),

            // Admin: content
            entry("com.ulticode.modules.admin.service.impl.AdminProblemListServiceImpl",
                    "updateProblemList", "UPDATE_PROBLEM_LIST", "PROBLEM_LIST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminProblemListServiceImpl",
                    "deleteProblemList", "DELETE_PROBLEM_LIST", "PROBLEM_LIST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminProblemListServiceImpl",
                    "updateListProblems", "UPDATE_PROBLEM_LIST", "PROBLEM_LIST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminProblemListServiceImpl",
                    "updateBasicInfo", "UPDATE_PROBLEM_LIST", "PROBLEM_LIST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminProblemListServiceImpl",
                    "updateVisibility", "UPDATE_PROBLEM_LIST", "PROBLEM_LIST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminProblemListServiceImpl",
                    "updateBanner", "UPDATE_PROBLEM_LIST", "PROBLEM_LIST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminSolutionServiceImpl",
                    "flagSolution", "FLAG_SOLUTION", "SOLUTION", null),
            entry("com.ulticode.modules.admin.service.impl.AdminSolutionServiceImpl",
                    "unflagSolution", "UNFLAG_SOLUTION", "SOLUTION", null),
            entry("com.ulticode.modules.admin.service.impl.AdminSolutionServiceImpl",
                    "deleteSolution", "DELETE_SOLUTION", "SOLUTION", null),
            entry("com.ulticode.modules.admin.service.impl.AdminSolutionServiceImpl",
                    "bulkAction", "BULK_SOLUTION_ACTION", "SOLUTION", null),
            entry("com.ulticode.modules.admin.service.impl.AdminCommentServiceImpl",
                    "flagComment", "FLAG_COMMENT", "COMMENT", null),
            entry("com.ulticode.modules.admin.service.impl.AdminCommentServiceImpl",
                    "unflagComment", "UNFLAG_COMMENT", "COMMENT", null),
            entry("com.ulticode.modules.admin.service.impl.AdminCommentServiceImpl",
                    "deleteComment", "DELETE_COMMENT", "COMMENT", null),
            entry("com.ulticode.modules.admin.service.impl.AdminTagServiceImpl",
                    "createTag", "CREATE_TAG", "TAG", null),
            entry("com.ulticode.modules.admin.service.impl.AdminTagServiceImpl",
                    "updateTag", "UPDATE_TAG", "TAG", null),
            entry("com.ulticode.modules.admin.service.impl.AdminTagServiceImpl",
                    "deleteTag", "DELETE_TAG", "TAG", null),
            entry("com.ulticode.modules.admin.service.impl.AdminTagServiceImpl",
                    "mergeTag", "UPDATE_TAG", "TAG", null),
            entry("com.ulticode.modules.admin.service.impl.AdminNotificationServiceImpl",
                    "createSystemNotification", "CREATE_NOTIFICATION", "NOTIFICATION", null),
            entry("com.ulticode.modules.admin.service.impl.AdminNotificationServiceImpl",
                    "deleteNotification", "DELETE_NOTIFICATION", "NOTIFICATION", null),
            entry("com.ulticode.modules.admin.service.impl.AdminNotificationServiceImpl",
                    "updateSystemNotification", "UPDATE_NOTIFICATION", "NOTIFICATION", null),
            entry("com.ulticode.modules.admin.service.impl.AdminSubmissionServiceImpl",
                    "rejudge", "REQUEUE_SUBMISSION", "SUBMISSION",
                    "ADMIN re-judges a submission"),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "createContest", "CREATE_CONTEST", "CONTEST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "updateContest", "UPDATE_CONTEST", "CONTEST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "deleteContest", "DELETE_CONTEST", "CONTEST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "startContest", "UPDATE_CONTEST", "CONTEST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "endContest", "UPDATE_CONTEST", "CONTEST", null),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "createAnnouncement", "CREATE_CONTEST_ANNOUNCEMENT", "CONTEST_ANNOUNCEMENT", null),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "updateAnnouncement", "UPDATE_CONTEST_ANNOUNCEMENT", "CONTEST_ANNOUNCEMENT", null),
            entry("com.ulticode.modules.admin.service.impl.AdminContestMutationServiceImpl",
                    "deleteAnnouncement", "DELETE_CONTEST_ANNOUNCEMENT", "CONTEST_ANNOUNCEMENT", null),

            // Contest (user-facing) — admin lifecycle (create/update/delete/
            // start/end) lives in AdminContestMutationServiceImpl above; only
            // contest-problem link management remains on ContestServiceImpl.
            entry("com.ulticode.modules.contest.service.impl.ContestServiceImpl",
                    "addProblem", "UPDATE_CONTEST", "CONTEST", null),
            entry("com.ulticode.modules.contest.service.impl.ContestServiceImpl",
                    "removeProblem", "UPDATE_CONTEST", "CONTEST", null),
    };

    // -----------------------------------------------------------------------
    // Ban-check entry points — kept in sync with `@CheckBan` annotations.
    // Banned users can still READ; only writes (create post, create
    // comment, create solution) are blocked here.
    // -----------------------------------------------------------------------

    public static final BanEntry[] BAN_CHECKED = {
            entry("com.ulticode.modules.solution.service.impl.SolutionServiceImpl",
                    "create", "solution",
                    "banned users cannot create new solutions"),
            entry("com.ulticode.modules.solution.service.impl.SolutionServiceImpl",
                    "createComment", "solution",
                    "banned users cannot comment on solutions"),
            entry("com.ulticode.modules.forum.service.impl.ForumPostServiceImpl",
                    "createPost", "forum",
                    "banned users cannot create new forum posts"),
            entry("com.ulticode.modules.forum.service.impl.ForumCommentServiceImpl",
                    "createComment", "forum",
                    "banned users cannot create new forum comments"),
    };

    private static AuditEntry entry(String declaringClass, String methodName,
                                     String action, String entityType, String notes) {
        return new AuditEntry(declaringClass, methodName, action, entityType, notes);
    }

    private static BanEntry entry(String declaringClass, String methodName,
                                   String targetModule, String notes) {
        return new BanEntry(declaringClass, methodName, targetModule, notes);
    }
}