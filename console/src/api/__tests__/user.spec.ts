import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet, apiPatch } from "@/utils/request";
import {
  fetchUserProfile,
  updateMyProfile,
  fetchUserStats,
  fetchUserSkills,
  fetchProfileByUsername,
} from "@/api/user";
import type { UserProfile, ProfileData } from "@/api/user";
import type { UserStats, UserSkills } from "@/types/userStats";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPatch: vi.fn(),
}));

const mockProfile: UserProfile = {
  id: "user-123",
  username: "alice",
  name: "Alice",
  email: "alice@example.com",
  bio: "hello world",
  avatar: "https://example.com/a.png",
  joined_at: "2026-01-01T00:00:00Z",
};

const mockProfileData: ProfileData = {
  id: "user-123",
  username: "alice",
  name: "Alice",
  avatar: "https://example.com/a.png",
  bio: "hi",
  company: "ACME",
  location: "Earth",
  website: "https://alice.dev",
  joinedAt: "2026-01-01T00:00:00Z",
  preferredLanguage: "en",
  totalSolved: 42,
  submissionCount: 100,
  globalRank: 7,
  acceptanceRate: 0.42,
  followerCount: 3,
  followingCount: 9,
  achievementCount: 1,
};

const mockStats: UserStats = {
  stats: {
    Easy: { count: 5, total: 100 },
    Medium: { count: 3, total: 200 },
    Hard: { count: 1, total: 80 },
  },
  streak: 7,
  totalSolved: 9,
  heatmap: [{ date: "2026-01-01", level: 2 }],
};

const mockSkills: UserSkills = {
  skills: [
    { tagName: "Array", tagSlug: "array", count: 5 },
    { tagName: "Hash Table", tagSlug: "hash-table", count: 3 },
  ],
  totalSolved: 9,
};

describe("user api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("fetchUserProfile", () => {
    it("calls apiGet with /users/{userId}", async () => {
      vi.mocked(apiGet).mockResolvedValue(mockProfile);
      await fetchUserProfile("user-123");
      expect(apiGet).toHaveBeenCalledWith("/users/user-123");
    });

    it("decodes the snake_case UserVO into ProfileData", async () => {
      vi.mocked(apiGet).mockResolvedValue(mockProfile);
      const result = await fetchUserProfile("user-123");
      // snake_case joined_at → camelCase joinedAt
      expect(result.joinedAt).toBe("2026-01-01T00:00:00Z");
      expect(result.id).toBe("user-123");
      expect(result.username).toBe("alice");
      // empty defaults for fields UserVO does not expose
      expect(result.totalSolved).toBe(0);
      expect(result.followerCount).toBe(0);
      expect(result.globalRank).toBeNull();
    });
  });

  describe("updateMyProfile", () => {
    it("targets /users/me (not /users/{userId})", async () => {
      vi.mocked(apiPatch).mockResolvedValue(mockProfile);
      const update: Partial<UserProfile> = { bio: "updated bio" };
      await updateMyProfile(update);
      // CRITICAL: must call /users/me, not /users/{id}
      expect(apiPatch).toHaveBeenCalledWith("/users/me", update);
      // Negative check: must NOT call /users/{uuid-anything}-like path
      const calls = vi.mocked(apiPatch).mock.calls;
      const usedNonMePath = calls.some(
        ([path]: [unknown]) =>
          typeof path === "string" &&
          path.startsWith("/users/") &&
          path !== "/users/me" &&
          !path.startsWith("/users/me/"),
      );
      expect(usedNonMePath).toBe(false);
    });

    it("returns a decoded ProfileData", async () => {
      vi.mocked(apiPatch).mockResolvedValue(mockProfile);
      const result = await updateMyProfile({ bio: "x" });
      expect(result.joinedAt).toBe("2026-01-01T00:00:00Z");
      expect(result.id).toBe("user-123");
    });

    it("passes Partial<UserProfile> payload verbatim", async () => {
      vi.mocked(apiPatch).mockResolvedValue(mockProfile);
      const update: Partial<UserProfile> = {
        name: "Alice New",
        bio: "x",
        location: "Mars",
        website: "https://x.dev",
        twitter: "https://x.com/alice",
        github: "https://github.com/alice",
      };
      await updateMyProfile(update);
      expect(apiPatch).toHaveBeenCalledWith("/users/me", update);
    });
  });

  describe("fetchUserStats", () => {
    it("calls apiGet with /users/{userId}/stats", async () => {
      vi.mocked(apiGet).mockResolvedValue(mockStats);
      const result = await fetchUserStats("user-123");
      expect(apiGet).toHaveBeenCalledWith("/users/user-123/stats");
      expect(result).toEqual(mockStats);
    });
  });

  describe("fetchUserSkills", () => {
    it("calls apiGet with /users/{userId}/skills", async () => {
      vi.mocked(apiGet).mockResolvedValue(mockSkills);
      const result = await fetchUserSkills("user-123");
      expect(apiGet).toHaveBeenCalledWith("/users/user-123/skills");
      expect(result).toEqual(mockSkills);
    });
  });

  describe("fetchProfileByUsername", () => {
    it("calls apiGet with /users/by-username/{username}/profile", async () => {
      vi.mocked(apiGet).mockResolvedValue(mockProfileData);
      const result = await fetchProfileByUsername("alice");
      expect(apiGet).toHaveBeenCalledWith("/users/by-username/alice/profile");
      // decodeProfile passes camelCase through; editable fields default to ''
      expect(result).toEqual({ ...mockProfileData, email: "", twitter: "", github: "" });
    });

    it("URL-encodes usernames with special characters", async () => {
      vi.mocked(apiGet).mockResolvedValue(mockProfileData);
      await fetchProfileByUsername("alice@example.com");
      expect(apiGet).toHaveBeenCalledWith(
        "/users/by-username/alice%40example.com/profile",
      );
    });

    it("URL-encodes Chinese usernames", async () => {
      vi.mocked(apiGet).mockResolvedValue(mockProfileData);
      await fetchProfileByUsername("张三");
      expect(apiGet).toHaveBeenCalledWith(
        "/users/by-username/%E5%BC%A0%E4%B8%89/profile",
      );
    });
  });
});
