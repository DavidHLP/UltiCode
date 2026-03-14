// console/src/stores/contest/__tests__/contestStore.spec.ts
import { describe, it, expect, beforeEach, vi } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useContestStore } from "../contestStore";
import * as contestApi from "@/api/contest";

// Mock the API module
vi.mock("@/api/contest", () => ({
  getContests: vi.fn(),
  getContest: vi.fn(),
  getContestProblems: vi.fn(),
  getAnnouncements: vi.fn(),
  register: vi.fn(),
  checkIn: vi.fn(),
  withdraw: vi.fn(),
  getMyParticipation: vi.fn(),
}));

const mockContest = {
  id: "contest-1",
  title: "Weekly Contest 1",
  slug: "weekly-contest-1",
  contest_type: "WEEKLY",
  start_time: new Date().toISOString(),
  duration_minutes: 90,
  status: "UPCOMING",
  registered_count: 100,
  participant_count: 0,
  is_rated: true,
  penalty_per_wrong: 5,
  scoring_mode: "SCORE" as const,
  tie_breaker: "LAST_SOLVE_TIME" as const,
};

const mockProblem = {
  id: "cp-1",
  contestId: "contest-1",
  problemId: "1",
  problemIndex: "A",
  title: "Two Sum",
  slug: "two-sum",
  difficulty: "Easy",
  score: 100,
  penaltyPerWrong: 5,
  order: 0,
  solvedCount: 50,
  submissionCount: 100,
};

const mockAnnouncement = {
  id: "ann-1",
  contestId: "contest-1",
  title: "Welcome",
  content: "Welcome to the contest!",
  isPinned: true,
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

const mockParticipation = {
  isRegistered: true,
  status: "REGISTERED",
  participantId: "part-1",
  virtualSessionId: null,
  startedAt: null,
  finishedAt: null,
  totalScore: 0,
  totalPenalty: 0,
};

const mockPaginatedResult = {
  items: [mockContest],
  total: 1,
  page: 1,
  limit: 10,
  totalPages: 1,
};

describe("useContestStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  describe("initial state", () => {
    it("should have empty initial state", () => {
      const store = useContestStore();

      expect(store.contests).toEqual([]);
      expect(store.currentContest).toBeNull();
      expect(store.currentProblems).toEqual([]);
      expect(store.currentAnnouncements).toEqual([]);
      expect(store.myParticipation).toBeNull();
      expect(store.loading).toBe(false);
      expect(store.error).toBeNull();
      expect(store.meta).toEqual({
        total: 0,
        page: 1,
        limit: 10,
        totalPages: 0,
      });
    });
  });

  describe("fetchContests", () => {
    it("should fetch contests successfully", async () => {
      vi.mocked(contestApi.getContests).mockResolvedValue(mockPaginatedResult);
      const store = useContestStore();

      await store.fetchContests();

      expect(store.contests).toEqual([mockContest]);
      expect(store.meta).toEqual({
        total: 1,
        page: 1,
        limit: 10,
        totalPages: 1,
      });
      expect(store.loading).toBe(false);
      expect(store.error).toBeNull();
    });

    it("should fetch contests with filters", async () => {
      vi.mocked(contestApi.getContests).mockResolvedValue(mockPaginatedResult);
      const store = useContestStore();

      await store.fetchContests({ status: "UPCOMING", page: 2, limit: 20 });

      expect(contestApi.getContests).toHaveBeenCalledWith({
        status: "UPCOMING",
        page: 2,
        limit: 20,
      });
    });

    it("should handle fetch error", async () => {
      vi.mocked(contestApi.getContests).mockRejectedValue(
        new Error("Network error"),
      );
      const store = useContestStore();

      await expect(store.fetchContests()).rejects.toThrow("Network error");

      expect(store.error).toBe("Network error");
      expect(store.loading).toBe(false);
    });
  });

  describe("fetchContest", () => {
    it("should fetch contest detail successfully", async () => {
      vi.mocked(contestApi.getContest).mockResolvedValue({
        ...mockContest,
        problems: [],
      });
      const store = useContestStore();

      await store.fetchContest("weekly-contest-1");

      expect(store.currentContest).toEqual({ ...mockContest, problems: [] });
      expect(store.loading).toBe(false);
    });

    it("should handle fetch contest error", async () => {
      vi.mocked(contestApi.getContest).mockRejectedValue(
        new Error("Contest not found"),
      );
      const store = useContestStore();

      await expect(store.fetchContest("invalid-slug")).rejects.toThrow(
        "Contest not found",
      );

      expect(store.error).toBe("Contest not found");
    });
  });

  describe("fetchProblems", () => {
    it("should fetch contest problems successfully", async () => {
      vi.mocked(contestApi.getContestProblems).mockResolvedValue([mockProblem]);
      const store = useContestStore();

      await store.fetchProblems("weekly-contest-1");

      expect(store.currentProblems).toEqual([mockProblem]);
      expect(store.loading).toBe(false);
    });

    it("should handle fetch problems error", async () => {
      vi.mocked(contestApi.getContestProblems).mockRejectedValue(
        new Error("Failed to load problems"),
      );
      const store = useContestStore();

      await expect(store.fetchProblems("weekly-contest-1")).rejects.toThrow(
        "Failed to load problems",
      );

      expect(store.error).toBe("Failed to load problems");
    });
  });

  describe("fetchAnnouncements", () => {
    it("should fetch announcements successfully", async () => {
      vi.mocked(contestApi.getAnnouncements).mockResolvedValue([
        mockAnnouncement,
      ]);
      const store = useContestStore();

      await store.fetchAnnouncements("weekly-contest-1");

      expect(store.currentAnnouncements).toEqual([mockAnnouncement]);
    });
  });

  describe("registerContest", () => {
    it("should register for contest successfully", async () => {
      vi.mocked(contestApi.register).mockResolvedValue(undefined);
      vi.mocked(contestApi.getMyParticipation).mockResolvedValue(
        mockParticipation,
      );
      const store = useContestStore();

      await store.registerContest("weekly-contest-1");

      expect(contestApi.register).toHaveBeenCalledWith("weekly-contest-1");
      expect(store.myParticipation).toEqual(mockParticipation);
    });

    it("should handle register error", async () => {
      vi.mocked(contestApi.register).mockRejectedValue(
        new Error("Already registered"),
      );
      const store = useContestStore();

      await expect(store.registerContest("weekly-contest-1")).rejects.toThrow(
        "Already registered",
      );

      expect(store.error).toBe("Already registered");
    });
  });

  describe("checkInContest", () => {
    it("should check in for contest successfully", async () => {
      vi.mocked(contestApi.checkIn).mockResolvedValue(undefined);
      vi.mocked(contestApi.getMyParticipation).mockResolvedValue({
        ...mockParticipation,
        status: "CHECKED_IN",
      });
      const store = useContestStore();

      await store.checkInContest("weekly-contest-1");

      expect(contestApi.checkIn).toHaveBeenCalledWith("weekly-contest-1");
      expect(store.myParticipation?.status).toBe("CHECKED_IN");
    });
  });

  describe("withdrawContest", () => {
    it("should withdraw from contest successfully", async () => {
      vi.mocked(contestApi.withdraw).mockResolvedValue(undefined);
      vi.mocked(contestApi.getMyParticipation).mockResolvedValue({
        ...mockParticipation,
        isRegistered: false,
        status: null,
      });
      const store = useContestStore();

      await store.withdrawContest("weekly-contest-1");

      expect(contestApi.withdraw).toHaveBeenCalledWith("weekly-contest-1");
      expect(store.myParticipation?.isRegistered).toBe(false);
    });
  });

  describe("clearCurrentContest", () => {
    it("should clear current contest data", async () => {
      vi.mocked(contestApi.getContest).mockResolvedValue({
        ...mockContest,
        problems: [],
      });
      const store = useContestStore();

      await store.fetchContest("weekly-contest-1");
      store.clearCurrentContest();

      expect(store.currentContest).toBeNull();
      expect(store.currentProblems).toEqual([]);
      expect(store.currentAnnouncements).toEqual([]);
      expect(store.myParticipation).toBeNull();
    });
  });

  describe("getters", () => {
    it("isActive should return true for ongoing contest", () => {
      const store = useContestStore();
      store.currentContest = {
        ...mockContest,
        status: "RUNNING",
      };

      expect(store.isActive).toBe(true);
    });

    it("isActive should return false for upcoming contest", () => {
      const store = useContestStore();
      store.currentContest = {
        ...mockContest,
        status: "UPCOMING",
      };

      expect(store.isActive).toBe(false);
    });

    it("isActive should return false when no current contest", () => {
      const store = useContestStore();

      expect(store.isActive).toBe(false);
    });

    it("canRegister should return true for upcoming contest with registration open", () => {
      const store = useContestStore();
      store.currentContest = {
        ...mockContest,
        status: "UPCOMING",
      };

      expect(store.canRegister).toBe(true);
    });

    it("canRegister should return false for running contest", () => {
      const store = useContestStore();
      store.currentContest = {
        ...mockContest,
        status: "RUNNING",
      };

      expect(store.canRegister).toBe(false);
    });

    it("isRegistered should return true when user is registered", () => {
      const store = useContestStore();
      store.myParticipation = {
        ...mockParticipation,
        isRegistered: true,
      };

      expect(store.isRegistered).toBe(true);
    });

    it("isRegistered should return false when user is not registered", () => {
      const store = useContestStore();
      store.myParticipation = {
        ...mockParticipation,
        isRegistered: false,
      };

      expect(store.isRegistered).toBe(false);
    });

    it("isRegistered should return false when no participation data", () => {
      const store = useContestStore();

      expect(store.isRegistered).toBe(false);
    });

    it("isCheckedIn should return true when status is CHECKED_IN", () => {
      const store = useContestStore();
      store.myParticipation = {
        ...mockParticipation,
        status: "CHECKED_IN",
      };

      expect(store.isCheckedIn).toBe(true);
    });

    it("isCheckedIn should return false when status is REGISTERED", () => {
      const store = useContestStore();
      store.myParticipation = {
        ...mockParticipation,
        status: "REGISTERED",
      };

      expect(store.isCheckedIn).toBe(false);
    });
  });

  describe("clearError", () => {
    it("should clear error", () => {
      const store = useContestStore();
      store.error = "Some error";
      store.clearError();

      expect(store.error).toBeNull();
    });
  });

  describe("$reset", () => {
    it("should reset all state to initial values", async () => {
      vi.mocked(contestApi.getContests).mockResolvedValue(mockPaginatedResult);
      vi.mocked(contestApi.getContest).mockResolvedValue({
        ...mockContest,
        problems: [],
      });
      const store = useContestStore();

      // Populate store
      await store.fetchContests();
      await store.fetchContest("weekly-contest-1");
      store.myParticipation = mockParticipation;
      store.error = "Some error";

      // Reset
      store.$reset();

      expect(store.contests).toEqual([]);
      expect(store.currentContest).toBeNull();
      expect(store.currentProblems).toEqual([]);
      expect(store.currentAnnouncements).toEqual([]);
      expect(store.myParticipation).toBeNull();
      expect(store.loading).toBe(false);
      expect(store.error).toBeNull();
      expect(store.meta).toEqual({
        total: 0,
        page: 1,
        limit: 10,
        totalPages: 0,
      });
    });
  });
});
