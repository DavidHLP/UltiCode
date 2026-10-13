import type { SubmissionRecord } from "@/types/submission";

const TERMINAL_STATUSES = new Set([
  "Accepted",
  "Wrong Answer",
  "Time Limit Exceeded",
  "Memory Limit Exceeded",
  "Output Limit Exceeded",
  "Runtime Error",
  "Compile Error",
  "Presentation Error",
  "System Error",
  "Sandbox Error",
]);

export function isAgentReviewEligible(
  submission: SubmissionRecord,
  userId: string | null,
  enabled: boolean,
  contestId?: string,
): boolean {
  return enabled
    && !contestId
    && submission.contestSubmission === false
    && Boolean(userId)
    && submission.user?.id === userId
    && TERMINAL_STATUSES.has(submission.status);
}
