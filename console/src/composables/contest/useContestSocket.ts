// console/src/composables/contest/useContestSocket.ts
import { ref, onMounted, onUnmounted, watch } from "vue";
import { useAuthStore } from "@/stores/auth";
import {
  ContestRoom,
  type ConnectionStatus,
  type ContestRoomResponse,
  type RankingUpdatePayload,
  type FirstSolvePayload,
  type AnnouncementPayload,
  type ContestStatusPayload,
  type SubmissionResultPayload,
} from "@/lib/realtime/contest-room";

// Re-export room types so existing imports from this module stay valid.
export type {
  ConnectionStatus,
  ContestRoomResponse,
  RankingUpdatePayload,
  FirstSolvePayload,
  AnnouncementPayload,
  ContestStatusPayload,
  SubmissionResultPayload,
};

/**
 * Options for useContestSocket composable.
 *
 * Only {@link autoConnect} is honoured per-call. Reconnect policy is owned by
 * the deep realtime transport (via {@link ContestRoom}'s contest-channel
 * adapter); the remaining fields are retained for API compatibility.
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

/**
 * Composable for contest WebSocket real-time updates.
 *
 * Thin Vue binding over the deep {@link ContestRoom} room module: it owns the
 * reactive refs and component lifecycle (mount auth-check, unmount teardown)
 * and delegates room semantics — join/leave, the join-during-connect dance,
 * message parsing, and the six typed room events — to the room. Behavior is
 * preserved exactly from the legacy inline composable.
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
 * await joinContest('contest-uuid');
 * onRankingUpdate((data) => { });
 * ```
 */
export function useContestSocket(
  options: UseContestSocketOptions = {},
): UseContestSocketReturn {
  const { autoConnect = true } = options;

  const authStore = useAuthStore();
  const room = new ContestRoom();

  const status = ref<ConnectionStatus>(room.status);
  const isConnected = ref(room.status === "connected");
  const currentContestId = ref<string | null>(null);
  const error = ref<string | null>(null);

  // Auto-teardown list: the internal status subscription and any
  // onConnectionStatus registrations the consumer made. The room's own
  // pending-join cleanup runs via room.dispose() on unmount.
  const unsubscribers: (() => void)[] = [];

  const handleStatusChange = (newStatus: ConnectionStatus): void => {
    status.value = newStatus;
    isConnected.value = newStatus === "connected";
  };

  // ==================== Connection Methods ====================

  const connect = (): void => {
    if (authStore.isAuthenticated) {
      room.connect();
    }
  };

  const disconnect = (): void => {
    room.disconnect();
    currentContestId.value = null;
  };

  // ==================== Room Management ====================

  const joinContest = async (
    contestId: string,
  ): Promise<ContestRoomResponse> => {
    const response = await room.join(contestId);
    currentContestId.value = room.contestId;
    return response;
  };

  const leaveContest = async (): Promise<ContestRoomResponse> => {
    const response = await room.leave();
    currentContestId.value = room.contestId;
    return response;
  };

  // ==================== Event Subscriptions ====================

  const onRankingUpdate = (
    callback: (data: RankingUpdatePayload) => void,
  ): (() => void) => room.onRankingUpdate(callback);

  const onFirstSolve = (
    callback: (data: FirstSolvePayload) => void,
  ): (() => void) => room.onFirstSolve(callback);

  const onAnnouncement = (
    callback: (data: AnnouncementPayload) => void,
  ): (() => void) => room.onAnnouncement(callback);

  const onContestStatus = (
    callback: (data: ContestStatusPayload) => void,
  ): (() => void) => room.onContestStatus(callback);

  const onSubmissionResult = (
    callback: (data: SubmissionResultPayload) => void,
  ): (() => void) => room.onSubmissionResult(callback);

  const onConnectionStatus = (
    callback: (status: ConnectionStatus) => void,
  ): (() => void) => {
    const unsub = room.onConnectionStatus(callback);
    // Match legacy behaviour: connection-status listeners are auto-torn-down
    // on unmount alongside the room's pending-join cleanup.
    unsubscribers.push(unsub);
    return unsub;
  };

  const clearError = (): void => {
    error.value = null;
  };

  // ==================== Lifecycle ====================

  onMounted(() => {
    // Subscribe to connection status changes (auto-tracked).
    onConnectionStatus(handleStatusChange);

    // Auto-connect if enabled and authenticated.
    if (autoConnect && authStore.isAuthenticated) {
      connect();
    }

    // Watch for authentication changes.
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
    room.dispose();
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
