import { describe, expect, it } from "vitest";
import type { SubmissionRecord } from "@/types/submission";
import { isAgentReviewEligible } from "../agentReviewPolicy";

const submission = (overrides: Partial<SubmissionRecord> = {}) => ({
  id: "submission-1",
  problem_id: 1,
  status: "Accepted",
  language: "java",
  runtime: 10,
  memory: 20,
  created_at: "2026-01-01T00:00:00Z",
  user: { id: "user-1", username: "learner" },
  contestSubmission: false,
  ...overrides,
}) as SubmissionRecord;

describe("Agent review eligibility", () => {
  it("requires opt-in, explicit practice provenance, ownership, and a known terminal verdict", () => {
    expect(isAgentReviewEligible(submission(), "user-1", true)).toBe(true);
    expect(isAgentReviewEligible(submission(), "user-1", false)).toBe(false);
    expect(isAgentReviewEligible(submission({ contestSubmission: undefined }), "user-1", true)).toBe(false);
    expect(isAgentReviewEligible(submission({ contestSubmission: true }), "user-1", true)).toBe(false);
    expect(isAgentReviewEligible(submission(), "user-2", true)).toBe(false);
    expect(isAgentReviewEligible(submission(), null, true)).toBe(false);
    expect(isAgentReviewEligible(submission({ status: "Judging" }), "user-1", true)).toBe(false);
    expect(isAgentReviewEligible(submission(), "user-1", true, "contest-1")).toBe(false);
  });
});
