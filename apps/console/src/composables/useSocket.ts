import { onMounted, onUnmounted, ref, watch } from "vue";
import { useAuthStore } from "@/stores/auth";
import {
  getSocketManager,
  type ConnectionStatus,
  NotificationEvent,
  type SubmissionResultPayload,
  type ContestUpdatePayload,
  type BadgeEarnedPayload,
  type NotificationPayload,
} from "@/lib/socket";
import { bindConnectionStatus } from "@/lib/realtime/lifecycle";

export interface UseSocketOptions {
  autoConnect?: boolean;
}

export interface UseSocketReturn {
  status: ReturnType<typeof ref<ConnectionStatus>>;
  isConnected: ReturnType<typeof ref<boolean>>;
  connect: () => void;
  disconnect: () => void;
  subscribeToContest: (contestId: string) => void;
  unsubscribeFromContest: (contestId: string) => void;
  onSubmissionResult: (
    callback: (data: SubmissionResultPayload) => void,
  ) => () => void;
  onContestUpdate: (
    callback: (data: ContestUpdatePayload) => void,
  ) => () => void;
  onBadgeEarned: (callback: (data: BadgeEarnedPayload) => void) => () => void;
  onNotification: (callback: (data: NotificationPayload) => void) => () => void;
  onConnectionStatus: (
    callback: (status: ConnectionStatus) => void,
  ) => () => void;
}

export function useSocket(options: UseSocketOptions = {}): UseSocketReturn {
  const { autoConnect = true } = options;

  const authStore = useAuthStore();
  const socketManager = getSocketManager();

  const status = ref<ConnectionStatus>("disconnected");
  const isConnected = ref(false);

  const unsubscribers: (() => void)[] = [];

  // Update status when connection status changes
  const handleStatusChange = (newStatus: ConnectionStatus) => {
    status.value = newStatus;
    isConnected.value = newStatus === "connected";
  };

  const connect = () => {
    if (authStore.isAuthenticated) {
      socketManager.connect();
    }
  };

  const disconnect = () => {
    socketManager.disconnect();
  };

  const subscribeToContest = (contestId: string) => {
    socketManager.subscribeToContest(contestId);
  };

  const unsubscribeFromContest = (contestId: string) => {
    socketManager.unsubscribeFromContest(contestId);
  };

  // The five typed event helpers share one bind/unbind ritual so an
  // unmount cleans every listener up.
  function bind<T>(event: NotificationEvent | string, callback: (data: T) => void) {
    socketManager.on(event, callback);
    const unsub = () => socketManager.off(event, callback);
    unsubscribers.push(unsub);
    return unsub;
  }

  const onSubmissionResult = (callback: (data: SubmissionResultPayload) => void) =>
    bind(NotificationEvent.SUBMISSION_RESULT, callback);

  const onContestUpdate = (callback: (data: ContestUpdatePayload) => void) =>
    bind(NotificationEvent.CONTEST_UPDATE, callback);

  const onBadgeEarned = (callback: (data: BadgeEarnedPayload) => void) =>
    bind(NotificationEvent.BADGE_EARNED, callback);

  const onNotification = (callback: (data: NotificationPayload) => void) =>
    bind(NotificationEvent.SYSTEM_ANNOUNCEMENT, callback);

  const onConnectionStatus = (callback: (status: ConnectionStatus) => void) =>
    bind("connection:status", callback);

  let stopConnectionStatus: (() => void) | null = null;
  let stopAuthWatch: (() => void) | null = null;

  // Mount setup
  onMounted(() => {
    // Connection-status subscription: shared with useRealtimeChannel
    // through `bindConnectionStatus` so both composables bind/unbind
    // the same way.
    stopConnectionStatus = bindConnectionStatus(handleStatusChange);
    // Initial status snapshot
    status.value = socketManager.status;
    isConnected.value = socketManager.status === "connected";
    // Watch for authentication changes. `autoConnect` gates the
    // "connect on sign-in" transition; sign-out always disconnects to
    // match the previous inline policy.
    stopAuthWatch = watch(
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
  });

  // Cleanup on unmount
  onUnmounted(() => {
    stopConnectionStatus?.();
    stopConnectionStatus = null;
    stopAuthWatch?.();
    stopAuthWatch = null;
    unsubscribers.forEach((unsub) => unsub());
  });

  return {
    status,
    isConnected,
    connect,
    disconnect,
    subscribeToContest,
    unsubscribeFromContest,
    onSubmissionResult,
    onContestUpdate,
    onBadgeEarned,
    onNotification,
    onConnectionStatus,
  };
}
