import type { SolutionFeedResponse } from "@/api/solution";
import type { ProblemDetail } from "@/types/problem-detail";
import type { SubmissionRecord } from "@/types/submission";
import type { ProblemRunResult } from "@/types/test-results";
import { createHookHub } from "./hookHub";

export type ProblemLayout = "leet" | "classic" | "compact" | "wide";

export type ProblemHookMap = {
  "problem:view:mount": { slug?: string | null };
  "problem:view:unmount": { slug?: string | null };
  "problem:load:before": { slug: string };
  "problem:load:after": { slug: string; problem: ProblemDetail | null };
  "problem:load:error": { slug: string; error: unknown };
  "problem:run:before": { problemId: number; caseCount: number };
  "problem:run:after": { problemId: number; runResult: ProblemRunResult };
  "problem:run:error": { problemId: number; error: unknown };
  "problem:layout:change": { layout: ProblemLayout };
  "problem:tab:change": { from: string | null; to: string };
  "problem:submissions:load:before": {
    problemId: number;
    userId?: string | null;
  };
  "problem:submissions:load:after": {
    problemId: number;
    userId?: string | null;
    submissions: SubmissionRecord[];
  };
  "problem:submissions:load:error": {
    problemId: number;
    userId?: string | null;
    error: unknown;
  };
  "problem:solutions:load:before": {
    problemId: number;
    userId?: string | null;
  };
  "problem:solutions:load:after": {
    problemId: number;
    userId?: string | null;
    feed: SolutionFeedResponse | null;
  };
  "problem:solutions:load:error": {
    problemId: number;
    userId?: string | null;
    error: unknown;
  };
  "problem:code:language:change": { from: string; to: string };
};

export const problemHooks = createHookHub<ProblemHookMap>({
  name: "problem-hooks",
});
