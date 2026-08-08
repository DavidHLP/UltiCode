import { apiGet, apiPatch } from '@/utils/request'
import type { UserStats, UserSkills } from '@/types/userStats'
import { decodeProfile } from '@/api/projection'

/**
 * Wire DTO for backend `UserVO` (snake_case). Used ONLY as the PATCH
 * payload shape for {@link updateMyProfile} — never returned to UI code.
 * The decoded, camelCase view is {@link ProfileData}; consumers should
 * always work with that.
 */
export interface UserProfile {
  id: string
  username: string
  name: string
  email?: string
  bio?: string
  avatar: string
  location?: string
  website?: string
  twitter?: string
  github?: string
  joined_at?: string
  rank?: number
  solved_count?: number
  submission_count?: number
}

/**
 * Canonical profile shape consumed by the UI.
 *
 * Returned by:
 *   - GET /users/{userId}                          (via {@link fetchUserProfile})
 *   - PATCH /users/me                              (via {@link updateMyProfile})
 *   - GET /users/by-username/{username}/profile    (via {@link fetchProfileByUsername})
 *
 * <p>Both `UserVO` (snake_case) and `ProfileVO` (camelCase) backend responses
 * collapse into this shape through {@link decodeProfile}. Consumers never
 * need to know which VO the backend served.
 */
export interface ProfileData {
  id: string
  username: string
  name: string
  avatar: string
  bio: string
  company: string
  location: string
  website: string
  email: string
  twitter: string
  github: string
  joinedAt: string
  preferredLanguage: string
  totalSolved: number
  submissionCount: number
  globalRank: number | null
  acceptanceRate: number | null
  followerCount: number
  followingCount: number
  achievementCount: number
}

export async function fetchUserProfile(userId: string): Promise<ProfileData> {
  return decodeProfile(await apiGet<unknown>(`/users/${userId}`))
}

export async function updateMyProfile(
  data: Partial<UserProfile>,
): Promise<ProfileData> {
  return decodeProfile(await apiPatch<unknown>('/users/me', data))
}

export async function changePassword(data: {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}): Promise<void> {
  return apiPatch<void>('/auth/me/password', data)
}

export async function fetchUserStats(userId: string): Promise<UserStats> {
  return apiGet<UserStats>(`/users/${userId}/stats`)
}

export async function fetchUserSkills(userId: string): Promise<UserSkills> {
  return apiGet<UserSkills>(`/users/${userId}/skills`)
}

export async function fetchProfileByUsername(
  username: string,
): Promise<ProfileData> {
  return decodeProfile(
    await apiGet<unknown>(
      `/users/by-username/${encodeURIComponent(username)}/profile`,
    ),
  )
}