import { apiGet } from "@/utils/request";
import type {
  Achievement,
  AchievementListResult,
  AchievementProgress,
  AchievementQuery,
} from "@/types/achievement";

export const achievementApi = {
  /**
   * Get all achievements (with optional filtering)
   */
  async getAll(params?: AchievementQuery): Promise<AchievementListResult> {
    return apiGet<AchievementListResult>("/achievements", { params });
  },

  /**
   * Get a single achievement by ID
   */
  async getById(id: string): Promise<Achievement> {
    return apiGet<Achievement>(`/achievements/${id}`);
  },

  /**
   * Get user's achievements with progress
   */
  async getUserAchievements(): Promise<AchievementProgress[]> {
    return apiGet<AchievementProgress[]>("/achievements/my");
  },

  /**
   * Get user's total achievement points
   */
  async getUserPoints(): Promise<{ points: number }> {
    return apiGet<{ points: number }>("/achievements/points");
  },
};
