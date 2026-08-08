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
  facets?: Record<string, Record<string, number>>;
}

export interface SearchQuery {
  query: string;
  index?: SearchIndex;
  page?: number;
  limit?: number;
}
