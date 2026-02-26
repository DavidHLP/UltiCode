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
  problem_solving: "text-blue-500",
  consistency: "text-green-500",
  contest: "text-purple-500",
  community: "text-orange-500",
};

export const TierLabels: Record<number, string> = {
  1: "Bronze",
  2: "Silver",
  3: "Gold",
};

export const TierColors: Record<number, string> = {
  1: "from-orange-600 to-orange-400",
  2: "from-gray-400 to-gray-200",
  3: "from-yellow-500 to-yellow-300",
};
