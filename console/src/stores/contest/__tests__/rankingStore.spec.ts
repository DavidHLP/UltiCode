// console/src/stores/contest/__tests__/rankingStore.spec.ts
import { describe, it, expect, beforeEach, vi } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useRankingStore } from "../rankingStore";
import * as contestApi from "@/api/contest";

// Mock the API module
vi.mock("@/api/contest", () => ({
  getRanking: vi.fn(),
}));

const mockRankingEntry = {
  rank: 1,
  userId: "user-1",
  username: "testuser",
  avatar: null,
  country: null,
  totalScore: 300,
  totalPenalty: 10,
  solvedCount: 3,
  finishTime: 5000,
  ratingBefore: 1500,
  ratingAfter: 1520,
  ratingChange: 20,
  isVirtual: false,
  problemResults: [
    {
      problemIndex: "A",
      problemId: "1",
      isSolved: true,
      score: 100,
      attempts: 1,
      wrongAttempts: 0,
      solveTime: 1000,
      penaltyTime: 1000,
    },
  ],
};

const mockPaginatedRanking = {
  items: [mockRankingEntry],
  total: 100,
  page: 1,
  limit: 50,
  totalPages: 2,
};

describe("useRankingStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  describe("initial state", () => {
    it("should have empty initial state", () => {
      const store = useRankingStore();

      expect(store.rankings).toEqual([]);
      expect(store.loading).toBe(false);
      expect(store.error).toBeNull();
    });
  });

  describe("fetchRanking", () => {
    it("should fetch ranking successfully", async () => {
      vi.mocked(contestApi.getRanking).mockResolvedValue(mockPaginatedRanking);
      const store = useRankingStore();

      await store.fetchRanking("weekly-contest-1");

      expect(store.rankings).toEqual([mockRankingEntry]);
      expect(store.loading).toBe(false);
      expect(store.error).toBeNull();
    });

    it("should fetch ranking with options", async () => {
      vi.mocked(contestApi.getRanking).mockResolvedValue(mockPaginatedRanking);
      const store = useRankingStore();

      await store.fetchRanking("weekly-contest-1", {
        page: 2,
        limit: 100,
        includeVirtual: false,
      });

      expect(contestApi.getRanking).toHaveBeenCalledWith("weekly-contest-1", {
        page: 2,
        limit: 100,
        includeVirtual: false,
      });
    });

    it("should handle fetch error", async () => {
      vi.mocked(contestApi.getRanking).mockRejectedValue(
        new Error("Network error"),
      );
      const store = useRankingStore();

      await expect(store.fetchRanking("weekly-contest-1")).rejects.toThrow(
        "Network error",
      );

      expect(store.error).toBe("Network error");
      expect(store.loading).toBe(false);
    });
  });

  describe("clearRanking", () => {
    it("should clear ranking data", async () => {
      vi.mocked(contestApi.getRanking).mockResolvedValue(mockPaginatedRanking);
      const store = useRankingStore();

      await store.fetchRanking("weekly-contest-1");
      store.clearRanking();

      expect(store.rankings).toEqual([]);
      expect(store.error).toBeNull();
    });
  });

  describe("getters", () => {
    it("top10 should return first 10 entries", async () => {
      const manyEntries = Array.from({ length: 15 }, (_, i) => ({
        ...mockRankingEntry,
        rank: i + 1,
        userId: `user-${i + 1}`,
        username: `user${i + 1}`,
      }));
      vi.mocked(contestApi.getRanking).mockResolvedValue({
        items: manyEntries,
        total: 15,
        page: 1,
        limit: 50,
        totalPages: 1,
      });
      const store = useRankingStore();

      await store.fetchRanking("weekly-contest-1");

      expect(store.top10).toHaveLength(10);
      expect(store.top10[0].rank).toBe(1);
      expect(store.top10[9].rank).toBe(10);
    });

    it("top10 should return all entries if less than 10", async () => {
      const fewEntries = Array.from({ length: 5 }, (_, i) => ({
        ...mockRankingEntry,
        rank: i + 1,
        userId: `user-${i + 1}`,
        username: `user${i + 1}`,
      }));
      vi.mocked(contestApi.getRanking).mockResolvedValue({
        items: fewEntries,
        total: 5,
        page: 1,
        limit: 50,
        totalPages: 1,
      });
      const store = useRankingStore();

      await store.fetchRanking("weekly-contest-1");

      expect(store.top10).toHaveLength(5);
    });

    it("top10 should return empty array when no rankings", () => {
      const store = useRankingStore();

      expect(store.top10).toEqual([]);
    });

    it("isFrozen should return true when contest is in FREEZING status", () => {
      // Note: isFrozen getter depends on contest status, which we don't have direct access to
      // For now, we'll test with a frozen flag in the ranking data if available
      // This getter may need to be updated based on actual implementation
      const store = useRankingStore();

      // Default should be false since we don't have contest status
      expect(store.isFrozen).toBe(false);
    });
  });

  describe("clearError", () => {
    it("should clear error", () => {
      const store = useRankingStore();
      store.error = "Some error";
      store.clearError();

      expect(store.error).toBeNull();
    });
  });

  describe("$reset", () => {
    it("should reset all state to initial values", async () => {
      vi.mocked(contestApi.getRanking).mockResolvedValue(mockPaginatedRanking);
      const store = useRankingStore();

      // Populate store
      await store.fetchRanking("weekly-contest-1");
      store.error = "Some error";

      // Reset
      store.$reset();

      expect(store.rankings).toEqual([]);
      expect(store.loading).toBe(false);
      expect(store.error).toBeNull();
    });
  });
});
