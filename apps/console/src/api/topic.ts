import { apiGet } from "@/utils/request";
import type { SolutionTopic, SolutionTopicsResponse } from "@/types/topic";

export async function fetchSolutionTopics(): Promise<SolutionTopicsResponse> {
  return { topics: await apiGet<SolutionTopic[]>("/solution-topics") };
}
