// console/src/composables/contest/useContestSocket.ts
import { ref, onMounted, onUnmounted, watch } from "vue";
import { io, type Socket } from "socket.io-client";
import { useAuthStore } from "@/stores/auth";
import { useRankingStore } from "@/stores/contest/rankingStore";
import { useContestStore } from "@/stores/contest/contestStore";
import type { RankingEntry, ContestAnnouncement } from "@/types/contest";

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
const WS_URL = API_BASE_URL.replace(/^http/, "ws");

// Singleton socket instance for contest namespace
let socketInstance: Socket | null = null;
let connectionStatus: ConnectionStatus = "disconnected";
const eventCallbacks = new Map<string, Set<(...args: unknown[]) => void>>();

/**
 * Get or create the contest socket instance
 */
function getContestSocket(options: Required<UseContestSocketOptions>): Socket {
  if (socketInstance?.connected) {
    return socketInstance;
  }

  // Get JWT token from cookies for authentication
  const getCookie = (name: string): string | null => {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop()?.split(";").shift() || null;
    return null;
  };

  const token = getCookie("access_token");

  connectionStatus = "connecting";
  notifyStatusChange("connecting");

  socketInstance = io(`${WS_URL}/contest`, {
    auth: { token },
    withCredentials: true,
    transports: ["websocket", "polling"],
    reconnection: options.autoReconnect,
    reconnectionAttempts: options.maxReconnectAttempts,
    reconnectionDelay: options.reconnectionDelay,
    reconnectionDelayMax: 5000,
  });

  // Connection events
  socketInstance.on("connect", () => {
    connectionStatus = "connected";
    notifyStatusChange("connected");
  });

  socketInstance.on("disconnect", (reason) => {
    connectionStatus = "disconnected";
    notifyStatusChange("disconnected");
    const callbacks = eventCallbacks.get("disconnect");
    if (callbacks) callbacks.forEach((cb) => cb(reason));
  });

  socketInstance.on("connect_error", (error) => {
    connectionStatus = options.autoReconnect ? "reconnecting" : "disconnected";
    notifyStatusChange(connectionStatus);
    const callbacks = eventCallbacks.get("connect_error");
    if (callbacks) callbacks.forEach((cb) => cb(error));
  });

  // Register contest event listeners
  const contestEvents = [
    "ranking_update",
    "first_solve",
    "announcement",
    "contest_status",
    "submission_result",
  ];

  contestEvents.forEach((event) => {
    socketInstance?.on(event, (data: unknown) => {
      const callbacks = eventCallbacks.get(event);
      if (callbacks) callbacks.forEach((cb) => cb(data));
    });
  });

  return socketInstance;
}

/**
 * Notify all status change listeners
 */
function notifyStatusChange(status: ConnectionStatus): void {
  const callbacks = eventCallbacks.get("connection:status");
  if (callbacks) callbacks.forEach((cb) => cb(status));
}

/**
 * Disconnect and cleanup socket
 */
function disconnectSocket(): void {
  if (socketInstance) {
    socketInstance.disconnect();
    socketInstance = null;
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
 * Connects to the /contest namespace and provides methods to:
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
  const rankingStore = useRankingStore();
  const contestStore = useContestStore();

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
    const socket = getContestSocket(fullOptions);

    return new Promise((resolve, reject) => {
      socket.emit(
        "join_contest",
        contestId,
        (response: ContestRoomResponse) => {
          if (response.success) {
            currentContestId.value = contestId;
            resolve(response);
          } else {
            error.value = response.error || response.message;
            reject(new Error(response.message));
          }
        },
      );

      // Timeout after 10 seconds
      setTimeout(() => {
        reject(new Error("Connection timeout"));
      }, 10000);
    });
  };

  const leaveContest = async (): Promise<ContestRoomResponse> => {
    const socket = getContestSocket(fullOptions);
    const contestId = currentContestId.value;

    if (!contestId) {
      return {
        success: true,
        contestId: "",
        message: "Not in any contest room",
      };
    }

    return new Promise((resolve, reject) => {
      socket.emit(
        "leave_contest",
        contestId,
        (response: ContestRoomResponse) => {
          if (response.success) {
            currentContestId.value = null;
            resolve(response);
          } else {
            error.value = response.error || response.message;
            reject(new Error(response.message));
          }
        },
      );

      // Timeout after 10 seconds
      setTimeout(() => {
        reject(new Error("Disconnection timeout"));
      }, 10000);
    });
  };

  // ==================== Event Subscriptions ====================

  const onRankingUpdate = (
    callback: (data: RankingUpdatePayload) => void,
  ): (() => void) => {
    const wrappedCallback = (data: unknown): void => {
      const payload = data as RankingUpdatePayload;
      // Auto-update ranking store
      if (payload.rankings) {
        rankingStore.updateRankings(payload.rankings);
      }
      callback(payload);
    };

    if (!eventCallbacks.has("ranking_update")) {
      eventCallbacks.set("ranking_update", new Set());
    }
    eventCallbacks.get("ranking_update")!.add(wrappedCallback);

    const unsub = () => {
      eventCallbacks.get("ranking_update")?.delete(wrappedCallback);
    };
    unsubscribers.push(unsub);
    return unsub;
  };

  const onFirstSolve = (
    callback: (data: FirstSolvePayload) => void,
  ): (() => void) => {
    const wrappedCallback = (data: unknown): void => {
      callback(data as FirstSolvePayload);
    };

    if (!eventCallbacks.has("first_solve")) {
      eventCallbacks.set("first_solve", new Set());
    }
    eventCallbacks.get("first_solve")!.add(wrappedCallback);

    const unsub = () => {
      eventCallbacks.get("first_solve")?.delete(wrappedCallback);
    };
    unsubscribers.push(unsub);
    return unsub;
  };

  const onAnnouncement = (
    callback: (data: AnnouncementPayload) => void,
  ): (() => void) => {
    const wrappedCallback = (data: unknown): void => {
      const payload = data as AnnouncementPayload;
      // Auto-add to contest store announcements
      if (payload.contestId === currentContestId.value) {
        const announcement: ContestAnnouncement = {
          id: payload.id,
          contestId: payload.contestId,
          title: payload.title,
          content: payload.content,
          isPinned: false,
          createdAt:
            typeof payload.createdAt === "string"
              ? payload.createdAt
              : new Date(payload.createdAt).toISOString(),
          updatedAt:
            typeof payload.createdAt === "string"
              ? payload.createdAt
              : new Date(payload.createdAt).toISOString(),
        };
        // Update store if we have current announcements
        if (contestStore.currentAnnouncements) {
          contestStore.currentAnnouncements = [
            announcement,
            ...contestStore.currentAnnouncements,
          ];
        }
      }
      callback(payload);
    };

    if (!eventCallbacks.has("announcement")) {
      eventCallbacks.set("announcement", new Set());
    }
    eventCallbacks.get("announcement")!.add(wrappedCallback);

    const unsub = () => {
      eventCallbacks.get("announcement")?.delete(wrappedCallback);
    };
    unsubscribers.push(unsub);
    return unsub;
  };

  const onContestStatus = (
    callback: (data: ContestStatusPayload) => void,
  ): (() => void) => {
    const wrappedCallback = (data: unknown): void => {
      callback(data as ContestStatusPayload);
    };

    if (!eventCallbacks.has("contest_status")) {
      eventCallbacks.set("contest_status", new Set());
    }
    eventCallbacks.get("contest_status")!.add(wrappedCallback);

    const unsub = () => {
      eventCallbacks.get("contest_status")?.delete(wrappedCallback);
    };
    unsubscribers.push(unsub);
    return unsub;
  };

  const onSubmissionResult = (
    callback: (data: SubmissionResultPayload) => void,
  ): (() => void) => {
    const wrappedCallback = (data: unknown): void => {
      callback(data as SubmissionResultPayload);
    };

    if (!eventCallbacks.has("submission_result")) {
      eventCallbacks.set("submission_result", new Set());
    }
    eventCallbacks.get("submission_result")!.add(wrappedCallback);

    const unsub = () => {
      eventCallbacks.get("submission_result")?.delete(wrappedCallback);
    };
    unsubscribers.push(unsub);
    return unsub;
  };

  const onConnectionStatus = (
    callback: (status: ConnectionStatus) => void,
  ): (() => void) => {
    if (!eventCallbacks.has("connection:status")) {
      eventCallbacks.set("connection:status", new Set());
    }
    eventCallbacks.get("connection:status")!.add(callback as () => void);

    const unsub = () => {
      eventCallbacks.get("connection:status")?.delete(callback as () => void);
    };
    unsubscribers.push(unsub);
    return unsub;
  };

  const clearError = (): void => {
    error.value = null;
  };

  // ==================== Lifecycle ====================

  // Watch for authentication changes
  watch(
    () => authStore.isAuthenticated,
    (isAuthenticated) => {
      if (isAuthenticated && autoConnect) {
        connect();
      } else if (!isAuthenticated) {
        disconnect();
      }
    },
    { immediate: true },
  );

  // Subscribe to connection status changes on mount
  onMounted(() => {
    onConnectionStatus(handleStatusChange);
    // Set initial status
    status.value = connectionStatus;
    isConnected.value = connectionStatus === "connected";
  });

  // Cleanup on unmount
  onUnmounted(() => {
    // Remove all callbacks registered by this instance
    unsubscribers.forEach((unsub) => unsub());
    // Note: We don't disconnect the socket on unmount as it's a singleton
    // and may be used by other components
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
