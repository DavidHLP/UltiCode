import { apiGet } from "@/utils/request";
import type { ProblemRunResult } from "@/types/test-results";

export async function fetchProblemRunResultByProblemId(
  problemId: number,
): Promise<ProblemRunResult | null> {
  return apiGet<ProblemRunResult | null>(`/problems/${problemId}/results`);
}
