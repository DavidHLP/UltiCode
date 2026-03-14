/**
 * Contest-related error codes and messages
 */
export const CONTEST_ERRORS = {
  // Contest not found
  NOT_FOUND: {
    code: 'CONTEST_NOT_FOUND',
    message: 'contest.errors.notFound',
    httpStatus: 404,
  },

  // Registration errors
  REGISTRATION_CLOSED: {
    code: 'CONTEST_REGISTRATION_CLOSED',
    message: 'contest.errors.registrationClosed',
    httpStatus: 400,
  },
  ALREADY_REGISTERED: {
    code: 'CONTEST_ALREADY_REGISTERED',
    message: 'contest.errors.alreadyRegistered',
    httpStatus: 400,
  },
  NOT_REGISTERED: {
    code: 'CONTEST_NOT_REGISTERED',
    message: 'contest.errors.notRegistered',
    httpStatus: 400,
  },
  CONTEST_FULL: {
    code: 'CONTEST_FULL',
    message: 'contest.errors.contestFull',
    httpStatus: 400,
  },

  // Check-in errors
  ALREADY_CHECKED_IN: {
    code: 'CONTEST_ALREADY_CHECKED_IN',
    message: 'contest.errors.alreadyCheckedIn',
    httpStatus: 400,
  },

  // Contest status errors
  NOT_STARTED: {
    code: 'CONTEST_NOT_STARTED',
    message: 'contest.errors.notStarted',
    httpStatus: 400,
  },
  ENDED: {
    code: 'CONTEST_ENDED',
    message: 'contest.errors.ended',
    httpStatus: 400,
  },

  // Submission errors
  SUBMISSION_TIMEOUT: {
    code: 'CONTEST_SUBMISSION_TIMEOUT',
    message: 'contest.errors.submissionTimeout',
    httpStatus: 400,
  },
  SUBMISSION_RATE_LIMITED: {
    code: 'SUBMISSION_RATE_LIMITED',
    message: 'contest.errors.rateLimited',
    httpStatus: 429,
  },

  // Virtual contest errors
  VIRTUAL_SESSION_EXISTS: {
    code: 'VIRTUAL_SESSION_EXISTS',
    message: 'contest.errors.virtualSessionExists',
    httpStatus: 400,
  },
  VIRTUAL_SESSION_NOT_FOUND: {
    code: 'VIRTUAL_SESSION_NOT_FOUND',
    message: 'contest.errors.virtualSessionNotFound',
    httpStatus: 404,
  },

  // Scoring rule errors
  SCORING_RULE_NOT_FOUND: {
    code: 'SCORING_RULE_NOT_FOUND',
    message: 'contest.errors.scoringRuleNotFound',
    httpStatus: 404,
  },
  CANNOT_DELETE_DEFAULT_RULE: {
    code: 'CANNOT_DELETE_DEFAULT_RULE',
    message: 'contest.errors.cannotDeleteDefaultRule',
    httpStatus: 400,
  },
} as const;

export type ContestErrorCode = (typeof CONTEST_ERRORS)[keyof typeof CONTEST_ERRORS]['code'];

/**
 * Get error info by code
 */
export function getContestError(code: ContestErrorCode) {
  return Object.values(CONTEST_ERRORS).find((e) => e.code === code);
}