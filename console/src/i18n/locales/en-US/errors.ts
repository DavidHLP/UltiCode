export default {
  // HTTP errors
  http: {
    400: "Bad request",
    401: "Please login first",
    403: "Permission denied",
    404: "Resource not found",
    409: "Resource conflict",
    500: "Internal server error",
    networkError: "Network connection failed",
    timeout: "Request timeout",
  },

  // Auth module errors (1xxxx)
  auth: {
    AUTH_10001: "Invalid username or password",
    AUTH_10002:
      "This account has no password set. Please use another login method.",
    AUTH_10003: "Username is already taken",
    AUTH_10004: "Email is already in use",
    AUTH_10005: "Account not found",
    AUTH_10006: "Session expired. Please login again.",
  },

  // User module errors (2xxxx)
  user: {
    USER_20001: "User not found",
    USER_20002: "You don't have permission to edit this profile",
  },

  // Problem module errors (3xxxx)
  problem: {
    PROBLEM_30001: "Problem not found",
    PROBLEM_30002: "Problem is locked",
    PROBLEM_30003: "Premium subscription required",
  },

  // Submission module errors (4xxxx)
  submission: {
    SUBMISSION_40001: "Submission not found",
    SUBMISSION_40002: "User ID is required",
    SUBMISSION_40003: "Too many submissions. Please try again later.",
    SUBMISSION_40004: "Code cannot be empty",
    SUBMISSION_40005: "Unsupported programming language",
  },

  // Solution module errors (5xxxx)
  solution: {
    SOLUTION_50001: "Solution not found",
    SOLUTION_50002: "You don't have permission to delete this solution",
    SOLUTION_50003: "You don't have permission to edit this solution",
    SOLUTION_50004: "Comment not found",
    SOLUTION_50007:
      "You need to solve this problem first before posting a solution",
    SOLUTION_50008: "You have already posted a solution for this problem",
  },

  // Forum module errors (6xxxx)
  forum: {
    FORUM_60001: "Post not found",
    FORUM_60002: "Community not found",
    FORUM_60003: "This community is restricted",
    FORUM_60004: "You don't have permission to edit this post",
    FORUM_60005: "You don't have permission to delete this post",
    FORUM_60006: "Comment not found",
    FORUM_60007: "Post is locked",
  },

  // Contest module errors (7xxxx)
  contest: {
    CONTEST_70001: "Contest not found",
    CONTEST_70002: "You can only register for upcoming contests",
    CONTEST_70003: "You are already registered for this contest",
    CONTEST_70004: "You are not registered for this contest",
    CONTEST_70005: "Registration is closed",
    CONTEST_70006: "Contest is full",
    CONTEST_70007: "You don't have permission to participate in this contest",
    CONTEST_70008: "Contest has not started yet",
    CONTEST_70009: "Contest has ended",
    CONTEST_70010: "Contest has not started yet",
  },

  // Bookmark module errors (8xxxx)
  bookmark: {
    BOOKMARK_80001: "Bookmark folder not found",
    BOOKMARK_80002: "Cannot delete default folder",
    BOOKMARK_80003: "Folder name already exists",
  },

  // Problem list module errors (9xxxx)
  problemList: {
    PROBLEM_LIST_90001: "Problem list not found",
    PROBLEM_LIST_90002: "You don't have permission to edit this list",
    PROBLEM_LIST_90003: "This is a private list and cannot be accessed",
  },

  // Default error message
  default: "Operation failed. Please try again later.",
  unknown: "An unknown error occurred",
} as const;
