// console/src/composables/contest/useContestSocket.ts
import { ref, onMounted, onUnmounted, watch } from "vue";
import type { IMessage } from "@stomp/stompjs";
import { useAuthStore } from "@/stores/auth";
import type { RankingEntry } from "@/types/contest";
import { getCsrfToken } from "@/shared/auth-core/src";
import {
  createRealtimeTransport,
  type ConnectionStatus,
  type RealtimeTransport,
} from "@/lib/realtime/transport";

// ============================================================================
// TYPES
// ============================================================================

export type { ConnectionStatus };

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
 * Options for useContestSocket composable.
 *
 * Only {@link autoConnect} is honoured per-call. Reconnect policy
 * (cadence, attempt cap, delay) is now owned by the deep realtime transport
 * (see {@link getContestTransport}) so it concentrates in one module instead
 * of drifting per caller; the remaining fields are retained for API
 * compatibility and default to the transport's values.
 */
export interface UseContestSocketOptions {
  /** Auto-connect when authenticated (default: true) */
  autoConnect?: boolean;
  /** Auto-reconnect on disconnect (default: true) — now transport-owned. */
  autoReconnect?: boolean;
  /** Maximum reconnection attempts (default: 10) — now transport-owned. */
  maxReconnectAttempts?: number;
  /** Reconnection delay in ms (default: 1000) — now transport-owned. */
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
// CONTEST CHANNEL ADAPTER (deep transport)
// ============================================================================

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:9001";

let contestTransport: RealtimeTransport | null = null;

/**
 * Contest channel adapter over the deep realtime transport. Endpoint
 * /ws/contest; manual exponential backoff (1s -> 2s -> 4s -> ... -> 30s, max
 * 10 attempts) owned by the transport via scheduleExponential. OnConnect
 * (re)subscribes the broadcast topic then fires the one-shot "ready" event
 * that joinContest uses for the join-during-connect dance. STOMP ERROR frames
 * carrying FORBIDDEN / 403 / "not registered" are classified as "rejected"
 * (F-43) so views can show "you are not registered" instead of looping.
 */
function getContestTransport(): RealtimeTransport {
  if (contestTransport) return contestTransport;
  contestTransport = createRealtimeTransport({
    endpoint: "/ws/contest",
    apiBaseUrl: API_BASE_URL,
    getCsrfToken,
    logTag: "STOMP Contest",
    reconnect: {
      kind: "exponential",
      baseDelay: 1000,
      maxDelay: 30_000,
      maxAttempts: 10,
    },
    onConnect: (t) => {
      // General broadcast topic — must be (re)established before the
      // room-lifecycle "ready" hooks fire so joinContest's performJoin inherits
      // a connected, broadcast-subscribed client.
      t.subscribe("broadcast", "/topic/broadcast", (message: IMessage) =>
        t.dispatch("announcement", message),
      );
      // Fire one-shot room-lifecycle hooks. The transport is the single owner
      // of the connect handler — joinContest registers on "ready" instead of
      // overwriting client.onConnect (the old override clobbered the status
      // notification + broadcast subscription, and the parallel
      // "connected_once" callback was never fired — dead code).
      t.emit("ready");
    },
    classifyStompError: (body) => {
      if (
        body.includes("FORBIDDEN") ||
        body.includes("403") ||
        body.toLowerCase().includes("not registered")
      ) {
        return "rejected";
      }
      return undefined;
    },
  });
  return contestTransport;
}

// ============================================================================
// COMPOSABLE
// ============================================================================

/**
 * Composable for contest WebSocket real-time updates
 *
 * Connects to the /ws/contest STOMP endpoint (via the deep realtime
 * transport) and provides methods to:
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
 * });
 * ```
 */
export function useContestSocket(
  options: UseContestSocketOptions = {},
): UseContestSocketReturn {
  const { autoConnect = true } = options;

  const authStore = useAuthStore();
  const transport = getContestTransport();

  const status = ref<ConnectionStatus>(transport.status);
  const isConnected = ref(transport.status === "connected");
  const currentContestId = ref<string | null>(null);
  const error = ref<string | null>(null);

  const unsubscribers: (() => void)[] = [];

  // ==================== Status Management ====================

  const handleStatusChange = (newStatus: ConnectionStatus): void => {
    status.value = newStatus;
    isConnected.value = newStatus === "connected";
  };

  // ==================== Connection Methods ====================

  const connect = (): void => {
    if (authStore.isAuthenticated) {
      transport.connect();
    }
  };

  const disconnect = (): void => {
    transport.disconnect();
    currentContestId.value = null;
  };

  // ==================== Room Management ====================

  const joinContest = async (
    contestId: string,
  ): Promise<ContestRoomResponse> => {
    return new Promise((resolve, reject) => {
      // Ensure the singleton transport is connecting. connect() reuses a
      // connected client or spawns a fresh one when half-open (matching the
      // pre-refactor getContestSocket behaviour).
      transport.connect();

      // One-shot "ready" hook used only when the client is still connecting.
      // The transport's onConnect emits "ready"; we never overwrite
      // client.onConnect (the old override destroyed status notification +
      // the broadcast subscription, and the parallel "connected_once"
      // callback was dead code).
      let ready: (() => void) | null = null;
      if (!transport.isConnected()) {
        ready = () => {
          if (ready) transport.off("ready", ready);
          performJoin();
        };
        transport.on("ready", ready);
      } else {
        performJoin();
      }

      function performJoin() {
        // Unsubscribe from existing contest subscription if any
        transport.unsubscribeKey(`contest-${currentContestId.value}`);

        // Subscribe to contest-specific topic
        transport.subscribe(
          `contest-${contestId}`,
          `/topic/contest/${contestId}`,
          (message: IMessage) => {
            // Parse message to determine event type
            try {
              const data = JSON.parse(message.body);
              const eventType = data.type || data.event || "contest_update";
              transport.dispatch(eventType, message);
            } catch {
              transport.dispatch("contest_update", message);
            }
          },
        );

        // Send join message to server via STOMP
        transport.publish("/app/contest.join", JSON.stringify({ contestId }));

        currentContestId.value = contestId;

        // Resolve with success response. In STOMP we don't get a direct
        // response, so we assume success.
        resolve({
          success: true,
          contestId,
          message: `Successfully joined contest ${contestId}`,
        });
      }

      // Timeout after 10 seconds; also drop the pending ready hook so a late
      // connect cannot fire a stale join.
      setTimeout(() => {
        if (ready) {
          transport.off("ready", ready);
        }
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

    if (transport.isConnected()) {
      // Unsubscribe from contest topic
      transport.unsubscribeKey(`contest-${contestId}`);

      // Send leave message to server
      transport.publish("/app/contest.leave", JSON.stringify({ contestId }));
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
    transport.on(event, callback as (...args: unknown[]) => void);
    return () => transport.off(event, callback as (...args: unknown[]) => void);
  };

  const onFirstSolve = (
    callback: (data: FirstSolvePayload) => void,
  ): (() => void) => {
    const event = "first_solve";
    transport.on(event, callback as (...args: unknown[]) => void);
    return () => transport.off(event, callback as (...args: unknown[]) => void);
  };

  const onAnnouncement = (
    callback: (data: AnnouncementPayload) => void,
  ): (() => void) => {
    const event = "announcement";
    transport.on(event, callback as (...args: unknown[]) => void);
    return () => transport.off(event, callback as (...args: unknown[]) => void);
  };

  const onContestStatus = (
    callback: (data: ContestStatusPayload) => void,
  ): (() => void) => {
    const event = "contest_status";
    transport.on(event, callback as (...args: unknown[]) => void);
    return () => transport.off(event, callback as (...args: unknown[]) => void);
  };

  const onSubmissionResult = (
    callback: (data: SubmissionResultPayload) => void,
  ): (() => void) => {
    const event = "submission_result";
    transport.on(event, callback as (...args: unknown[]) => void);
    return () => transport.off(event, callback as (...args: unknown[]) => void);
  };

  const onConnectionStatus = (
    callback: (status: ConnectionStatus) => void,
  ): (() => void) => {
    const event = "connection:status";
    transport.on(event, callback as (...args: unknown[]) => void);
    unsubscribers.push(() =>
      transport.off(event, callback as (...args: unknown[]) => void),
    );
    return () => transport.off(event, callback as (...args: unknown[]) => void);
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
