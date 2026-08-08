// console/src/composables/contest/__tests__/useContestSocket.spec.ts
import { describe, it, expect, beforeEach, vi } from "vitest";
import { ref } from "vue";
import { setActivePinia, createPinia } from "pinia";

// Mock @stomp/stompjs
vi.mock("@stomp/stompjs", () => ({
  Client: vi.fn(function () {
    return {
      connected: false,
      subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })),
      publish: vi.fn(),
      activate: vi.fn(),
      deactivate: vi.fn(),
      onConnect: null,
      onDisconnect: null,
      onStompError: null,
      onWebSocketError: null,
    };
  }),
}));

vi.mock("sockjs-client", () => ({
  default: vi.fn(() => ({})),
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
import { Client } from "@stomp/stompjs";
import { useContestSocket } from "../useContestSocket";

describe("useContestSocket", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  describe("initialization", () => {
    it("should return initial disconnected state", () => {
      const { status, isConnected, currentContestId, error } = useContestSocket(
        { autoConnect: false },
      );

      expect(status.value).toBe("disconnected");
      expect(isConnected.value).toBe(false);
      expect(currentContestId.value).toBeNull();
      expect(error.value).toBeNull();
    });
  });

  describe("API surface", () => {
    it("should expose connect method", () => {
      const socket = useContestSocket({ autoConnect: false });
      expect(typeof socket.connect).toBe("function");
    });

    it("should expose disconnect method", () => {
      const socket = useContestSocket({ autoConnect: false });
      expect(typeof socket.disconnect).toBe("function");
    });

    it("should expose joinContest method", () => {
      const socket = useContestSocket({ autoConnect: false });
      expect(typeof socket.joinContest).toBe("function");
    });

    it("should expose leaveContest method", () => {
      const socket = useContestSocket({ autoConnect: false });
      expect(typeof socket.leaveContest).toBe("function");
    });

    it("should expose event callback registration methods", () => {
      const socket = useContestSocket({ autoConnect: false });
      expect(typeof socket.onRankingUpdate).toBe("function");
      expect(typeof socket.onFirstSolve).toBe("function");
      expect(typeof socket.onAnnouncement).toBe("function");
      expect(typeof socket.onContestStatus).toBe("function");
      expect(typeof socket.onSubmissionResult).toBe("function");
      expect(typeof socket.onConnectionStatus).toBe("function");
    });

    it("should expose clearError method", () => {
      const socket = useContestSocket({ autoConnect: false });
      expect(typeof socket.clearError).toBe("function");
    });
  });

  describe("event subscriptions", () => {
    it("should return unsubscriber from onRankingUpdate", () => {
      const { onRankingUpdate } = useContestSocket({ autoConnect: false });
      const callback = vi.fn();
      const unsub = onRankingUpdate(callback);

      expect(typeof unsub).toBe("function");
      unsub(); // Should not throw
    });

    it("should return unsubscriber from onFirstSolve", () => {
      const { onFirstSolve } = useContestSocket({ autoConnect: false });
      const callback = vi.fn();
      const unsub = onFirstSolve(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should return unsubscriber from onAnnouncement", () => {
      const { onAnnouncement } = useContestSocket({ autoConnect: false });
      const callback = vi.fn();
      const unsub = onAnnouncement(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should return unsubscriber from onContestStatus", () => {
      const { onContestStatus } = useContestSocket({ autoConnect: false });
      const callback = vi.fn();
      const unsub = onContestStatus(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should return unsubscriber from onSubmissionResult", () => {
      const { onSubmissionResult } = useContestSocket({ autoConnect: false });
      const callback = vi.fn();
      const unsub = onSubmissionResult(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });

    it("should return unsubscriber from onConnectionStatus", () => {
      const { onConnectionStatus } = useContestSocket({ autoConnect: false });
      const callback = vi.fn();
      const unsub = onConnectionStatus(callback);

      expect(typeof unsub).toBe("function");
      unsub();
    });
  });

  describe("error handling", () => {
    it("should clear error", () => {
      const { error, clearError } = useContestSocket({ autoConnect: false });

      error.value = "Some error";
      clearError();

      expect(error.value).toBeNull();
    });
  });

  describe("room management", () => {
    it("should return early if not in a contest when leaving", async () => {
      const { leaveContest } = useContestSocket({ autoConnect: false });

      const result = await leaveContest();

      expect(result).toEqual({
        success: true,
        contestId: "",
        message: "Not in any contest room",
      });
    });

    it("joinContest while connecting never overwrites onConnect (uses the ready hook)", async () => {
      // Regression for the pre-fix bug where joinContest reassigned
      // client.onConnect, destroying the singleton's connect handler
      // (status notification + broadcast subscription) and leaving the
      // parallel "connected_once" callback as dead code.
      const { connect, joinContest } = useContestSocket({ autoConnect: false });
      connect(); // creates the singleton STOMP client (mock.connected = false)

      const mockedClient = vi.mocked(Client);
      const joinPromise = joinContest("contest-regression");

      const instance = mockedClient.mock.results.at(-1)!.value as {
        connected: boolean;
        onConnect: unknown;
        subscribe: ReturnType<typeof vi.fn>;
        publish: ReturnType<typeof vi.fn>;
      };
      // joinContest must NOT reassign the instance onConnect.
      expect(instance.onConnect).toBeNull();

      // The singleton's onConnect (captured from the constructor config) is
      // the only connect handler; firing it must drive performJoin via the
      // ready hook.
      const config = mockedClient.mock.calls.at(-1)![0] as {
        onConnect: () => void;
      };
      config.onConnect();

      const result = await joinPromise;
      expect(result.success).toBe(true);
      expect(instance.subscribe).toHaveBeenCalledWith(
        "/topic/contest/contest-regression",
        expect.any(Function),
      );
      expect(instance.publish).toHaveBeenCalled();
    });
  });
});
