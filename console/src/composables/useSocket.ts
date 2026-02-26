import { ref, onMounted, onUnmounted, watch } from "vue";
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

  const onSubmissionResult = (
    callback: (data: SubmissionResultPayload) => void,
  ) => {
    socketManager.on(NotificationEvent.SUBMISSION_RESULT, callback);
    const unsub = () =>
      socketManager.off(NotificationEvent.SUBMISSION_RESULT, callback);
    unsubscribers.push(unsub);
    return unsub;
  };

  const onContestUpdate = (callback: (data: ContestUpdatePayload) => void) => {
    socketManager.on(NotificationEvent.CONTEST_UPDATE, callback);
    const unsub = () =>
      socketManager.off(NotificationEvent.CONTEST_UPDATE, callback);
    unsubscribers.push(unsub);
    return unsub;
  };

  const onBadgeEarned = (callback: (data: BadgeEarnedPayload) => void) => {
    socketManager.on(NotificationEvent.BADGE_EARNED, callback);
    const unsub = () =>
      socketManager.off(NotificationEvent.BADGE_EARNED, callback);
    unsubscribers.push(unsub);
    return unsub;
  };

  const onNotification = (callback: (data: NotificationPayload) => void) => {
    socketManager.on(NotificationEvent.SYSTEM_ANNOUNCEMENT, callback);
    const unsub = () =>
      socketManager.off(NotificationEvent.SYSTEM_ANNOUNCEMENT, callback);
    unsubscribers.push(unsub);
    return unsub;
  };

  const onConnectionStatus = (callback: (status: ConnectionStatus) => void) => {
    socketManager.on("connection:status", callback);
    const unsub = () => socketManager.off("connection:status", callback);
    unsubscribers.push(unsub);
    return unsub;
  };

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

  // Subscribe to connection status changes
  onMounted(() => {
    socketManager.on("connection:status", handleStatusChange);
    // Set initial status
    status.value = socketManager.status;
    isConnected.value = socketManager.status === "connected";
  });

  // Cleanup on unmount
  onUnmounted(() => {
    socketManager.off("connection:status", handleStatusChange);
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
