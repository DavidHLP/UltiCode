/**
 * PWA Service Worker Registration
 *
 * Registers the service worker for offline support and handles update prompts.
 * This module is imported in main.ts to enable PWA functionality.
 */

import { registerSW } from "virtual:pwa-register";

// Export the update prompt callback type
export type UpdatePromptCallback = (reload: () => void) => void;

// Store the update callback
let updateCallback: UpdatePromptCallback | null = null;

/**
 * Set the callback to be called when an update is available
 */
export function setUpdateCallback(callback: UpdatePromptCallback): void {
  updateCallback = callback;
}

/**
 * Register the service worker
 * Returns a function to check for updates
 */
export const updateServiceWorker = registerSW({
  immediate: true,
  onNeedRefresh() {
    // Called when a new version is available
    if (updateCallback) {
      updateCallback(() => {
        updateServiceWorker(true); // true = reload the page
      });
    }
  },
  onOfflineReady() {
    // Called when the app is ready to work offline
  },
  onRegistered(swRegistration) {
    // Check for updates every hour
    if (swRegistration) {
      setInterval(
        () => {
          swRegistration.update();
        },
        60 * 60 * 1000,
      );
    }
  },
  onRegisterError(error) {
    console.error("[PWA] Service worker registration error:", error);
  },
});

// Type declaration for virtual module
declare module "virtual:pwa-register" {
  export interface RegisterSWOptions {
    immediate?: boolean;
    onNeedRefresh?: () => void;
    onOfflineReady?: () => void;
    onRegistered?: (
      registration: ServiceWorkerRegistration | undefined,
    ) => void;
    onRegisterError?: (error: Error) => void;
  }

  export function registerSW(
    options?: RegisterSWOptions,
  ): (reloadPage?: boolean) => void;
}
