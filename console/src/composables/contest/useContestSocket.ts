// console/src/composables/contest/useContestSocket.ts
import { ref, onMounted, onUnmounted, watch } from "vue";
import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useAuthStore } from "@/stores/auth";
import type { RankingEntry } from "@/types/contest";

// ============================================================================
// TYPES
// ============================================================================

export type ConnectionStatus =
  | "connected"
  | "disconnected"
  | "connecting"
  | "reconnecting";

/**
 * Response from join/leave contest operations
 */
interface ContestRoomResponse {
  success: boolean;
  contestId: string;
  message: string;
  error?: string;
}

/**
 * Ranking update event payload from server
 */
export interface RankingUpdatePayload {
  contestId: string;
  rankings: RankingEntry[];
  updatedAt: Date | string;
}

/**
 * First solve notification payload from server
 */
export interface FirstSolvePayload {
  contestId: string;
  problemId: string;
  problemTitle: string;
  userId: string;
  username: string;
  solvedAt: Date | string;
}

/**
 * Announcement payload from server
 */
export interface AnnouncementPayload {
  id: string;
  contestId: string;
  title: string;
  content: string;
  createdAt: Date | string;
}

/**
 * Contest status update payload from server
 */
export interface ContestStatusPayload {
  contestId: string;
  status: "upcoming" | "registration" | "running" | "ended";
  startedAt?: Date | string;
  endsAt?: Date | string;
  message?: string;
}

/**
 * Submission result payload from server
 */
export interface SubmissionResultPayload {
  submissionId: string;
  contestId: string;
  problemId: string;
  userId: string;
  status: string;
  score: number;
  timeUsed?: number;
  memoryUsed?: number;
  judgedAt: Date | string;
}

/**
 * Options for useContestSocket composable
 */
export interface UseContestSocketOptions {
  /** Auto-connect when authenticated (default: true) */
  autoConnect?: boolean;
  /** Auto-reconnect on disconnect (default: true) */
  autoReconnect?: boolean;
  /** Maximum reconnection attempts (default: 10) */
  maxReconnectAttempts?: number;
  /** Reconnection delay in ms (default: 1000) */
  reconnectionDelay?: number;
}

/**
 * Return type for useContestSocket composable
 */
export interface UseContestSocketReturn {
  /** Current connection status */
  status: ReturnType<typeof ref<ConnectionStatus>>;
  /** Whether socket is connected */
  isConnected: ReturnType<typeof ref<boolean>>;
  /** Current contest ID if joined */
  currentContestId: ReturnType<typeof ref<string | null>>;
  /** Last error message */
  error: ReturnType<typeof ref<string | null>>;
  /** Connect to WebSocket server */
  connect: () => void;
  /** Disconnect from WebSocket server */
  disconnect: () => void;
  /** Join a contest room */
  joinContest: (contestId: string) => Promise<ContestRoomResponse>;
  /** Leave current contest room */
  leaveContest: () => Promise<ContestRoomResponse>;
  /** Register callback for ranking updates */
  onRankingUpdate: (
    callback: (data: RankingUpdatePayload) => void,
  ) => () => void;
  /** Register callback for first solve notifications */
  onFirstSolve: (callback: (data: FirstSolvePayload) => void) => () => void;
  /** Register callback for announcements */
  onAnnouncement: (callback: (data: AnnouncementPayload) => void) => () => void;
  /** Register callback for contest status changes */
  onContestStatus: (
    callback: (data: ContestStatusPayload) => void,
  ) => () => void;
  /** Register callback for submission results */
  onSubmissionResult: (
    callback: (data: SubmissionResultPayload) => void,
  ) => () => void;
  /** Register callback for connection status changes */
  onConnectionStatus: (
    callback: (status: ConnectionStatus) => void,
  ) => () => void;
  /** Clear any error */
  clearError: () => void;
}

// ============================================================================
// SOCKET MANAGER SINGLETON
// ============================================================================

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:9001";

// Singleton STOMP client instance for contest namespace
let stompClient: Client | null = null;
let connectionStatus: ConnectionStatus = "disconnected";
let reconnectAttempts = 0;
const eventCallbacks = new Map<string, Set<(...args: unknown[]) => void>>();
const subscriptions = new Map<string, StompSubscription>();

/**
 * Get JWT token from cookies for authentication
 */
function getTokenFromCookie(): string | null {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; access_token=`);
  if (parts.length === 2) return parts.pop()?.split(";").shift() || null;

  // Also try ulticode_token (Spring Boot cookie name)
  const ulticodeParts = value.split(`; ulticode_token=`);
  if (ulticodeParts.length === 2)
    return ulticodeParts.pop()?.split(";").shift() || null;

  return null;
}

/**
 * Get CSRF token from cookies
 */
function getCsrfToken(): string | null {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; csrf_token=`);
  if (parts.length === 2) return parts.pop()?.split(";").shift() || null;
  return null;
}

/**
 * Notify all status change listeners
 */
function notifyStatusChange(status: ConnectionStatus): void {
  connectionStatus = status;
  const callbacks = eventCallbacks.get("connection:status");
  if (callbacks) callbacks.forEach((cb) => cb(status));
}

/**
 * Handle incoming STOMP message and dispatch to event callbacks
 */
function handleMessage(eventType: string, message: IMessage): void {
  try {
    const data = JSON.parse(message.body);
    const callbacks = eventCallbacks.get(eventType);
    if (callbacks) callbacks.forEach((cb) => cb(data));
  } catch (error) {
    console.error(
      `[STOMP Contest] Error parsing message for ${eventType}:`,
      error,
    );
  }
}

/**
 * Get or create the contest STOMP client
 */
function getContestSocket(options: Required<UseContestSocketOptions>): Client {
  if (stompClient?.connected) {
    return stompClient;
  }

  const token = getTokenFromCookie();
  const csrfToken = getCsrfToken();

  connectionStatus = "connecting";
  notifyStatusChange("connecting");

  // Pass token as query parameter since SockJS doesn't forward custom headers
  const wsUrl = token
    ? `${API_BASE_URL}/ws/contest?token=${encodeURIComponent(token)}`
    : `${API_BASE_URL}/ws/contest`;

  stompClient = new Client({
    webSocketFactory: () => new SockJS(wsUrl),
    connectHeaders: {
      Authorization: token ? `Bearer ${token}` : "",
      "X-CSRF-Token": csrfToken || "",
    },
    debug: (str) => {
      if (import.meta.env.DEV) {
        console.log(`[STOMP Contest] ${str}`);
      }
    },
    reconnectDelay: options.reconnectionDelay,
    maxReconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      connectionStatus = "connected";
      notifyStatusChange("connected");
      reconnectAttempts = 0;
      console.log("[STOMP Contest] Connected to contest WebSocket");

      // Subscribe to contest topic (general broadcast)
      const broadcastSub = stompClient?.subscribe(
        "/topic/broadcast",
        (message) => handleMessage("announcement", message),
      );
      if (broadcastSub) {
        subscriptions.set("broadcast", broadcastSub);
      }
    },
    onDisconnect: () => {
      connectionStatus = "disconnected";
      notifyStatusChange("disconnected");
      const callbacks = eventCallbacks.get("disconnect");
      if (callbacks) callbacks.forEach((cb) => cb("disconnected"));
      console.log("[STOMP Contest] Disconnected from contest WebSocket");
    },
    onStompError: (frame) => {
      console.error("[STOMP Contest] STOMP error:", frame);
      connectionStatus = "disconnected";
      notifyStatusChange("disconnected");
      const callbacks = eventCallbacks.get("connect_error");
      if (callbacks) callbacks.forEach((cb) => cb({ error: frame.body }));
    },
    onWebSocketError: (event) => {
      console.error("[STOMP Contest] WebSocket error:", event);
      reconnectAttempts++;

      if (reconnectAttempts >= options.maxReconnectAttempts) {
        connectionStatus = "disconnected";
        console.error("[STOMP Contest] Max reconnect attempts reached");
      } else {
        connectionStatus = "reconnecting";
      }
      notifyStatusChange(connectionStatus);

      const callbacks = eventCallbacks.get("connect_error");
      if (callbacks) callbacks.forEach((cb) => cb(event));
    },
    onWebSocketClose: () => {
      if (connectionStatus === "connected") {
        connectionStatus = "disconnected";
        notifyStatusChange("disconnected");
      }
    },
  });

  stompClient.activate();
  return stompClient;
}

/**
 * Disconnect and cleanup STOMP client
 */
function disconnectSocket(): void {
  // Unsubscribe from all subscriptions
  subscriptions.forEach((sub) => sub.unsubscribe());
  subscriptions.clear();

  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
    connectionStatus = "disconnected";
    notifyStatusChange("disconnected");
  }
}

// ============================================================================
// COMPOSABLE
// ============================================================================

/**
 * Composable for contest WebSocket real-time updates
 *
 * Connects to the /ws/contest STOMP endpoint and provides methods to:
 * - Join/leave contest rooms
 * - Listen for ranking updates, first solves, announcements
 * - Handle contest status changes and submission results
 * - Auto-reconnect on connection loss
 *
 * @example
 * ```ts
 * const {
 *   isConnected,
 *   joinContest,
 *   onRankingUpdate,
 *   onFirstSolve,
 * } = useContestSocket();
 *
 * // Join a contest
 * await joinContest('contest-uuid');
 *
 * // Listen for ranking updates
 * onRankingUpdate((data) => {
 *   console.log('New rankings:', data.rankings);
 * });
 * ```
 */
export function useContestSocket(
  options: UseContestSocketOptions = {},
): UseContestSocketReturn {
  const {
    autoConnect = true,
    autoReconnect = true,
    maxReconnectAttempts = 10,
    reconnectionDelay = 1000,
  } = options;

  const authStore = useAuthStore();

  const status = ref<ConnectionStatus>(connectionStatus);
  const isConnected = ref(connectionStatus === "connected");
  const currentContestId = ref<string | null>(null);
  const error = ref<string | null>(null);

  const unsubscribers: (() => void)[] = [];
  const fullOptions: Required<UseContestSocketOptions> = {
    autoConnect,
    autoReconnect,
    maxReconnectAttempts,
    reconnectionDelay,
  };

  // ==================== Status Management ====================

  const handleStatusChange = (newStatus: ConnectionStatus): void => {
    status.value = newStatus;
    isConnected.value = newStatus === "connected";
  };

  // ==================== Connection Methods ====================

  const connect = (): void => {
    if (authStore.isAuthenticated) {
      getContestSocket(fullOptions);
    }
  };

  const disconnect = (): void => {
    disconnectSocket();
    currentContestId.value = null;
  };

  // ==================== Room Management ====================

  const joinContest = async (
    contestId: string,
  ): Promise<ContestRoomResponse> => {
    return new Promise((resolve, reject) => {
      const client = getContestSocket(fullOptions);

      if (!client.connected) {
        // Wait for connection
        const onConnect = () => {
          performJoin();
        };
        eventCallbacks.set("connected_once", new Set([onConnect]));
        client.onConnect = () => {
          performJoin();
        };
      } else {
        performJoin();
      }

      function performJoin() {
        // Unsubscribe from existing contest subscription if any
        const existingKey = `contest-${currentContestId.value}`;
        const existingSub = subscriptions.get(existingKey);
        if (existingSub) {
          existingSub.unsubscribe();
          subscriptions.delete(existingKey);
        }

        // Subscribe to contest-specific topic
        const contestSub = client.subscribe(
          `/topic/contest/${contestId}`,
          (message: IMessage) => {
            // Parse message to determine event type
            try {
              const data = JSON.parse(message.body);
              const eventType = data.type || data.event || "contest_update";
              handleMessage(eventType, message);
            } catch {
              handleMessage("contest_update", message);
            }
          },
        );
        subscriptions.set(`contest-${contestId}`, contestSub);

        // Send join message to server via STOMP
        client.publish({
          destination: "/app/contest.join",
          body: JSON.stringify({ contestId }),
        });

        currentContestId.value = contestId;

        // Resolve with success response
        // In STOMP, we don't get a direct response, so we assume success
        resolve({
          success: true,
          contestId,
          message: `Successfully joined contest ${contestId}`,
        });
      }

      // Timeout after 10 seconds
      setTimeout(() => {
        reject(new Error("Connection timeout"));
      }, 10000);
    });
  };

  const leaveContest = async (): Promise<ContestRoomResponse> => {
    if (!currentContestId.value) {
      return {
        success: true,
        contestId: "",
        message: "Not in any contest room",
      };
    }

    const contestId = currentContestId.value;
    const client = getContestSocket(fullOptions);

    if (client.connected) {
      // Unsubscribe from contest topic
      const key = `contest-${contestId}`;
      const sub = subscriptions.get(key);
      if (sub) {
        sub.unsubscribe();
        subscriptions.delete(key);
      }

      // Send leave message to server
      client.publish({
        destination: "/app/contest.leave",
        body: JSON.stringify({ contestId }),
      });
    }

    currentContestId.value = null;

    return {
      success: true,
      contestId,
      message: `Successfully left contest ${contestId}`,
    };
  };

  // ==================== Event Subscriptions ====================

  const onRankingUpdate = (
    callback: (data: RankingUpdatePayload) => void,
  ): (() => void) => {
    const event = "ranking_update";
    if (!eventCallbacks.has(event)) {
      eventCallbacks.set(event, new Set());
    }
    eventCallbacks.get(event)!.add(callback as (...args: unknown[]) => void);
    return () => {
      eventCallbacks
        .get(event)
        ?.delete(callback as (...args: unknown[]) => void);
    };
  };

  const onFirstSolve = (
    callback: (data: FirstSolvePayload) => void,
  ): (() => void) => {
    const event = "first_solve";
    if (!eventCallbacks.has(event)) {
      eventCallbacks.set(event, new Set());
    }
    eventCallbacks.get(event)!.add(callback as (...args: unknown[]) => void);
    return () => {
      eventCallbacks
        .get(event)
        ?.delete(callback as (...args: unknown[]) => void);
    };
  };

  const onAnnouncement = (
    callback: (data: AnnouncementPayload) => void,
  ): (() => void) => {
    const event = "announcement";
    if (!eventCallbacks.has(event)) {
      eventCallbacks.set(event, new Set());
    }
    eventCallbacks.get(event)!.add(callback as (...args: unknown[]) => void);
    return () => {
      eventCallbacks
        .get(event)
        ?.delete(callback as (...args: unknown[]) => void);
    };
  };

  const onContestStatus = (
    callback: (data: ContestStatusPayload) => void,
  ): (() => void) => {
    const event = "contest_status";
    if (!eventCallbacks.has(event)) {
      eventCallbacks.set(event, new Set());
    }
    eventCallbacks.get(event)!.add(callback as (...args: unknown[]) => void);
    return () => {
      eventCallbacks
        .get(event)
        ?.delete(callback as (...args: unknown[]) => void);
    };
  };

  const onSubmissionResult = (
    callback: (data: SubmissionResultPayload) => void,
  ): (() => void) => {
    const event = "submission_result";
    if (!eventCallbacks.has(event)) {
      eventCallbacks.set(event, new Set());
    }
    eventCallbacks.get(event)!.add(callback as (...args: unknown[]) => void);
    return () => {
      eventCallbacks
        .get(event)
        ?.delete(callback as (...args: unknown[]) => void);
    };
  };

  const onConnectionStatus = (
    callback: (status: ConnectionStatus) => void,
  ): (() => void) => {
    const event = "connection:status";
    if (!eventCallbacks.has(event)) {
      eventCallbacks.set(event, new Set());
    }
    eventCallbacks.get(event)!.add(callback as (...args: unknown[]) => void);
    unsubscribers.push(() => {
      eventCallbacks
        .get(event)
        ?.delete(callback as (...args: unknown[]) => void);
    });
    return () => {
      eventCallbacks
        .get(event)
        ?.delete(callback as (...args: unknown[]) => void);
    };
  };

  const clearError = (): void => {
    error.value = null;
  };

  // ==================== Lifecycle ====================

  onMounted(() => {
    // Subscribe to connection status changes
    onConnectionStatus(handleStatusChange);

    // Auto-connect if enabled and authenticated
    if (autoConnect && authStore.isAuthenticated) {
      connect();
    }

    // Watch for authentication changes
    watch(
      () => authStore.isAuthenticated,
      (isAuth) => {
        if (isAuth && autoConnect) {
          connect();
        } else if (!isAuth) {
          disconnect();
        }
      },
    );
  });

  onUnmounted(() => {
    unsubscribers.forEach((unsub) => unsub());
  });

  return {
    status,
    isConnected,
    currentContestId,
    error,
    connect,
    disconnect,
    joinContest,
    leaveContest,
    onRankingUpdate,
    onFirstSolve,
    onAnnouncement,
    onContestStatus,
    onSubmissionResult,
    onConnectionStatus,
    clearError,
  };
}
