import type { Problem } from "./problem";

export interface ProblemTestCaseInput {
  id?: string;
  name: string;
  value: string;
  label?: string;
  fieldName?: string;
}

export interface ProblemTestCase {
  id: string;
  inputs?: ProblemTestCaseInput[];
  output?: string;
  isSample?: boolean;
  label?: string;
  explanation?: string;
}

export interface ProblemLanguageOption {
  id: number | string;
  label: string;
  value: string;
  style?: string;
  starterCode: string;
}

export type ProblemReactionType = "like" | "dislike";

export interface ProblemReaction {
  id: string;
  user: {
    id: string;
    username: string;
    avatar?: string;
  };
  type: ProblemReactionType;
}

export interface ProblemInteractionCounts {
  likes: number;
  dislikes: number;
  favorites: number;
}

export interface ProblemInteractionSnapshot {
  reactions: ProblemReaction[];
  counts: ProblemInteractionCounts;
  viewer?: {
    reaction?: ProblemReactionType;
  };
}

export interface ProblemCompany {
  id: string;
  name: string;
  logo?: string;
}

export interface ProblemDetail extends Problem {
  content: string;
  examples: unknown[];
  constraints: string[];
  testCases?: ProblemTestCase[];
  languages: ProblemLanguageOption[];
  starterNotes?: string[];
  followUp?: string;
  summary?: string;
  companies?: ProblemCompany[];
  interactions?: ProblemInteractionSnapshot;
}
