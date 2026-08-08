/**
 * Direct Vitest unit tests for `useSocket`.
 *
 * Sibling to `useRealtimeChannel.spec.ts`: both composables share the
 * `bindConnectionStatus` / auth-watch lifecycle glue, but only
 * `useRealtimeChannel` had a direct test after the realtime-chain
 * deepening (architecture-review candidate 4). This suite covers the
 * `useSocket` half — status binding, the typed event helpers + their
 * unsubscribe, the auth-gated connect/disconnect, and full unmount cleanup.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { defineComponent, h, nextTick } from "vue";
import { mount } from "@vue/test-utils";

let authed = false;
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({
    get isAuthenticated() {
      return authed;
    },
  }),
}));

const sm = vi.hoisted(() => {
  const handlers = new Map<string, Set<(payload: unknown) => void>>();
  return {
    status: "disconnected" as string,
    on: vi.fn((event: string, handler: (payload: unknown) => void) => {
      if (!handlers.has(event)) handlers.set(event, new Set());
      handlers.get(event)!.add(handler);
    }),
    off: vi.fn((event: string, handler: (payload: unknown) => void) => {
      handlers.get(event)?.delete(handler);
    }),
    connect: vi.fn(),
    disconnect: vi.fn(),
    subscribeToContest: vi.fn(),
    unsubscribeFromContest: vi.fn(),
    _emit(event: string, payload: unknown) {
      handlers.get(event)?.forEach((h) => h(payload));
    },
    _emitStatus(status: string) {
      handlers.get("connection:status")?.forEach((h) => h(status));
    },
    _reset() {
      handlers.clear();
    },
  };
});

vi.mock("@/lib/socket", () => ({
  getSocketManager: () => sm,
  NotificationEvent: {
    SUBMISSION_RESULT: "submission_result",
    CONTEST_UPDATE: "contest_status",
    BADGE_EARNED: "badge_earned",
    SYSTEM_ANNOUNCEMENT: "announcement",
  },
}));

import { useSocket } from "../useSocket";

function mountSocket(options: Parameters<typeof useSocket>[0] = {}) {
  let api: ReturnType<typeof useSocket> | undefined;
  const Host = defineComponent({
    setup() {
      api = useSocket(options);
      return () => h("div");
    },
  });
  const wrapper = mount(Host);
  return { get api() { return api!; }, wrapper };
}

describe("useSocket", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authed = false;
    sm.status = "disconnected";
    sm._reset();
  });

  it("binds connection:status and reflects it in status/isConnected", async () => {
    const { api } = mountSocket();
    expect(api.isConnected.value).toBe(false);
    sm._emitStatus("connected");
    await nextTick();
    expect(api.status.value).toBe("connected");
    expect(api.isConnected.value).toBe(true);
    sm._emitStatus("disconnected");
    await nextTick();
    expect(api.isConnected.value).toBe(false);
  });

  it("registers a typed event handler and unsubscribes via the returned handle", () => {
    const { api } = mountSocket();
    const cb = vi.fn();
    const unsub = api.onSubmissionResult(cb);

    sm._emit("submission_result", { submissionId: "s1" });
    expect(cb).toHaveBeenCalledWith({ submissionId: "s1" });

    unsub();
    expect(sm.off).toHaveBeenCalledWith("submission_result", expect.any(Function));
    sm._emit("submission_result", { submissionId: "s2" });
    expect(cb).toHaveBeenCalledTimes(1);
  });

  it("connect() opens the socket only when authenticated", () => {
    authed = false;
    const { api } = mountSocket({ autoConnect: false });
    api.connect();
    expect(sm.connect).not.toHaveBeenCalled();

    authed = true;
    api.connect();
    expect(sm.connect).toHaveBeenCalledTimes(1);
  });

  it("disconnect() always tears the socket down", () => {
    // authed + autoConnect:false → the immediate auth watch is a no-op on
    // mount, so the only disconnect comes from the explicit call below.
    authed = true;
    const { api } = mountSocket({ autoConnect: false });
    api.disconnect();
    expect(sm.disconnect).toHaveBeenCalledTimes(1);
  });

  it("auto-connects on mount when authenticated (default autoConnect)", () => {
    authed = true;
    mountSocket();
    // immediate auth watch + autoConnect default true → connect on mount
    expect(sm.connect).toHaveBeenCalled();
  });

  it("does not auto-connect when autoConnect is false, even if authenticated", () => {
    authed = true;
    mountSocket({ autoConnect: false });
    expect(sm.connect).not.toHaveBeenCalled();
  });

  it("disconnects on mount when unauthenticated (immediate auth watch)", () => {
    authed = false;
    mountSocket();
    expect(sm.disconnect).toHaveBeenCalled();
  });

  it("delegates contest subscription helpers to the socket manager", () => {
    const { api } = mountSocket({ autoConnect: false });
    api.subscribeToContest("c1");
    api.unsubscribeFromContest("c1");
    expect(sm.subscribeToContest).toHaveBeenCalledWith("c1");
    expect(sm.unsubscribeFromContest).toHaveBeenCalledWith("c1");
  });

  it("cleans up the connection binding and every registered handler on unmount", () => {
    const { api, wrapper } = mountSocket({ autoConnect: false });
    api.onSubmissionResult(vi.fn());
    api.onNotification(vi.fn());

    wrapper.unmount();

    // one connection:status binding + two typed event handlers
    expect(sm.off).toHaveBeenCalledWith("connection:status", expect.any(Function));
    expect(sm.off).toHaveBeenCalledWith("submission_result", expect.any(Function));
    expect(sm.off).toHaveBeenCalledWith("announcement", expect.any(Function));
    expect(sm.off).toHaveBeenCalledTimes(3);
  });
});
