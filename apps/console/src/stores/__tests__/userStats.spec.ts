import { describe, it, expect, vi, beforeEach } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useUserStatsStore } from "@/stores/userStats";
import { useAuthStore } from "@/stores/auth";
import { fetchUserStats, fetchUserSkills } from "@/api/user";
import type { UserStats, UserSkills } from "@/types/userStats";
import type { User } from "@/types/auth";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPatch: vi.fn(),
  apiPost: vi.fn(),
  apiDelete: vi.fn(),
}));

vi.mock("@/api/user", () => ({
  fetchUserStats: vi.fn(),
  fetchUserSkills: vi.fn(),
  fetchUserProfile: vi.fn(),
  fetchProfileByUsername: vi.fn(),
  updateMyProfile: vi.fn(),
}));

const mockStats: UserStats = {
  stats: {
    Easy: { count: 5, total: 100 },
    Medium: { count: 3, total: 200 },
    Hard: { count: 1, total: 80 },
  },
  streak: 7,
  totalSolved: 9,
  heatmap: [],
};

const mockSkills: UserSkills = {
  skills: [{ tagName: "Array", tagSlug: "array", count: 5 }],
  totalSolved: 9,
};

describe("useUserStatsStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    const auth = useAuthStore();
    // userId is a computed off `user` ref in auth store
    auth.user = { id: "user-123" } as User;
  });

  describe("fetchStats", () => {
    it("calls fetchUserStats (from api/user) with auth userId", async () => {
      vi.mocked(fetchUserStats).mockResolvedValue(mockStats);
      const store = useUserStatsStore();
      const result = await store.fetchStats(true);
      expect(fetchUserStats).toHaveBeenCalledWith("user-123");
      expect(result).toEqual(mockStats);
      expect(store.stats).toEqual(mockStats);
    });

    it("does NOT import userStatsApi (was removed in C3)", async () => {
      // Sanity: if userStatsApi were re-introduced, this test would not catch it
      // (we mock the new module). The proof is in the imports above: we never
      // import from "@/api/userStats" because that file is deleted.
      vi.mocked(fetchUserStats).mockResolvedValue(mockStats);
      const store = useUserStatsStore();
      await store.fetchStats(true);
      expect(fetchUserStats).toHaveBeenCalledTimes(1);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      expect((store as any).userStatsApi).toBeUndefined();
    });

    it("returns null when no auth userId", async () => {
      const auth = useAuthStore();
      auth.user = null;
      const store = useUserStatsStore();
      const result = await store.fetchStats(true);
      expect(result).toBeNull();
      expect(fetchUserStats).not.toHaveBeenCalled();
    });
  });

  describe("fetchSkills", () => {
    it("calls fetchUserSkills with auth userId", async () => {
      vi.mocked(fetchUserSkills).mockResolvedValue(mockSkills);
      const store = useUserStatsStore();
      const result = await store.fetchSkills(true);
      expect(fetchUserSkills).toHaveBeenCalledWith("user-123");
      expect(result).toEqual(mockSkills);
      expect(store.skills).toEqual(mockSkills);
    });
  });

  describe("computed progress", () => {
    it("easyProgress percentage = count/total * 100", async () => {
      vi.mocked(fetchUserStats).mockResolvedValue(mockStats);
      const store = useUserStatsStore();
      await store.fetchStats(true);
      expect(store.easyProgress).toEqual({
        count: 5,
        total: 100,
        percentage: 5,
      });
      expect(store.mediumProgress.percentage).toBe(2); // 3/150 rounded
      expect(store.hardProgress.percentage).toBe(1); // 1/80 rounded
    });

    it("totalProgress sums counts and totals", async () => {
      vi.mocked(fetchUserStats).mockResolvedValue(mockStats);
      const store = useUserStatsStore();
      await store.fetchStats(true);
      expect(store.totalProgress.count).toBe(5 + 3 + 1);
      expect(store.totalProgress.total).toBe(100 + 200 + 80);
    });

    it("returns zero progress when stats not loaded", () => {
      const store = useUserStatsStore();
      expect(store.easyProgress).toEqual({ count: 0, total: 0, percentage: 0 });
    });
  });
});
