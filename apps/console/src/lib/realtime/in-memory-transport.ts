// console/src/lib/realtime/in-memory-transport.ts
import type { IMessage } from "@stomp/stompjs";
import {
  type ConnectionStatus,
  type EventHandler,
  type RealtimeTransport,
} from "@/lib/realtime/transport";

/**
 * In-memory {@link RealtimeTransport} for deterministic unit tests.
 *
 * <p>Implements the full {@link RealtimeTransport} interface with a
 * synchronous event loop and exposes test helpers
 * ({@link InMemoryTransportHelpers}) so a test can simulate
 * connect / disconnect, deliver messages to subscribed destinations, and
 * inspect published payloads without any STOMP / SockJS machinery.
 *
 * <p>Behavior mirrors the production transport semantically (status
 * transitions, listener registry, per-key subscription tracking, JSON
 * dispatch) but skips the reconnect cadence, CSRF header construction,
 * and library-driven async loop. Two adapters in the architecture-review
 * after-state — STOMP (production) and this one (tests).
 */
export interface InMemoryTransportHelpers {
  /** Flip the transport to "connected" and fire the configured onConnect. */
  simulateConnect(): void;
  /** Flip the transport to "disconnected" without a server reason. */
  simulateDisconnect(): void;
  /** Deliver a parsed message body to every subscriber of `destination`. */
  deliver(destination: string, body: unknown): void;
  /** Deliver a raw IMessage body (e.g. malformed JSON) to every subscriber. */
  deliverRaw(destination: string, rawBody: string): void;
  /** Inspect publish history. Each entry is the (destination, body) pair. */
  published(): Array<{ destination: string; body: string }>;
  /** Inspect current subscription destination per key. */
  subscriptions(): Map<string, string>;
}

export interface InMemoryTransport {
  transport: RealtimeTransport;
  helpers: InMemoryTransportHelpers;
}

/**
 * Build an in-memory transport with deterministic helpers for tests.
 *
 * <p>Pass an optional `onConnect` to mimic the production adapter's
 * channel-specific connect-time work (e.g. ContestRoom subscribes the
 * broadcast topic and emits "ready" inside onConnect).
 */
export function createInMemoryTransport(
  options: {
    onConnect?: (transport: RealtimeTransport) => void;
  } = {},
): InMemoryTransport {
  const listeners = new Map<string, Set<EventHandler>>();
  const subs = new Map<string, { destination: string; handler: (m: IMessage) => void }>();
  const publishes: Array<{ destination: string; body: string }> = [];
  let status: ConnectionStatus = "disconnected";

  const emit: RealtimeTransport["emit"] = (event, ...args) => {
    listeners.get(event)?.forEach((cb) => cb(...args));
  };

  const setStatus = (next: ConnectionStatus) => {
    status = next;
    listeners.get("connection:status")?.forEach((cb) => cb(next));
  };

  const dispatch: RealtimeTransport["dispatch"] = (event, message) => {
    try {
      const body = JSON.parse(message.body) as unknown;
      emit(event, body);
    } catch (error) {
      console.error(`[in-memory] Error parsing message for ${event}:`, error);
    }
  };

  const transport: RealtimeTransport = {
    get status() {
      return status;
    },
    connect: () => {
      if (status === "connected") return;
      setStatus("connecting");
      // Synchronous connect — helpers.simulateConnect() flips to "connected"
      // so tests can interleave events between transport.connect() and the
      // ready hook. This matches the legacy "join-during-connect" flow.
    },
    disconnect: () => {
      subs.clear();
      setStatus("disconnected");
    },
    isConnected: () => status === "connected",
    on: (event, handler) => {
      if (!listeners.has(event)) listeners.set(event, new Set());
      listeners.get(event)!.add(handler);
    },
    off: (event, handler) => {
      listeners.get(event)?.delete(handler);
    },
    emit,
    dispatch,
    subscribe: (key, destination, handler) => {
      subs.set(key, { destination, handler });
    },
    unsubscribeKey: (key) => {
      subs.delete(key);
    },
    publish: (destination, body) => {
      publishes.push({ destination, body });
    },
  };

  const helpers: InMemoryTransportHelpers = {
    simulateConnect: () => {
      if (status === "connected") return;
      setStatus("connected");
      emit("connected", { connected: true });
      options.onConnect?.(transport);
    },
    simulateDisconnect: () => {
      setStatus("disconnected");
      emit("disconnect", { reason: "disconnected" });
    },
    deliver: (destination, body) => {
      const raw = typeof body === "string" ? body : JSON.stringify(body);
      const message = { body: raw } as IMessage;
      for (const sub of subs.values()) {
        if (sub.destination === destination) {
          sub.handler(message);
        }
      }
    },
    deliverRaw: (destination, rawBody) => {
      const message = { body: rawBody } as IMessage;
      for (const sub of subs.values()) {
        if (sub.destination === destination) {
          sub.handler(message);
        }
      }
    },
    published: () => publishes.slice(),
    subscriptions: () =>
      new Map(
        Array.from(subs.entries()).map(([k, v]) => [k, v.destination] as const),
      ),
  };

  return { transport, helpers };
}