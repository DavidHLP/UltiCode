import { apiGet } from "@/utils/request";
import type { SolutionTopicsResponse } from "@/types/topic";

export async function fetchSolutionTopics(): Promise<SolutionTopicsResponse> {
  return apiGet<SolutionTopicsResponse>("/solution-topics");
}
