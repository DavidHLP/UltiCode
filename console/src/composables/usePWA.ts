/**
 * PWA Composable
 *
 * Provides reactive state for PWA features including:
 * - Offline readiness status
 * - Update availability detection
 * - Service worker update controls
 */

import { ref } from "vue";
import {
  setUpdateCallback,
  updateServiceWorker as updateSW,
} from "@/pwa-register";

// Global state shared across all instances
const isOfflineReady = ref(false);
const needRefresh = ref(false);
let reloadCallback: (() => void) | null = null;

// Set up the update callback once
let initialized = false;

function initializePWA(): void {
  if (initialized) return;
  initialized = true;

  setUpdateCallback((reload) => {
    needRefresh.value = true;
    reloadCallback = reload;
  });
}

export interface UsePWAReturn {
  /** Whether the app is ready to work offline */
  isOfflineReady: typeof isOfflineReady;
  /** Whether a new version is available */
  needRefresh: typeof needRefresh;
  /** Update the service worker and reload the page */
  updateServiceWorker: () => void;
  /** Dismiss the update prompt */
  close: () => void;
}

/**
 * Composable for PWA functionality
 */
export function usePWA(): UsePWAReturn {
  // Initialize on first use
  initializePWA();

  /**
   * Update the service worker and reload the page
   */
  function handleUpdate(): void {
    if (reloadCallback) {
      reloadCallback();
    } else {
      updateSW(true);
    }
    needRefresh.value = false;
  }

  /**
   * Dismiss the update prompt
   */
  function close(): void {
    needRefresh.value = false;
  }

  return {
    isOfflineReady,
    needRefresh,
    updateServiceWorker: handleUpdate,
    close,
  };
}
