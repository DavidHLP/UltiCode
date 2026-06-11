import { apiGet, apiPost, apiDelete } from "@/utils/request";

export interface FollowStatus {
  following: boolean;
}

export interface FollowStats {
  followerCount: number;
  followingCount: number;
}

export const followApi = {
  /**
   * Follow a user
   */
  async followUser(userId: string): Promise<FollowStats> {
    return apiPost<FollowStats>(`/users/${userId}/follow`);
  },

  /**
   * Unfollow a user
   */
  async unfollowUser(userId: string): Promise<FollowStats> {
    return apiDelete<FollowStats>(`/users/${userId}/follow`);
  },

  /**
   * Check if the current user follows a specific user
   */
  async getFollowStatus(userId: string): Promise<FollowStatus> {
    return apiGet<FollowStatus>(`/users/${userId}/follow/status`);
  },
};
