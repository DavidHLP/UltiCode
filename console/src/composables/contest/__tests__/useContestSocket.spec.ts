// console/src/composables/contest/__tests__/useContestSocket.spec.ts
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { ref } from "vue";
import { setActivePinia, createPinia } from "pinia";

// Mock socket.io-client
const mockSocket = {
  connected: false,
  on: vi.fn(),
  off: vi.fn(),
  emit: vi.fn(),
  disconnect: vi.fn(),
  connect: vi.fn(),
};

vi.mock("socket.io-client", () => ({
  io: vi.fn(() => mockSocket),
}));

// Mock stores
vi.mock("@/stores/auth", () => ({
  useAuthStore: vi.fn(() => ({
    isAuthenticated: ref(true),
    user: ref({ id: "user-1", username: "testuser" }),
  })),
}));

vi.mock("@/stores/contest/rankingStore", () => ({
  useRankingStore: vi.fn(() => ({
    updateRankings: vi.fn(),
    rankings: ref([]),
  })),
}));

vi.mock("@/stores/contest/contestStore", () => ({
  useContestStore: vi.fn(() => ({
    currentAnnouncements: ref([]),
  })),
}));

// Import after mocking
import { useContestSocket } from "../useContestSocket";
import { io } from "socket.io-client";

describe("useContestSocket", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    mockSocket.connected = false;

    // Reset socket instance between tests
    // We need to clear the module cache to reset the singleton
    vi.resetModules();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("initialization", () => {
    it("should return initial disconnected state", () => {
      const { status, isConnected, currentContestId, error } =
        useContestSocket();

      expect(status.value).toBe("disconnected");
      expect(isConnected.value).toBe(false);
      expect(currentContestId.value).toBeNull();
      expect(error.value).toBeNull();
    });

    it("should not auto-connect when autoConnect is false", () => {
      useContestSocket({ autoConnect: false });

      // io should not be called immediately
      expect(io).not.toHaveBeenCalled();
    });
  });

  describe("connection management", () => {
    it("should call connect and create socket", () => {
      const { connect } = useContestSocket({ autoConnect: false });

      connect();

      expect(io).toHaveBeenCalledWith(
        expect.stringContaining("/contest"),
        expect.objectContaining({
          withCredentials: true,
          transports: ["websocket", "polling"],
        }),
      );
    });

    it("should register event listeners on connect", () => {
      const { connect } = useContestSocket({ autoConnect: false });

      connect();

      // Check that event listeners are registered
      expect(mockSocket.on).toHaveBeenCalledWith(
        "connect",
        expect.any(Function),
      );
      expect(mockSocket.on).toHaveBeenCalledWith(
        "disconnect",
        expect.any(Function),
      );
      expect(mockSocket.on).toHaveBeenCalledWith(
        "connect_error",
        expect.any(Function),
      );
      expect(mockSocket.on).toHaveBeenCalledWith(
        "ranking_update",
        expect.any(Function),
      );
      expect(mockSocket.on).toHaveBeenCalledWith(
        "first_solve",
        expect.any(Function),
      );
      expect(mockSocket.on).toHaveBeenCalledWith(
        "announcement",
        expect.any(Function),
      );
      expect(mockSocket.on).toHaveBeenCalledWith(
        "contest_status",
        expect.any(Function),
      );
      expect(mockSocket.on).toHaveBeenCalledWith(
        "submission_result",
        expect.any(Function),
      );
    });

    it("should disconnect socket on disconnect call", () => {
      const { connect, disconnect } = useContestSocket({ autoConnect: false });

      connect();
      disconnect();

      expect(mockSocket.disconnect).toHaveBeenCalled();
    });
  });

  describe("room management", () => {
    it("should join contest room", async () => {
      const mockResponse = {
        success: true,
        contestId: "contest-123",
        message: "Successfully joined contest contest-123",
      };

      mockSocket.emit.mockImplementation((_event, _data, callback) => {
        callback(mockResponse);
      });

      const { connect, joinContest, currentContestId } = useContestSocket({
        autoConnect: false,
      });

      connect();
      const result = await joinContest("contest-123");

      expect(mockSocket.emit).toHaveBeenCalledWith(
        "join_contest",
        "contest-123",
        expect.any(Function),
      );
      expect(result).toEqual(mockResponse);
      expect(currentContestId.value).toBe("contest-123");
    });

    it("should handle join contest failure", async () => {
      const mockResponse = {
        success: false,
        contestId: "contest-123",
        message: "Contest not found",
        error: "NOT_FOUND",
      };

      mockSocket.emit.mockImplementation((_event, _data, callback) => {
        callback(mockResponse);
      });

      const { connect, joinContest, error } = useContestSocket({
        autoConnect: false,
      });

      connect();

      await expect(joinContest("contest-123")).rejects.toThrow(
        "Contest not found",
      );
      expect(error.value).toBe("NOT_FOUND");
    });

    it("should leave contest room", async () => {
      const mockJoinResponse = {
        success: true,
        contestId: "contest-123",
        message: "Successfully joined",
      };
      const mockLeaveResponse = {
        success: true,
        contestId: "contest-123",
        message: "Successfully left",
      };

      mockSocket.emit.mockImplementation((_event, data, callback) => {
        if (_event === "join_contest") {
          callback(mockJoinResponse);
        } else {
          callback(mockLeaveResponse);
        }
      });

      const { connect, joinContest, leaveContest, currentContestId } =
        useContestSocket({ autoConnect: false });

      connect();
      await joinContest("contest-123");
      const result = await leaveContest();

      expect(mockSocket.emit).toHaveBeenCalledWith(
        "leave_contest",
        "contest-123",
        expect.any(Function),
      );
      expect(result).toEqual(mockLeaveResponse);
      expect(currentContestId.value).toBeNull();
    });

    it("should return early if not in a contest when leaving", async () => {
      const { leaveContest } = useContestSocket({ autoConnect: false });

      const result = await leaveContest();

      expect(result).toEqual({
        success: true,
        contestId: "",
        message: "Not in any contest room",
      });
    });
  });

  describe("event subscriptions", () => {
    it("should register ranking update callback", () => {
      const callback = vi.fn();
      const { connect, onRankingUpdate } = useContestSocket({
        autoConnect: false,
      });

      connect();
      const unsub = onRankingUpdate(callback);

      expect(typeof unsub).toBe("function");
      unsub(); // Should not throw
    });

    it("should register first solve callback", () => {
      const callback = vi.fn();
      const { connect, onFirstSolve } = useContestSocket({
        autoConnect: false,
      });

      connect();
      const unsub = onFirstSolve(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should register announcement callback", () => {
      const callback = vi.fn();
      const { connect, onAnnouncement } = useContestSocket({
        autoConnect: false,
      });

      connect();
      const unsub = onAnnouncement(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should register contest status callback", () => {
      const callback = vi.fn();
      const { connect, onContestStatus } = useContestSocket({
        autoConnect: false,
      });

      connect();
      const unsub = onContestStatus(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should register submission result callback", () => {
      const callback = vi.fn();
      const { connect, onSubmissionResult } = useContestSocket({
        autoConnect: false,
      });

      connect();
      const unsub = onSubmissionResult(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should register connection status callback", () => {
      const callback = vi.fn();
      const { onConnectionStatus } = useContestSocket({ autoConnect: false });

      const unsub = onConnectionStatus(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });
  });

  describe("error handling", () => {
    it("should clear error", () => {
      const { error, clearError } = useContestSocket();

      error.value = "Some error";
      clearError();

      expect(error.value).toBeNull();
    });
  });

  describe("options", () => {
    it("should use custom reconnection options", () => {
      const { connect } = useContestSocket({
        autoConnect: false,
        autoReconnect: true,
        maxReconnectAttempts: 5,
        reconnectionDelay: 2000,
      });

      connect();

      expect(io).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          reconnection: true,
          reconnectionAttempts: 5,
          reconnectionDelay: 2000,
        }),
      );
    });
  });

  describe("authentication", () => {
    it("should include token in auth when cookies are present", () => {
      // Mock document.cookie
      Object.defineProperty(document, "cookie", {
        writable: true,
        value: "access_token=test-token-123",
      });

      const { connect } = useContestSocket({ autoConnect: false });

      connect();

      expect(io).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          auth: { token: "test-token-123" },
        }),
      );
    });
  });
});
