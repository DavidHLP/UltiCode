import { apiGet, apiPatch } from "@/utils/request";
import type { UserStats, UserSkills } from "@/types/userStats";

/**
 * Profile data matching backend UserVO response.
 * Returned by:
 *   - GET /users/{userId}      → public profile (email stripped via toPublicVO)
 *   - GET /users/me            → current authenticated user
 *   - PATCH /users/me (response)
 *
 * Note: Backend returns snake_case fields (joined_at, submission_count, etc.)
 * unchanged. See CLAUDE.md "Cross-stack DTO alignment" for the
 * known snake_case/camelCase mismatch with ProfileData below.
 */
export interface UserProfile {
  id: string;
  username: string;
  name: string;
  email?: string;
  bio?: string;
  avatar: string;
  location?: string;
  website?: string;
  twitter?: string;
  github?: string;
  joined_at?: string;
  rank?: number;
  solved_count?: number;
  submission_count?: number;
}

/**
 * Profile data matching backend ProfileVO response.
 * Returned by:
 *   - GET /users/{id}/profile
 *   - GET /users/by-username/{username}/profile
 *
 * Note: Uses camelCase (joinedAt, submissionCount) — different
 * naming convention from UserProfile above. Both shapes come
 * from different backend VOs (UserVO vs ProfileVO).
 */
export interface ProfileData {
  id: string;
  username: string;
  name: string;
  avatar: string;
  bio: string;
  company: string;
  location: string;
  website: string;
  joinedAt: string;
  preferredLanguage: string;
  totalSolved: number;
  submissionCount: number;
  globalRank: number | null;
  acceptanceRate: number | null;
  followerCount: number;
  followingCount: number;
  achievementCount: number;
}

export async function fetchUserProfile(userId: string): Promise<UserProfile> {
  return apiGet<UserProfile>(`/users/${userId}`);
}

export async function updateMyProfile(
  data: Partial<UserProfile>,
): Promise<UserProfile> {
  return apiPatch<UserProfile>("/users/me", data);
}

export async function changePassword(data: {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}): Promise<void> {
  return apiPatch<void>("/users/me/password", data);
}

export async function fetchUserStats(userId: string): Promise<UserStats> {
  return apiGet<UserStats>(`/users/${userId}/stats`);
}

export async function fetchUserSkills(userId: string): Promise<UserSkills> {
  return apiGet<UserSkills>(`/users/${userId}/skills`);
}

export async function fetchProfileByUsername(
  username: string,
): Promise<ProfileData> {
  return apiGet<ProfileData>(
    `/users/by-username/${encodeURIComponent(username)}/profile`,
  );
}
