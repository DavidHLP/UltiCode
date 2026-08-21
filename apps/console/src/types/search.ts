export enum SearchIndex {
  PROBLEMS = "PROBLEMS",
  USERS = "USERS",
  POSTS = "POSTS",
  SOLUTIONS = "SOLUTIONS",
}

export interface SearchResult {
  id: string;
  type: SearchIndex;
  title: string;
  description?: string;
  url: string;
  highlights?: Record<string, string[]>;
}

export interface SearchResponse {
  query: string;
  total: number;
  page: number;
  limit: number;
  results: SearchResult[];
  semantics?: SearchReadSemantics;
  facets?: Record<string, Record<string, number>>;
}

export interface SearchReadSemantics {
  mode: "DATABASE" | "INDEXED";
  source: "DATABASE" | "MEILISEARCH";
  freshness: "REALTIME" | "EVENTUAL";
  ordering: string;
  total: string;
  fallbackApplied: boolean;
}

export interface SearchQuery {
  query: string;
  index?: SearchIndex;
  page?: number;
  limit?: number;
}
