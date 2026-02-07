import type { Problem } from "@/types/problem";
import type { SubmissionRecord } from "@/types/submission";

export type DerivedProblemStatus = "solved" | "attempted" | "todo";

const toDateOnly = (iso: string) => iso.split("T")[0] ?? iso;

const getLatestAcceptedDate = (submissions: SubmissionRecord[]) => {
  let latest: string | null = null;
  for (const submission of submissions) {
    if (submission.status !== "Accepted") continue;
    if (!latest || submission.created_at > latest) {
      latest = submission.created_at;
    }
  }
  return latest ? toDateOnly(latest) : null;
};

export const applyProblemStatuses = (
  problems: Problem[],
  submissions: SubmissionRecord[],
): Problem[] => {
  const grouped = new Map<number, SubmissionRecord[]>();
  for (const submission of submissions) {
    const list = grouped.get(submission.problem_id) ?? [];
    list.push(submission);
    grouped.set(submission.problem_id, list);
  }

  return problems.map((problem) => {
    const bucket = grouped.get(problem.id) ?? [];
    const hasAccepted = bucket.some((s) => s.status === "Accepted");
    const status: DerivedProblemStatus = hasAccepted
      ? "solved"
      : bucket.length > 0
        ? "attempted"
        : "todo";
    const completedTime = hasAccepted ? getLatestAcceptedDate(bucket) : null;
    return {
      ...problem,
      status,
      completedTime: completedTime ?? undefined,
    };
  });
};
