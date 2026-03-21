package com.ulticode.common.constants;

/**
 * Application-wide error codes.
 *
 * <p>Format: MODULE_XXXXX
 *
 * <ul>
 *   <li>AUTH = 1xxxx - Authentication module
 *   <li>USER = 2xxxx - User module
 *   <li>PROBLEM = 3xxxx - Problem module
 *   <li>SUBMISSION = 4xxxx - Submission module
 *   <li>SOLUTION = 5xxxx - Solution module
 *   <li>FORUM = 6xxxx - Forum module
 *   <li>CONTEST = 7xxxx - Contest module
 *   <li>BOOKMARK = 8xxxx - Bookmark module
 *   <li>PROBLEM_LIST = 9xxxx - Problem list module
 *   <li>NOTIFICATION = 10xxxx - Notification module
 *   <li>SUBSCRIPTION = 11xxxx - Subscription module
 *   <li>EMAIL = 13xxxx - Email module
 *   <li>BACKUP = 14xxxx - Backup module
 *   <li>WEBSOCKET = 15xxxx - WebSocket module
 *   <li>QUEUE = 16xxxx - Queue module
 *   <li>ACHIEVEMENT = 17xxxx - Achievement module
 * </ul>
 */
public enum ErrorCode {
  // Generic errors (0xxxx)
  SUCCESS(0),
  UNKNOWN_ERROR(50000),
  BAD_REQUEST(40000),
  UNAUTHORIZED(40100),
  FORBIDDEN(40300),
  NOT_FOUND(40400),
  CONFLICT(40900),

  // Auth module (1xxxx)
  AUTH_INVALID_CREDENTIALS(10001),
  AUTH_NO_PASSWORD(10002),
  AUTH_USERNAME_TAKEN(10003),
  AUTH_EMAIL_TAKEN(10004),
  AUTH_USER_NOT_FOUND(10005),
  AUTH_TOKEN_EXPIRED(10006),
  AUTH_INVALID_RESET_TOKEN(10007),
  AUTH_RESET_TOKEN_ALREADY_USED(10008),
  AUTH_RESET_TOKEN_EXPIRED(10009),

  // User module (2xxxx)
  USER_NOT_FOUND(20001),
  USER_CANNOT_EDIT_OTHERS(20002),

  // Problem module (3xxxx)
  PROBLEM_NOT_FOUND(30001),
  PROBLEM_LOCKED(30002),
  PROBLEM_PREMIUM_REQUIRED(30003),

  // Submission module (4xxxx)
  SUBMISSION_NOT_FOUND(40001),
  SUBMISSION_USER_ID_REQUIRED(40002),
  SUBMISSION_RATE_LIMITED(40003),
  SUBMISSION_CODE_EMPTY(40004),
  SUBMISSION_LANGUAGE_UNSUPPORTED(40005),

  // Solution module (5xxxx)
  SOLUTION_NOT_FOUND(50001),
  SOLUTION_CANNOT_DELETE_OTHERS(50002),
  SOLUTION_CANNOT_UPDATE_OTHERS(50003),
  SOLUTION_COMMENT_NOT_FOUND(50004),
  SOLUTION_NEED_ACCEPTED_SUBMISSION(50007),
  SOLUTION_ALREADY_EXISTS(50008),

  // Forum module (6xxxx)
  FORUM_POST_NOT_FOUND(60001),
  FORUM_COMMUNITY_NOT_FOUND(60002),
  FORUM_COMMUNITY_RESTRICTED(60003),
  FORUM_CANNOT_EDIT_POST(60004),
  FORUM_CANNOT_DELETE_POST(60005),
  FORUM_COMMENT_NOT_FOUND(60006),
  FORUM_POST_LOCKED(60007),

  // Contest module (7xxxx)
  CONTEST_NOT_FOUND(70001),
  CONTEST_ONLY_REGISTER_UPCOMING(70002),
  CONTEST_ALREADY_REGISTERED(70003),
  CONTEST_NOT_REGISTERED(70004),
  CONTEST_REGISTRATION_CLOSED(70005),
  CONTEST_FULL(70006),
  CONTEST_NO_PERMISSION(70007),
  CONTEST_NOT_STARTED(70008),
  CONTEST_ENDED(70009),

  // Bookmark module (8xxxx)
  BOOKMARK_FOLDER_NOT_FOUND(80001),
  BOOKMARK_CANNOT_DELETE_DEFAULT(80002),
  BOOKMARK_FOLDER_NAME_EXISTS(80003),

  // Problem list module (9xxxx)
  PROBLEM_LIST_NOT_FOUND(90001),
  PROBLEM_LIST_CANNOT_EDIT(90002),
  PROBLEM_LIST_PRIVATE(90003),

  // Notification module (10xxxx)
  NOTIFICATION_NOT_FOUND(100001),

  // Subscription module (11xxxx)
  SUBSCRIPTION_NOT_FOUND(110001),
  SUBSCRIPTION_ALREADY_ACTIVE(110002),
  SUBSCRIPTION_EXPIRED(110003),

  // Email module (13xxxx)
  EMAIL_SEND_FAILED(130001),
  EMAIL_TEMPLATE_NOT_FOUND(130002),

  // Backup module (14xxxx)
  BACKUP_NOT_FOUND(140001),
  BACKUP_CREATION_FAILED(140002),
  BACKUP_RESTORE_FAILED(140003),

  // WebSocket module (15xxxx)
  WEBSOCKET_UNAUTHORIZED(150001),
  WEBSOCKET_INVALID_CONTEST_ID(150002),
  WEBSOCKET_INVALID_TOKEN(150003),
  WEBSOCKET_TOKEN_BLACKLISTED(150004),
  WEBSOCKET_USER_NOT_FOUND(150005),

  // Queue module (16xxxx)
  QUEUE_NOT_FOUND(160001),
  QUEUE_JOB_NOT_FOUND(160002),
  QUEUE_OPERATION_FAILED(160003),

  // Achievement module (17xxxx)
  ACHIEVEMENT_NOT_FOUND(170001),
  ACHIEVEMENT_ALREADY_EARNED(170002),
  ACHIEVEMENT_INVALID_TYPE(170003);

  private final int code;

  ErrorCode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  /**
   * Get error code name from code value.
   *
   * @param code the error code value
   * @return the error code name or "ERROR_{code}" if not found
   */
  public static String getErrorCodeKey(int code) {
    for (ErrorCode ec : values()) {
      if (ec.code == code) {
        return ec.name();
      }
    }
    return "ERROR_" + code;
  }
}
