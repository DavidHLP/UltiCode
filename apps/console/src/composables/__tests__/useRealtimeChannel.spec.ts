import { describe, it, expect, vi, beforeEach } from "vitest";
import { useRealtimeChannel } from "../useRealtimeChannel";
import { getSocketManager, NotificationEvent } from "@/lib/socket";

vi.mock("@/lib/socket", () => ({
  getSocketManager: vi.fn(),
  NotificationEvent: {
    SYSTEM_ANNOUNCEMENT: "SYSTEM_ANNOUNCEMENT",
    CONNECTION_STATUS: "CONNECTION_STATUS",
  },
}));

function makeSocketManager() {
  const handlers = new Map<string, Set<(...args: unknown[]) => void>>();
  return {
    on: vi.fn((event: string, handler: (...args: unknown[]) => void) => {
      if (!handlers.has(event)) handlers.set(event, new Set());
      handlers.get(event)!.add(handler);
    }),
    connect: vi.fn(),
    disconnect: vi.fn(),
    _emit(event: string, payload: unknown) {
      handlers.get(event)?.forEach((h) => h(payload));
    },
    _emitStatus(status: string) {
      handlers.get("connection:status")?.forEach((h) => h(status));
    },
  };
}

describe("useRealtimeChannel (options-object signature)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("accepts { isAuthenticated, onItem, onSignedOut } as a single object", () => {
    const sm = makeSocketManager();
    vi.mocked(getSocketManager).mockReturnValue(
      sm as unknown as ReturnType<typeof getSocketManager>,
    );
    const onItem = vi.fn();
    const onSignedOut = vi.fn();

    const channel = useRealtimeChannel({
      isAuthenticated: () => true,
      onItem,
      onSignedOut,
    });

    channel.setupRealtimeListeners();

    expect(sm.on).toHaveBeenCalledWith("connection:status", expect.any(Function));
    expect(sm.on).toHaveBeenCalledWith(
      NotificationEvent.SYSTEM_ANNOUNCEMENT,
      expect.any(Function,
      ),
    );
  });

  it("invokes onItem when a SYSTEM_ANNOUNCEMENT arrives", () => {
    const sm = makeSocketManager();
    vi.mocked(getSocketManager).mockReturnValue(
      sm as unknown as ReturnType<typeof getSocketManager>,
    );
    const onItem = vi.fn();
    const channel = useRealtimeChannel({
      isAuthenticated: () => true,
      onItem,
    });
    channel.setupRealtimeListeners();

    sm._emit(NotificationEvent.SYSTEM_ANNOUNCEMENT, {
      id: "n1",
      title: "hi",
      content: "body",
      type: "info",
      link: null,
      createdAt: "2026-01-01T00:00:00Z",
    });

    expect(onItem).toHaveBeenCalledWith(
      expect.objectContaining({
        id: "n1",
        title: "hi",
        body: "body",
        isRead: false,
      }),
    );
  });

  it("reflects realtimeConnected.value from connection:status events", () => {
    const sm = makeSocketManager();
    vi.mocked(getSocketManager).mockReturnValue(
      sm as unknown as ReturnType<typeof getSocketManager>,
    );
    const channel = useRealtimeChannel({
      isAuthenticated: () => true,
      onItem: vi.fn(),
    });
    channel.setupRealtimeListeners();

    expect(channel.realtimeConnected.value).toBe(false);
    sm._emitStatus("connected");
    expect(channel.realtimeConnected.value).toBe(true);
    sm._emitStatus("disconnected");
    expect(channel.realtimeConnected.value).toBe(false);
  });

  it("invokes onSignedOut when isAuthenticated becomes false", async () => {
    const sm = makeSocketManager();
    vi.mocked(getSocketManager).mockReturnValue(
      sm as unknown as ReturnType<typeof getSocketManager>,
    );
    const isAuth = vi.fn(() => false);
    const onSignedOut = vi.fn();
    const channel = useRealtimeChannel({
      isAuthenticated: isAuth,
      onItem: vi.fn(),
      onSignedOut,
    });
    channel.setupRealtimeListeners();

    expect(sm.disconnect).toHaveBeenCalled();
    expect(onSignedOut).toHaveBeenCalled();
  });
});