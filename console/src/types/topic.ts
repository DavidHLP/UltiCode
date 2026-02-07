export interface SolutionTopic {
  id: string;
  name: string;
  count: number;
}

export interface SolutionTopicsResponse {
  topics: SolutionTopic[];
}
