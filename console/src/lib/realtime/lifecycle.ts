/**
 * Cross-composable realtime lifecycle helpers.
 *
 * `useSocket` and `useRealtimeChannel` both:
 *
 *   1. Subscribe to the {@code "connection:status"} event so their
 *      reactive `isConnected` / `realtimeConnected` ref tracks the
 *      transport, with cleanup on unmount.
 *   2. Watch authentication state and call {@code connect} /
 *      {@code disconnect} on the shared {@code socketManager}, plus
 *      any consumer-supplied cleanup hook (e.g. "reset unread count"
 *      on sign-out).
 *
 * The two composables used to repeat that glue inline; this module
 * owns it so both share one bind/unbind ritual and one
 * auth-gated-connectivity policy.
 *
 * Shared with `architecture-review-20260716` candidate 4 (the
 * realtime-chain deepening) — `useSocket.bind` already collapsed the
 * five `onXxx` helpers; this module collapses the lifecycle glue.
 */
import { watch, type WatchStopHandle } from "vue";
import { getSocketManager, type ConnectionStatus } from "@/lib/socket";

/**
 * Subscribe a handler to socket-manager connection-status events.
 * Returns an unsubscribe function.
 */
export function bindConnectionStatus(
  handler: (status: ConnectionStatus) => void,
): () => void {
  const sm = getSocketManager();
  const wrapped = (raw: string) => handler(raw as ConnectionStatus);
  sm.on("connection:status", wrapped);
  return () => sm.off("connection:status", wrapped);
}

/**
 * Watch a getter that reports authentication state. On the transition
 * to authenticated, {@code socketManager.connect} fires. On the
 * transition to unauthenticated, {@code socketManager.disconnect}
 * fires and the optional {@code onSignedOut} hook runs. {@code immediate:
 * true} ensures the current state is honoured on subscription.
 *
 * Returns the watcher {@code stop} function so callers can wire cleanup
 * alongside their own unsubscribers.
 */
export function watchAuthConnectivity(
  isAuthenticated: () => boolean,
  onSignedOut?: () => void,
): WatchStopHandle {
  const sm = getSocketManager();
  return watch(
    isAuthenticated,
    (isAuth) => {
      if (isAuth) {
        sm.connect();
      } else {
        sm.disconnect();
        if (onSignedOut) {
          onSignedOut();
        }
      }
    },
    { immediate: true },
  );
}
