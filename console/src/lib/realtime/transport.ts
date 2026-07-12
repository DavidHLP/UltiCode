import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";

/**
 * Deep realtime transport.
 *
 * Single owner of every cross-channel WS fact: the {@link ConnectionStatus}
 * union, the listener registry, the STOMP subscription registry, the JSON
 * parse + dispatch path, and the Client lifecycle (SockJS + CSRF + STOMP
 * construction, connect/disconnect, heartbeat). Reconnect cadence and
 * channel-specific connect-time subscriptions live in adapters via
 * {@link RealtimeTransportConfig.onConnect} and {@link ReconnectPolicy}; the
 * transport itself stays free of notification-vs-contest knowledge.
 *
 * One factory call = one channel. Two endpoints stay two physical STOMP
 * connections, but they share one lifecycle implementation.
 */

export type ConnectionStatus =
  | "connected"
  | "disconnected"
  | "connecting"
  | "reconnecting";

export type EventHandler = (...args: unknown[]) => void;

/**
 * Reconnect cadence. {@link library} delegates to @stomp/stompjs' built-in
 * schedule (we only track a cosmetic attempt counter for status); {@link
 * exponential} owns the cadence manually (lib reconnectDelay set to 0) so the
 * adapter controls backoff, attempt caps, and the deactivate/activate loop.
 */
export type ReconnectPolicy =
  | {
      kind: "library";
      reconnectDelay: number;
      maxReconnectDelay: number;
      /** Cosmetic cap; the library's own loop is what actually stops on auth. */
      maxAttempts?: number;
    }
  | {
      kind: "exponential";
      baseDelay: number;
      maxDelay: number;
      maxAttempts: number;
    };

export interface RealtimeTransportConfig {
  endpoint: string;
  apiBaseUrl: string;
  getCsrfToken: () => string | null;
  reconnect: ReconnectPolicy;
  heartbeatIncoming?: number;
  heartbeatOutgoing?: number;
  /** Tag prefixed to console logs so channels are distinguishable. */
  logTag: string;
  /**
   * Channel-specific work run inside onConnect, after the transport has
   * flipped to "connected" but before control returns to the event loop.
   * Adapters (re)establish their STOMP destinations here and may emit their
   * own "ready" event for join-during-connect dances.
   */
  onConnect?: (transport: RealtimeTransport) => void;
  /**
   * Classify a STOMP ERROR frame body. Return "auth" to deactivate and stop
   * the reconnect loop (user is unauthenticated), "rejected" to surface an
   * authorization rejection without looping, or undefined to keep default
   * reconnect behaviour.
   */
  classifyStompError?: (body: string) => "auth" | "rejected" | undefined;
}

export interface RealtimeTransport {
  readonly status: ConnectionStatus;
  connect(): void;
  disconnect(): void;
  isConnected(): boolean;
  on(event: string, handler: EventHandler): void;
  off(event: string, handler: EventHandler): void;
  /** Emit a parsed event to every listener registered for `event`. */
  emit(event: string, ...args: unknown[]): void;
  /** Parse a STOMP message body and dispatch to `event` listeners. */
  dispatch(event: string, message: IMessage): void;
  /**
   * Track a STOMP subscription under `key`, replacing any prior one. No
   * connected-guard: callers that need one check {@link isConnected} first.
   */
  subscribe(
    key: string,
    destination: string,
    handler: (message: IMessage) => void,
  ): void;
  unsubscribeKey(key: string): void;
  publish(destination: string, body: string): void;
}

export function createRealtimeTransport(
  config: RealtimeTransportConfig,
): RealtimeTransport {
  const listeners = new Map<string, Set<EventHandler>>();
  const subscriptions = new Map<string, StompSubscription>();
  let client: Client | null = null;
  let status: ConnectionStatus = "disconnected";
  let reconnectAttempts = 0;
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  const emit: RealtimeTransport["emit"] = (event, ...args) => {
    listeners.get(event)?.forEach((cb) => cb(...args));
  };

  const setStatus = (next: ConnectionStatus) => {
    status = next;
    emit("connection:status", next);
  };

  const dispatch: RealtimeTransport["dispatch"] = (event, message) => {
    try {
      const body = JSON.parse(message.body);
      emit(event, body);
    } catch (error) {
      console.error(
        `[${config.logTag}] Error parsing message for ${event}:`,
        error,
      );
    }
  };

  const subscribe: RealtimeTransport["subscribe"] = (
    key,
    destination,
    handler,
  ) => {
    if (!client) return;
    const existing = subscriptions.get(key);
    if (existing) existing.unsubscribe();
    const sub = client.subscribe(destination, handler);
    subscriptions.set(key, sub);
  };

  const unsubscribeKey: RealtimeTransport["unsubscribeKey"] = (key) => {
    const sub = subscriptions.get(key);
    if (sub) {
      sub.unsubscribe();
      subscriptions.delete(key);
    }
  };

  const publish: RealtimeTransport["publish"] = (destination, body) => {
    // No connected-guard: callers that need one (e.g. leaveContest) check
    // isConnected first; joinContest's performJoin deliberately publishes
    // during the connect handshake, matching the pre-refactor client.publish.
    if (!client) return;
    client.publish({ destination, body });
  };

  /**
   * Exponential-policy reconnect owner. The library's own reconnectDelay is
   * set to 0 for this policy, so we alone drive the deactivate/activate
   * cadence. A successful onConnect resets {@link reconnectAttempts}.
   */
  const scheduleExponential = () => {
    if (config.reconnect.kind !== "exponential") return;
    if (reconnectTimer) clearTimeout(reconnectTimer);
    const { baseDelay, maxDelay, maxAttempts } = config.reconnect;
    if (reconnectAttempts >= maxAttempts) {
      setStatus("disconnected");
      return;
    }
    const delay = Math.min(baseDelay * 2 ** reconnectAttempts, maxDelay);
    reconnectAttempts++;
    reconnectTimer = setTimeout(() => {
      if (client && !client.connected) {
        client.activate();
      }
    }, delay);
  };

  const connect: RealtimeTransport["connect"] = () => {
    // Mirror the original singleton guard: a connected client is reused; a
    // half-open one is overwritten by a fresh Client (preserving prior
    // behaviour where connect-during-connect spawns a new instance).
    if (client?.connected) return;

    setStatus("connecting");

    const csrfToken = config.getCsrfToken();
    const policy = config.reconnect;

    client = new Client({
      webSocketFactory: () =>
        new SockJS(`${config.apiBaseUrl}${config.endpoint}`),
      // Auth relies on httpOnly cookies sent automatically by SockJS
      // (withCredentials). Never read access_token from document.cookie.
      connectHeaders: { "X-CSRF-Token": csrfToken || "" },
      debug: () => {},
      // Exponential policy owns cadence via scheduleExponential; hand the
      // library a 0 so it does not race our loop (R8.4 / F-29).
      reconnectDelay: policy.kind === "library" ? policy.reconnectDelay : 0,
      ...(policy.kind === "library"
        ? { maxReconnectDelay: policy.maxReconnectDelay }
        : {}),
      heartbeatIncoming: config.heartbeatIncoming ?? 10000,
      heartbeatOutgoing: config.heartbeatOutgoing ?? 10000,
      onConnect: () => {
        reconnectAttempts = 0;
        setStatus("connected");
        emit("connected", { connected: true });
        config.onConnect?.(transport);
      },
      onDisconnect: () => {
        setStatus("disconnected");
        emit("disconnect", { reason: "disconnected" });
      },
      onStompError: (frame) => {
        console.error(`[${config.logTag}] STOMP error:`, frame);
        const body = frame.body ?? "";
        const verdict = config.classifyStompError?.(body);
        if (verdict === "auth") {
          // Unauthenticated — stop the reconnect loop and release the client.
          client?.deactivate();
        } else if (verdict === "rejected") {
          // Authorization rejection (e.g. FORBIDDEN) — surface without loop.
          emit("rejected", { frame: body });
        }
        setStatus("disconnected");
        emit("connect_error", { error: frame.body, kind: "stomp" });
      },
      onWebSocketError: (event) => {
        console.error(`[${config.logTag}] WebSocket error:`, event);
        if (policy.kind === "library") {
          // Cosmetic counter only; the library drives its own schedule.
          reconnectAttempts++;
          setStatus(
            reconnectAttempts >= (policy.maxAttempts ?? Infinity)
              ? "disconnected"
              : "reconnecting",
          );
        } else {
          // Cadence is owned by scheduleExponential on close; just reflect
          // status so UIs can show "reconnecting".
          setStatus(
            reconnectAttempts >= policy.maxAttempts
              ? "disconnected"
              : "reconnecting",
          );
        }
        emit("connect_error", event);
      },
      onWebSocketClose: () => {
        if (status === "connected") {
          setStatus("disconnected");
        }
        if (policy.kind === "exponential") {
          scheduleExponential();
        }
      },
    });

    client.activate();
  };

  const disconnect: RealtimeTransport["disconnect"] = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
    subscriptions.forEach((sub) => sub.unsubscribe());
    subscriptions.clear();

    if (client) {
      client.deactivate();
      client = null;
      setStatus("disconnected");
    }
  };

  const isConnected: RealtimeTransport["isConnected"] = () =>
    client?.connected === true;

  const on: RealtimeTransport["on"] = (event, handler) => {
    if (!listeners.has(event)) listeners.set(event, new Set());
    listeners.get(event)!.add(handler);
  };

  const off: RealtimeTransport["off"] = (event, handler) => {
    listeners.get(event)?.delete(handler);
  };

  const transport: RealtimeTransport = {
    get status() {
      return status;
    },
    connect,
    disconnect,
    isConnected,
    on,
    off,
    emit,
    dispatch,
    subscribe,
    unsubscribeKey,
    publish,
  };

  return transport;
}
