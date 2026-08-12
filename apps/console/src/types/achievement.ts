export interface Achievement {
  id: string;
  key: string;
  name: string;
  description: string;
  icon?: string | null;
  category: string;
  tier: number;
  points: number;
  createdAt: string;
  updatedAt: string;
}

export interface AchievementProgress {
  achievementId: string;
  key: string;
  name: string;
  description: string;
  icon?: string;
  category: string;
  tier: number;
  points: number;
  earned: boolean;
  earnedAt?: string;
  progress: number;
  target: number;
}

export interface AchievementListResult {
  total: number;
  page: number;
  limit: number;
  items: Achievement[];
}

export interface AchievementQuery {
  category?: string;
  page?: number;
  limit?: number;
}

export type AchievementCategory =
  | "problem_solving"
  | "consistency"
  | "contest"
  | "community";

export const AchievementCategoryLabels: Record<AchievementCategory, string> = {
  problem_solving: "Problem Solving",
  consistency: "Consistency",
  contest: "Contest",
  community: "Community",
};

export const AchievementCategoryColors: Record<AchievementCategory, string> = {
  problem_solving: "text-foreground-strong",
  consistency: "text-foreground-strong",
  contest: "text-foreground-strong",
  community: "text-foreground-strong",
};

export const TierLabels: Record<number, string> = {
  1: "Bronze",
  2: "Silver",
  3: "Gold",
};

export const TierColors: Record<number, string> = {
  1: "bg-rank-third", // Bronze
  2: "bg-rank-second", // Silver
  3: "bg-rank-first", // Gold
};
