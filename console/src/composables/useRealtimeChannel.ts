import { ref, watch } from "vue";
import {
  getSocketManager,
  NotificationEvent,
  type NotificationPayload,
} from "@/lib/socket";
import type { NotificationItem } from "@/types/notification";

/**
 * Options for {@link useRealtimeChannel}.
 *
 * The three orthogonal callbacks (auth gate, item sink, signed-out hook)
 * are grouped into one parameter object so they cannot be silently
 * swapped at call sites and so adding a new hook later does not break
 * positional ordering.
 */
export interface UseRealtimeChannelOptions {
  /** Auth gate read each time the auth state changes. */
  isAuthenticated: () => boolean;
  /** Sink invoked for every realtime notification item. */
  onItem: (item: NotificationItem) => void;
  /** Optional cleanup fired when the user signs out. */
  onSignedOut?: () => void;
}

/**
 * Realtime notification channel composable.
 *
 * Owns the WebSocket side effects for notifications: connection-status
 * tracking and the `handleNewNotification` reducer that prepends new
 * realtime events into the feed. The composable returns reactive state
 * + a `handleNewNotification` callback and a `setupRealtimeListeners`
 * function. The store wires these into its existing selectors without
 * taking on any WS lifecycle itself.
 */
export function useRealtimeChannel(
  options: UseRealtimeChannelOptions,
) {
  const { isAuthenticated, onItem, onSignedOut } = options;
  const realtimeConnected = ref(false);
  const isSetup = ref(false);

  function handleNewNotification(payload: NotificationPayload) {
    const newItem: NotificationItem = {
      id: payload.id,
      title: payload.title,
      body: payload.content,
      type: (payload.type?.toUpperCase() ??
        "SYSTEM") as NotificationItem["type"],
      category: "SYSTEM",
      link: payload.link || null,
      isRead: false,
      readAt: null,
      createdAt: payload.createdAt,
    };
    onItem(newItem);
  }

  /**
   * Set up WebSocket listeners exactly once. Subsequent calls are
   * idempotent. The auth check and "reset unread count on logout" hook
   * are passed via the options object on construction so the websocket
   * lifecycle stays inside this composable while auth semantics stay
   * with the store.
   */
  function setupRealtimeListeners(): void {
    if (isSetup.value) {
      return;
    }

    const socketManager = getSocketManager();

    socketManager.on("connection:status", (status: string) => {
      realtimeConnected.value = status === "connected";
    });

    socketManager.on(
      NotificationEvent.SYSTEM_ANNOUNCEMENT,
      handleNewNotification,
    );

    watch(
      () => isAuthenticated(),
      (isAuth) => {
        if (isAuth) {
          socketManager.connect();
        } else {
          socketManager.disconnect();
          if (onSignedOut) {
            onSignedOut();
          }
        }
      },
      { immediate: true },
    );

    isSetup.value = true;
  }

  return {
    realtimeConnected,
    setupRealtimeListeners,
  };
}

export type RealtimeChannel = ReturnType<typeof useRealtimeChannel>;
