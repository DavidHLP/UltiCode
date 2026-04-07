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
  problem_solving: "text-[oklch(0.6149_0.1394_244.9)]",
  consistency: "text-[oklch(0.6444_0.1508_118.6)]",
  contest: "text-[oklch(0.5924_0.2025_355.9)]",
  community: "text-[oklch(0.6545_0.1340_85.7)]",
};

export const TierLabels: Record<number, string> = {
  1: "Bronze",
  2: "Silver",
  3: "Gold",
};

export const TierColors: Record<number, string> = {
  1: "bg-[oklch(0.6545_0.1340_85.7)]",
  2: "bg-[oklch(0.6979_0.0159_196.8)]",
  3: "bg-[oklch(0.795_0.184_86)]",
};
