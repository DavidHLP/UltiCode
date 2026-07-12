import { describe, it, expect, vi, beforeEach } from "vitest";

// Capture the Client config so tests can fire lifecycle handlers.
let lastClientConfig: Record<string, (...args: unknown[]) => void> = {};
const mockClient = {
  connected: false,
  subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })),
  publish: vi.fn(),
  activate: vi.fn(),
  deactivate: vi.fn(),
};

vi.mock("@stomp/stompjs", () => ({
  Client: vi.fn(function (_config: unknown) {
    lastClientConfig = _config as typeof lastClientConfig;
    return mockClient;
  }),
}));

vi.mock("sockjs-client", () => ({ default: vi.fn(() => ({})) }));

import { Client, type IMessage } from "@stomp/stompjs";
import {
  createRealtimeTransport,
  type RealtimeTransport,
} from "../transport";

function makeTransport(
  overrides: Partial<{
    endpoint: string;
    onConnect: (t: RealtimeTransport) => void;
    classifyStompError: (body: string) => "auth" | "rejected" | undefined;
  }> = {},
) {
  return createRealtimeTransport({
    endpoint: overrides.endpoint ?? "/ws/test",
    apiBaseUrl: "http://test.local",
    getCsrfToken: () => "csrf-token",
    logTag: "TEST",
    reconnect: { kind: "library", reconnectDelay: 1000, maxReconnectDelay: 5000 },
    onConnect: overrides.onConnect,
    classifyStompError: overrides.classifyStompError,
  });
}

describe("createRealtimeTransport", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockClient.connected = false;
    lastClientConfig = {};
  });

  it("builds the STOMP client with CSRF header + SockJS factory on connect", () => {
    const t = makeTransport();
    t.connect();
    expect(Client).toHaveBeenCalledTimes(1);
    const cfg = lastClientConfig as unknown as {
      connectHeaders: Record<string, string>;
      webSocketFactory: () => unknown;
    };
    expect(cfg.connectHeaders["X-CSRF-Token"]).toBe("csrf-token");
    expect(typeof cfg.webSocketFactory).toBe("function");
    expect(mockClient.activate).toHaveBeenCalledTimes(1);
  });

  it("flips status to connected + emits connected + runs adapter onConnect", () => {
    const onConnect = vi.fn();
    const t = makeTransport({ onConnect });
    const statuses: string[] = [];
    t.on("connection:status", (s) => statuses.push(s as string));

    t.connect();
    lastClientConfig.onConnect();

    expect(statuses).toContain("connecting");
    expect(statuses).toContain("connected");
    expect(onConnect).toHaveBeenCalledTimes(1);
    expect(onConnect.mock.calls[0][0]).toBe(t);
  });

  it("dispatch parses JSON body and emits the event", () => {
    const t = makeTransport();
    t.connect();
    const received: unknown[] = [];
    t.on("evt", (payload: unknown) => received.push(payload));
    lastClientConfig.onConnect();
    t.dispatch("evt", { body: JSON.stringify({ hello: "world" }) } as IMessage);
    expect(received).toEqual([{ hello: "world" }]);
  });

  it("subscribe tracks by key and replaces prior subscription", () => {
    const t = makeTransport();
    t.connect();
    lastClientConfig.onConnect();
    t.subscribe("k", "/topic/a", () => {});
    t.subscribe("k", "/topic/b", () => {});
    expect(mockClient.subscribe).toHaveBeenCalledWith("/topic/a", expect.any(Function));
    expect(mockClient.subscribe).toHaveBeenCalledWith("/topic/b", expect.any(Function));
  });

  it("publish delegates to client.publish without a connected guard", () => {
    const t = makeTransport();
    t.connect();
    t.publish("/app/x", "body");
    expect(mockClient.publish).toHaveBeenCalledWith({
      destination: "/app/x",
      body: "body",
    });
  });

  it("classifyStompError=auth deactivates the client", () => {
    const t = makeTransport({ classifyStompError: () => "auth" });
    t.connect();
    lastClientConfig.onStompError({ body: "WEBSOCKET_UNAUTHORIZED" });
    expect(mockClient.deactivate).toHaveBeenCalled();
  });

  it("classifyStompError=rejected emits a rejected event without looping", () => {
    const t = makeTransport({ classifyStompError: () => "rejected" });
    t.connect();
    const rejected: unknown[] = [];
    t.on("rejected", (p: unknown) => rejected.push(p));
    lastClientConfig.onStompError({ body: "FORBIDDEN" });
    expect(rejected).toEqual([{ frame: "FORBIDDEN" }]);
  });

  it("subscribe before CONNECTED still tracks the destination (no connected guard)", () => {
    // performJoin subscribes during the connect handshake, before onConnect
    // fires. The client exists (connect() built it) so the SUBSCRIBE is
    // registered and stompjs holds it until CONNECTED.
    const t = makeTransport();
    t.connect();
    // NB: onConnect NOT fired — still "connecting".
    t.subscribe("contest-1", "/topic/contest/1", () => {});
    expect(mockClient.subscribe).toHaveBeenCalledWith(
      "/topic/contest/1",
      expect.any(Function),
    );
  });

  it("publish while still connecting is queued to the client (join handshake)", () => {
    const t = makeTransport();
    t.connect();
    // still connecting (onConnect not fired); publish must not be dropped
    t.publish("/app/contest.join", "cid");
    expect(mockClient.publish).toHaveBeenCalledWith({
      destination: "/app/contest.join",
      body: "cid",
    });
  });

  it("re-runs adapter onConnect (re-subscribing) after a reconnect", () => {
    vi.useFakeTimers();
    const onConnect = vi.fn();
    const t = createRealtimeTransport({
      endpoint: "/ws/x",
      apiBaseUrl: "http://test.local",
      getCsrfToken: () => null,
      logTag: "X",
      reconnect: {
        kind: "exponential",
        baseDelay: 1000,
        maxDelay: 30_000,
        maxAttempts: 10,
      },
      onConnect,
    });
    t.connect();
    lastClientConfig.onConnect();
    expect(onConnect).toHaveBeenCalledTimes(1);

    // Drop + reconnect: close schedules activate, next onConnect re-runs the
    // adapter so channel subscriptions (e.g. broadcast) are re-established.
    mockClient.connected = false;
    lastClientConfig.onWebSocketClose();
    vi.advanceTimersByTime(1000);
    lastClientConfig.onConnect();
    expect(onConnect).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });

  it("exponential policy stops scheduling once maxAttempts is exhausted", () => {
    vi.useFakeTimers();
    const statuses: string[] = [];
    const t = createRealtimeTransport({
      endpoint: "/ws/x",
      apiBaseUrl: "http://test.local",
      getCsrfToken: () => null,
      logTag: "X",
      reconnect: {
        kind: "exponential",
        baseDelay: 1000,
        maxDelay: 4000,
        maxAttempts: 2,
      },
    });
    t.on("connection:status", (s) => statuses.push(s as string));
    t.connect();
    mockClient.connected = false;

    // attempt 1 (delay 1000), attempt 2 (delay 2000), then exhausted.
    lastClientConfig.onWebSocketClose();
    vi.advanceTimersByTime(1000);
    lastClientConfig.onWebSocketClose();
    vi.advanceTimersByTime(2000);
    const activatesBefore = mockClient.activate.mock.calls.length;
    // third close: attempts >= maxAttempts, must go disconnected, no activate.
    lastClientConfig.onWebSocketClose();
    vi.advanceTimersByTime(30_000);
    expect(mockClient.activate.mock.calls.length).toBe(activatesBefore);
    expect(statuses).toContain("disconnected");
    vi.useRealTimers();
  });
});
