package com.ulticode.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Application-wide error codes.
 * MUST match NestJS error codes exactly for frontend compatibility.
 * <p>
 * Format: MODULE_XXXXX
 * - AUTH        = 1xxxx  Authentication module
 * - USER        = 2xxxx  User module
 * - PROBLEM     = 3xxxx  Problem module
 * - SUBMISSION  = 4xxxx  Submission module
 * - SOLUTION    = 5xxxx  Solution module
 * - FORUM       = 6xxxx  Forum module
 * - CONTEST     = 7xxxx  Contest module
 * - BOOKMARK    = 8xxxx  Bookmark module
 * - PROBLEM_LIST = 9xxxx Problem list module
 */
@Getter
public enum ErrorCode {

    // Generic errors (0xxxx)
    SUCCESS(0, "success", HttpStatus.OK),
    UNKNOWN_ERROR(50000, "Unknown error", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR(50001, "Database error", HttpStatus.INTERNAL_SERVER_ERROR),
    BAD_REQUEST(40000, "Bad request", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(49999, "Validation failed", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "Unauthorized", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "Forbidden", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "Not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40500, "Method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(40900, "Conflict", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(42900, "Too many requests", HttpStatus.TOO_MANY_REQUESTS),

    // Auth module (1xxxx)
    AUTH_INVALID_CREDENTIALS(10001, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    AUTH_NO_PASSWORD(10002, "No password provided", HttpStatus.UNAUTHORIZED),
    AUTH_USERNAME_TAKEN(10003, "Username already taken", HttpStatus.CONFLICT),
    AUTH_EMAIL_TAKEN(10004, "Email already taken", HttpStatus.CONFLICT),
    AUTH_USER_NOT_FOUND(10005, "User not found", HttpStatus.NOT_FOUND),
    AUTH_TOKEN_EXPIRED(10006, "Token expired", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_RESET_TOKEN(10007, "Invalid reset token", HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_ALREADY_USED(10008, "Reset token already used", HttpStatus.BAD_REQUEST),
    AUTH_RESET_TOKEN_EXPIRED(10009, "Reset token expired", HttpStatus.BAD_REQUEST),

    // User module (2xxxx)
    USER_NOT_FOUND(20001, "User not found", HttpStatus.NOT_FOUND),
    USER_CANNOT_EDIT_OTHERS(20002, "Cannot edit other users", HttpStatus.FORBIDDEN),
    USER_BANNED(20003, "You are banned from posting content", HttpStatus.FORBIDDEN),
    USER_PASSWORD_INCORRECT(20004, "Current password is incorrect", HttpStatus.BAD_REQUEST),
    USER_PASSWORD_MISMATCH(20005, "New password and confirmation do not match", HttpStatus.BAD_REQUEST),

    // Problem module (3xxxx)
    PROBLEM_NOT_FOUND(30001, "Problem not found", HttpStatus.NOT_FOUND),
    PROBLEM_LOCKED(30002, "Problem is locked", HttpStatus.FORBIDDEN),
    PROBLEM_PREMIUM_REQUIRED(30003, "Premium subscription required", HttpStatus.FORBIDDEN),
    PROBLEM_VERSION_ALREADY_EXISTS(30004, "Problem version already exists", HttpStatus.CONFLICT),
    PROBLEM_TAG_NOT_FOUND(30010, "Problem tag not found", HttpStatus.NOT_FOUND),
    PROBLEM_TAG_NAME_EXISTS(30011, "Problem tag name already exists", HttpStatus.CONFLICT),
    PROBLEM_TAG_SLUG_EXISTS(30012, "Problem tag slug already exists", HttpStatus.CONFLICT),

    // Problem note (3xxxx) — 题目笔记(per-user 私有).
    // The "problem does not exist" case reuses the existing PROBLEM_NOT_FOUND(30001)
    // declared above — same semantics, no need for a second constant with the same
    // enum name. Only note-specific codes are added here.
    NOTE_NOT_FOUND(30013, "Note not found", HttpStatus.NOT_FOUND),
    NOTE_FORBIDDEN(30014, "Cannot access another user's note", HttpStatus.FORBIDDEN),

    // Test case (3xxxx) — 测试用例属于题目子域,沿用 3xxxx 号段
    TEST_CASE_NOT_FOUND(30020, "Test case not found", HttpStatus.NOT_FOUND),
    TEST_CASE_INVALID_SCOPE(30021, "Test case scope must be exactly one of SAMPLE or HIDDEN", HttpStatus.BAD_REQUEST),

    // Submission module (4xxxx)
    SUBMISSION_NOT_FOUND(40001, "Submission not found", HttpStatus.NOT_FOUND),
    SUBMISSION_USER_ID_REQUIRED(40002, "User ID is required", HttpStatus.BAD_REQUEST),
    SUBMISSION_RATE_LIMITED(40003, "Too many submissions, please try again later", HttpStatus.TOO_MANY_REQUESTS),
    SUBMISSION_CODE_EMPTY(40004, "Code cannot be empty", HttpStatus.BAD_REQUEST),
    SUBMISSION_LANGUAGE_UNSUPPORTED(40005, "Unsupported language", HttpStatus.BAD_REQUEST),
    SANDBOX_ERROR(40006, "Code execution sandbox error", HttpStatus.INTERNAL_SERVER_ERROR),
    SANDBOX_IMAGE_NOT_FOUND(40007, "Sandbox Docker image not found", HttpStatus.INTERNAL_SERVER_ERROR),
    SANDBOX_TIMEOUT(40008, "Code execution timed out", HttpStatus.REQUEST_TIMEOUT),

    // Solution module (5xxxx) — code 50401 is SOLUTION_NOT_FOUND (was 50001; renumbered to
    // avoid collision with DATABASE_ERROR(50001). Frontend consumers see HTTP 404 + 50401.)
    SOLUTION_NOT_FOUND(50401, "Solution not found", HttpStatus.NOT_FOUND),
    SOLUTION_CANNOT_DELETE_OTHERS(50002, "Cannot delete others' solution", HttpStatus.FORBIDDEN),
    SOLUTION_CANNOT_UPDATE_OTHERS(50003, "Cannot update others' solution", HttpStatus.FORBIDDEN),
    SOLUTION_COMMENT_NOT_FOUND(50004, "Solution comment not found", HttpStatus.NOT_FOUND),
    SOLUTION_NEED_ACCEPTED_SUBMISSION(50007, "Need accepted submission to create solution", HttpStatus.FORBIDDEN),
    SOLUTION_ALREADY_EXISTS(50008, "Solution already exists", HttpStatus.CONFLICT),

    // Forum module (6xxxx)
    FORUM_POST_NOT_FOUND(60001, "Post not found", HttpStatus.NOT_FOUND),
    FORUM_COMMUNITY_NOT_FOUND(60002, "Community not found", HttpStatus.NOT_FOUND),
    FORUM_COMMUNITY_RESTRICTED(60003, "Community is restricted", HttpStatus.FORBIDDEN),
    FORUM_CANNOT_EDIT_POST(60004, "Cannot edit this post", HttpStatus.FORBIDDEN),
    FORUM_CANNOT_DELETE_POST(60005, "Cannot delete this post", HttpStatus.FORBIDDEN),
    FORUM_COMMENT_NOT_FOUND(60006, "Comment not found", HttpStatus.NOT_FOUND),
    FORUM_POST_LOCKED(60007, "Post is locked", HttpStatus.FORBIDDEN),
    FORUM_TAG_NOT_FOUND(60010, "Forum tag not found", HttpStatus.NOT_FOUND),
    FORUM_TAG_NAME_EXISTS(60011, "Forum tag name already exists", HttpStatus.CONFLICT),
    FORUM_TAG_SLUG_EXISTS(60012, "Forum tag slug already exists", HttpStatus.CONFLICT),
    FORUM_INVALID_SORT(60013, "Unknown sortBy value", HttpStatus.BAD_REQUEST),

    // Contest module (7xxxx)
    CONTEST_NOT_FOUND(70001, "Contest not found", HttpStatus.NOT_FOUND),
    CONTEST_ONLY_REGISTER_UPCOMING(70002, "Can only register for upcoming contests", HttpStatus.BAD_REQUEST),
    CONTEST_ALREADY_REGISTERED(70003, "Already registered for this contest", HttpStatus.CONFLICT),
    CONTEST_NOT_REGISTERED(70004, "Not registered for this contest", HttpStatus.BAD_REQUEST),
    CONTEST_REGISTRATION_CLOSED(70005, "Contest registration is closed", HttpStatus.BAD_REQUEST),
    CONTEST_ONLY_UPDATE_UPCOMING(70006, "Contest can only be updated when in UPCOMING status", HttpStatus.BAD_REQUEST),
    CONTEST_FULL(70013, "Contest is full", HttpStatus.BAD_REQUEST),
    CONTEST_NO_PERMISSION(70007, "No permission for this contest", HttpStatus.FORBIDDEN),
    CONTEST_NOT_STARTED(70008, "Contest has not started", HttpStatus.BAD_REQUEST),
    CONTEST_ENDED(70009, "Contest has ended", HttpStatus.BAD_REQUEST),
    SCORING_RULE_NOT_FOUND(70010, "Scoring rule not found", HttpStatus.NOT_FOUND),
    CONTEST_PROBLEMS_LOCKED(70014, "Contest problems can only be modified before it starts", HttpStatus.BAD_REQUEST),
    CONTEST_SLUG_EXISTS(70015, "Contest slug already exists", HttpStatus.CONFLICT),

    // Bookmark module (8xxxx)
    BOOKMARK_FOLDER_NOT_FOUND(80001, "Bookmark folder not found", HttpStatus.NOT_FOUND),
    BOOKMARK_CANNOT_DELETE_DEFAULT(80002, "Cannot delete default folder", HttpStatus.BAD_REQUEST),
    BOOKMARK_FOLDER_NAME_EXISTS(80003, "Folder name already exists", HttpStatus.CONFLICT),

    // Problem list module (9xxxx)
    PROBLEM_LIST_NOT_FOUND(90001, "Problem list not found", HttpStatus.NOT_FOUND),
    PROBLEM_LIST_CANNOT_EDIT(90002, "Cannot edit this problem list", HttpStatus.FORBIDDEN),
    PROBLEM_LIST_PRIVATE(90003, "Problem list is private", HttpStatus.FORBIDDEN),
    PROBLEM_LIST_PROBLEM_DUPLICATE(90004, "This problem is already in the list", HttpStatus.CONFLICT),

    // Moderation module (10xxxx)
    MODERATION_QUEUE_NOT_FOUND(100001, "Moderation queue item not found", HttpStatus.NOT_FOUND),
    MODERATION_ALREADY_ASSIGNED(100002, "Item is already assigned to another moderator", HttpStatus.CONFLICT),
    MODERATION_ALREADY_REPORTED(100003, "You have already reported this content", HttpStatus.CONFLICT),
    MODERATION_APPEAL_NOT_FOUND(100004, "Appeal not found", HttpStatus.NOT_FOUND),
    MODERATION_APPEAL_ALREADY_REVIEWED(100005, "This appeal has already been reviewed", HttpStatus.BAD_REQUEST),
    MODERATION_CANNOT_APPEAL(100006, "This item cannot be appealed", HttpStatus.BAD_REQUEST),
    MODERATION_NOT_AUTHOR(100007, "Only the content author can appeal", HttpStatus.FORBIDDEN),

    // Search module (11xxxx)
    SEARCH_QUERY_EMPTY(110001, "Search query cannot be empty", HttpStatus.BAD_REQUEST),
    SEARCH_QUERY_TOO_LONG(110002, "Search query is too long", HttpStatus.BAD_REQUEST),
    SEARCH_INVALID_INDEX(110003, "Invalid search index type", HttpStatus.BAD_REQUEST),

    // Backup module (13xxxx)
    BACKUP_NOT_FOUND(130001, "Backup not found", HttpStatus.NOT_FOUND),
    BACKUP_IN_PROGRESS(130002, "Backup is in progress", HttpStatus.CONFLICT),
    BACKUP_NOT_COMPLETED(130003, "Backup is not completed yet", HttpStatus.BAD_REQUEST),
    BACKUP_FILE_NOT_FOUND(130004, "Backup file not found", HttpStatus.NOT_FOUND),
    BACKUP_FAILED(130005, "Backup failed", HttpStatus.INTERNAL_SERVER_ERROR),
    BACKUP_RESTORE_FAILED(130006, "Database restore failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // I18n module (14xxxx)
    I18N_INVALID_ENTITY_TYPE(140001, "Invalid entity type", HttpStatus.BAD_REQUEST),
    I18N_INVALID_LOCALE(140002, "Invalid locale", HttpStatus.BAD_REQUEST),
    I18N_INVALID_FIELD_NAME(140003, "Invalid field name for entity type", HttpStatus.BAD_REQUEST),

    // WebSocket module (15xxxx)
    WEBSOCKET_UNAUTHORIZED(150001, "WebSocket unauthorized", HttpStatus.UNAUTHORIZED),
    WEBSOCKET_INVALID_CONTEST_ID(150002, "Invalid contest ID", HttpStatus.BAD_REQUEST),
    WEBSOCKET_INVALID_TOKEN(150003, "Invalid WebSocket token", HttpStatus.UNAUTHORIZED),
    WEBSOCKET_TOKEN_BLACKLISTED(150004, "Token has been blacklisted", HttpStatus.UNAUTHORIZED),
    WEBSOCKET_USER_NOT_FOUND(150005, "User not found", HttpStatus.NOT_FOUND),
    WEBSOCKET_USER_BANNED(150006, "Account is banned or inactive", HttpStatus.FORBIDDEN),
    WEBSOCKET_SESSION_MISSING(150007, "WebSocket session is not authenticated", HttpStatus.UNAUTHORIZED),

    // Queue module (16xxxx)
    QUEUE_NOT_FOUND(160001, "Queue not found", HttpStatus.NOT_FOUND),
    QUEUE_JOB_NOT_FOUND(160002, "Job not found", HttpStatus.NOT_FOUND),
    QUEUE_OPERATION_FAILED(160003, "Queue operation failed", HttpStatus.INTERNAL_SERVER_ERROR),

    // Achievement module (17xxxx)
    ACHIEVEMENT_NOT_FOUND(170001, "Achievement not found", HttpStatus.NOT_FOUND),
    ACHIEVEMENT_ALREADY_EARNED(170002, "Achievement already earned", HttpStatus.CONFLICT),
    ACHIEVEMENT_INVALID_TYPE(170003, "Invalid achievement type", HttpStatus.BAD_REQUEST),

    // Subscription module (18xxxx)
    SUBSCRIPTION_NOT_FOUND(180001, "Subscription not found", HttpStatus.NOT_FOUND),
    SUBSCRIPTION_ALREADY_ACTIVE(180002, "User already has an active subscription", HttpStatus.CONFLICT),
    SUBSCRIPTION_EXPIRED(180003, "Subscription has expired", HttpStatus.BAD_REQUEST),
    SUBSCRIPTION_CANCELLED(180004, "Subscription is cancelled", HttpStatus.BAD_REQUEST),

    // Email module (19xxxx)
    EMAIL_TEMPLATE_NOT_FOUND(190001, "Email template not found", HttpStatus.NOT_FOUND),
    EMAIL_SEND_FAILED(190002, "Failed to send email", HttpStatus.INTERNAL_SERVER_ERROR),
    EMAIL_INVALID_RECIPIENT(190003, "Invalid email recipient", HttpStatus.BAD_REQUEST),

    // Settings module (20xxxx)
    SETTING_NOT_FOUND(200001, "Setting not found", HttpStatus.NOT_FOUND),
    SETTING_INVALID_VALUE(200002, "Invalid setting value", HttpStatus.BAD_REQUEST),
    SETTING_PERSISTENCE_FAILED(200003, "Failed to persist setting", HttpStatus.INTERNAL_SERVER_ERROR),
    SETTING_CACHE_CLEAR_FAILED(200004, "Failed to clear setting cache", HttpStatus.INTERNAL_SERVER_ERROR);

    private final Integer code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(Integer code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /**
     * Get HTTP status for this error code
     *
     * @return the corresponding HTTP status
     */
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    /**
     * Find ErrorCode by code value
     *
     * @param code the error code value
     * @return the matching ErrorCode, or UNKNOWN_ERROR if not found
     */
    public static ErrorCode fromCode(Integer code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
}
