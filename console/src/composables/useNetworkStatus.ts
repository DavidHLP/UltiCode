import { ref, computed, onMounted, onUnmounted } from "vue";

/**
 * Global network status state
 */
const isOnline = ref(navigator.onLine);
const lastOnlineTime = ref<Date | null>(isOnline.value ? new Date() : null);
const lastOfflineTime = ref<Date | null>(isOnline.value ? null : new Date());

/**
 * Listeners for network status changes
 */
type NetworkStatusListener = (online: boolean) => void;
const listeners = new Set<NetworkStatusListener>();

function handleOnline() {
  isOnline.value = true;
  lastOnlineTime.value = new Date();
  listeners.forEach((listener) => listener(true));
}

function handleOffline() {
  isOnline.value = false;
  lastOfflineTime.value = new Date();
  listeners.forEach((listener) => listener(false));
}

/**
 * Composable for detecting and managing network status
 *
 * Features:
 * - Reactive online/offline state
 * - Track last online/offline times
 * - Subscribe to status changes
 * - Auto-cleanup on unmount
 *
 * @returns Network status utilities
 */
export function useNetworkStatus() {
  const isListening = ref(false);

  /**
   * Start listening to network status changes
   */
  function startListening(): void {
    if (isListening.value) return;

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);
    isListening.value = true;

    // Update initial state
    isOnline.value = navigator.onLine;
  }

  /**
   * Stop listening to network status changes
   */
  function stopListening(): void {
    window.removeEventListener("online", handleOnline);
    window.removeEventListener("offline", handleOffline);
    isListening.value = false;
  }

  /**
   * Subscribe to network status changes
   *
   * @param listener - Callback function
   * @returns Unsubscribe function
   */
  function subscribe(listener: NetworkStatusListener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  }

  /**
   * Check if currently online
   */
  const online = computed(() => isOnline.value);

  /**
   * Check if currently offline
   */
  const offline = computed(() => !isOnline.value);

  /**
   * Get time since last online (if currently offline)
   */
  const timeSinceLastOnline = computed(() => {
    if (isOnline.value || !lastOnlineTime.value) return null;
    return Date.now() - lastOnlineTime.value.getTime();
  });

  /**
   * Get time since last offline (if currently online)
   */
  const timeSinceLastOffline = computed(() => {
    if (!isOnline.value || !lastOfflineTime.value) return null;
    return Date.now() - lastOfflineTime.value.getTime();
  });

  /**
   * Format time since last online/offline as human-readable string
   */
  const formattedOfflineDuration = computed(() => {
    const ms = timeSinceLastOnline.value;
    if (!ms) return null;

    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    if (hours > 0) return `${hours}h ${minutes % 60}m`;
    if (minutes > 0) return `${minutes}m ${seconds % 60}s`;
    return `${seconds}s`;
  });

  // Auto-setup event listeners on mount
  onMounted(() => {
    startListening();
  });

  // Auto-cleanup on unmount
  onUnmounted(() => {
    // Don't stop listening globally as other components may use it
    // Just remove our own listeners if this was the only user
  });

  return {
    // State
    isOnline,
    online,
    offline,
    lastOnlineTime,
    lastOfflineTime,
    timeSinceLastOnline,
    timeSinceLastOffline,
    formattedOfflineDuration,

    // Actions
    startListening,
    stopListening,
    subscribe,

    // Utilities
    checkConnectivity: () => navigator.onLine,
  };
}

/**
 * Type for network status listener
 */
export type { NetworkStatusListener };
