import { apiGet, apiPatch } from "@/utils/request";

export interface UserProfile {
  id: string;
  username: string;
  name: string;
  email: string;
  bio: string;
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

export interface UserStats {
  stats: Record<"Easy" | "Medium" | "Hard", { count: number; total: number }>;
  streak: number;
  totalSolved: number;
  heatmap: { date: string; level: number }[];
}

export async function fetchUserProfile(userId: string): Promise<UserProfile> {
  return apiGet<UserProfile>(`/users/${userId}`);
}

export async function updateUserProfile(
  userId: string,
  data: Partial<UserProfile>,
): Promise<UserProfile> {
  return apiPatch<UserProfile>(`/users/${userId}`, data);
}

export async function fetchUserStats(userId: string): Promise<UserStats> {
  return apiGet<UserStats>(`/users/${userId}/stats`);
}
