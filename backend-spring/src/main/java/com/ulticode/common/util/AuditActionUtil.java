package com.ulticode.common.util;

/**
 * Standard audit action and entity type constants.
 * Must stay in sync with frontend i18n keys.
 */
public final class AuditActionUtil {

    private AuditActionUtil() {
        // Utility class
    }

    // Actions

    public static final String CREATE_USER = "CREATE_USER";
    public static final String UPDATE_USER = "UPDATE_USER";
    public static final String DELETE_USER = "DELETE_USER";
    public static final String BAN_USER = "BAN_USER";
    public static final String UNBAN_USER = "UNBAN_USER";
    public static final String RESET_PASSWORD = "RESET_PASSWORD";

    public static final String CREATE_PROBLEM = "CREATE_PROBLEM";
    public static final String UPDATE_PROBLEM = "UPDATE_PROBLEM";
    public static final String DELETE_PROBLEM = "DELETE_PROBLEM";

    public static final String CREATE_CONTEST = "CREATE_CONTEST";
    public static final String UPDATE_CONTEST = "UPDATE_CONTEST";
    public static final String DELETE_CONTEST = "DELETE_CONTEST";

    public static final String CREATE_SOLUTION = "CREATE_SOLUTION";
    public static final String UPDATE_SOLUTION = "UPDATE_SOLUTION";
    public static final String DELETE_SOLUTION = "DELETE_SOLUTION";
    public static final String FLAG_SOLUTION = "FLAG_SOLUTION";
    public static final String UNFLAG_SOLUTION = "UNFLAG_SOLUTION";

    public static final String CREATE_FORUM_POST = "CREATE_FORUM_POST";
    public static final String UPDATE_FORUM_POST = "UPDATE_FORUM_POST";
    public static final String DELETE_FORUM_POST = "DELETE_FORUM_POST";
    public static final String PIN_POST = "PIN_POST";
    public static final String UNPIN_POST = "UNPIN_POST";
    public static final String LOCK_POST = "LOCK_POST";
    public static final String UNLOCK_POST = "UNLOCK_POST";

    public static final String CREATE_TAG = "CREATE_TAG";
    public static final String UPDATE_TAG = "UPDATE_TAG";
    public static final String DELETE_TAG = "DELETE_TAG";

    public static final String GRANT_PERMISSION = "GRANT_PERMISSION";
    public static final String REVOKE_PERMISSION = "REVOKE_PERMISSION";

    public static final String UPDATE_SETTINGS = "UPDATE_SETTINGS";

    public static final String UPDATE_PROBLEM_LIST = "UPDATE_PROBLEM_LIST";
    public static final String DELETE_PROBLEM_LIST = "DELETE_PROBLEM_LIST";

    public static final String CREATE_NOTIFICATION = "CREATE_NOTIFICATION";
    public static final String DELETE_NOTIFICATION = "DELETE_NOTIFICATION";

    public static final String CREATE_CONTEST_ANNOUNCEMENT = "CREATE_CONTEST_ANNOUNCEMENT";
    public static final String UPDATE_CONTEST_ANNOUNCEMENT = "UPDATE_CONTEST_ANNOUNCEMENT";
    public static final String DELETE_CONTEST_ANNOUNCEMENT = "DELETE_CONTEST_ANNOUNCEMENT";

    public static final String REQUEUE_SUBMISSION = "REQUEUE_SUBMISSION";
    public static final String DELETE_SUBMISSION = "DELETE_SUBMISSION";

    public static final String FLAG_COMMENT = "FLAG_COMMENT";
    public static final String UNFLAG_COMMENT = "UNFLAG_COMMENT";
    public static final String DELETE_COMMENT = "DELETE_COMMENT";

    public static final String MODERATE_CONTENT = "MODERATE_CONTENT";

    // Entity types

    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_PROBLEM = "PROBLEM";
    public static final String ENTITY_CONTEST = "CONTEST";
    public static final String ENTITY_CONTEST_ANNOUNCEMENT = "CONTEST_ANNOUNCEMENT";
    public static final String ENTITY_SOLUTION = "SOLUTION";
    public static final String ENTITY_SUBMISSION = "SUBMISSION";
    public static final String ENTITY_FORUM_POST = "FORUM_POST";
    public static final String ENTITY_FORUM_COMMENT = "FORUM_COMMENT";
    public static final String ENTITY_COMMENT = "COMMENT";
    public static final String ENTITY_TAG = "TAG";
    public static final String ENTITY_PROBLEM_LIST = "PROBLEM_LIST";
    public static final String ENTITY_SETTINGS = "SETTINGS";
    public static final String ENTITY_PERMISSION = "PERMISSION";
    public static final String ENTITY_NOTIFICATION = "NOTIFICATION";
}
