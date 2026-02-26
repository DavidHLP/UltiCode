import { apiGet } from "@/utils/request";
import type { UserStats, UserSkills } from "@/types/userStats";

export const userStatsApi = {
  /**
   * Get user stats (problems solved, streak, heatmap)
   */
  async getStats(userId: string): Promise<UserStats> {
    return apiGet<UserStats>(`/users/${userId}/stats`);
  },

  /**
   * Get user skills (based on solved problem tags)
   */
  async getSkills(userId: string): Promise<UserSkills> {
    return apiGet<UserSkills>(`/users/${userId}/skills`);
  },
};
