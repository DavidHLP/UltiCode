/**
 * Enhanced locale storage with fallback mechanism
 * Supports: localStorage → sessionStorage → in-memory storage
 *
 * This module provides robust locale persistence that gracefully degrades
 * when browser storage is unavailable (private mode, quota exceeded, etc.).
 * Users are notified via toast when fallback storage is active.
 */

import { type SupportedLocale } from "../types";

const LOCALE_STORAGE_KEY = "ulticode-locale";

/**
 * Storage layers in order of preference
 */
enum StorageLayer {
  LocalStorage = "localStorage",
  SessionStorage = "sessionStorage",
  Memory = "memory",
}

/**
 * In-memory storage cache (last resort fallback)
 */
const memoryCache = new Map<string, string>();

/**
 * Active storage layer (detected at runtime)
 */
let activeStorageLayer: StorageLayer = StorageLayer.LocalStorage;

/**
 * Whether storage has been initialized
 */
let storageInitialized = false;

/**
 * Last notification timestamp for debouncing (5 second cooldown)
 */
let lastNotificationTime = 0;
const NOTIFICATION_DEBOUNCE_MS = 5000;

/**
 * Dynamic import of toast (SSR-safe, lazy-loaded)
 */
let toastNotifier: typeof import("vue-sonner").toast | null = null;

/**
 * Initialize toast notifier (lazy-loaded, client-side only)
 */
async function initToastNotifier() {
  if (typeof window === "undefined") return;

  if (!toastNotifier) {
    try {
      const { toast } = await import("vue-sonner");
      toastNotifier = toast;
    } catch { // vue-sonner not available - toast notifications disabled
    }
  }
  return toastNotifier;
}

/**
 * Test if a storage type is accessible and writable
 */
function isStorageAccessible(storage: Storage): boolean {
  if (typeof window === "undefined") return false;

  try {
    const testKey = "__storage_test__";
    storage.setItem(testKey, "test");
    storage.removeItem(testKey);
    return true;
  } catch {
    return false;
  }
}

/**
 * Detect the best available storage layer
 * Returns localStorage if available, otherwise sessionStorage, otherwise memory
 */
function detectBestStorageLayer(): StorageLayer {
  if (typeof window === "undefined") {
    return StorageLayer.Memory;
  }

  // Try localStorage first (persistent, preferred)
  if (isStorageAccessible(window.localStorage)) {
    return StorageLayer.LocalStorage;
  }

  // Fall back to sessionStorage (session-based)
  if (isStorageAccessible(window.sessionStorage)) {
    return StorageLayer.SessionStorage;
  }

  // Last resort: in-memory (no persistence)
  return StorageLayer.Memory;
}

/**
 * Get the storage API for the active layer
 */
function getActiveStorage(): Storage | Map<string, string> {
  switch (activeStorageLayer) {
    case StorageLayer.LocalStorage:
      return typeof window !== "undefined" ? window.localStorage : memoryCache;
    case StorageLayer.SessionStorage:
      return typeof window !== "undefined"
        ? window.sessionStorage
        : memoryCache;
    case StorageLayer.Memory:
      return memoryCache;
    default:
      return memoryCache;
  }
}

/**
 * Get localized message for storage notifications
 */
type StorageMessageKey =
  | "localStorageFailed"
  | "sessionStorageFallback"
  | "memoryStorageFallback"
  | "storageRecovered";

function getLocalizedMessage(
  key: StorageMessageKey,
  locale: SupportedLocale,
): string {
  const storageMessages: Record<
    StorageMessageKey,
    Record<SupportedLocale, string>
  > = {
    localStorageFailed: {
      "en-US": "Language preference will not persist after closing browser",
      "zh-CN": "语言偏好设置将在关闭浏览器后不会保存",
    },
    sessionStorageFallback: {
      "en-US": "Language preference will persist during this session only",
      "zh-CN": "语言偏好设置仅在此会话期间保持",
    },
    memoryStorageFallback: {
      "en-US": "Language preference set for current page only",
      "zh-CN": "语言偏好设置仅在当前页面有效",
    },
    storageRecovered: {
      "en-US": "Language preference will now persist normally",
      "zh-CN": "语言偏好设置现在可以正常保存",
    },
  };

  return storageMessages[key][locale] || storageMessages[key]["en-US"];
}

/**
 * Show toast notification about storage layer change (debounced)
 */
async function notifyStorageChange(
  from: StorageLayer,
  to: StorageLayer,
  locale: SupportedLocale,
): Promise<void> {
  const now = Date.now();

  // Debounce: don't notify if we notified recently
  if (now - lastNotificationTime < NOTIFICATION_DEBOUNCE_MS) {
    return;
  }

  lastNotificationTime = now;

  const toast = await initToastNotifier();
  if (!toast) return;

  // Determine notification type and message based on transition
  if (
    to === StorageLayer.SessionStorage &&
    from === StorageLayer.LocalStorage
  ) {
    toast.warning(getLocalizedMessage("sessionStorageFallback", locale));
  } else if (to === StorageLayer.Memory) {
    toast.info(getLocalizedMessage("memoryStorageFallback", locale));
  } else if (
    to === StorageLayer.LocalStorage &&
    from !== StorageLayer.LocalStorage
  ) {
    toast.success(getLocalizedMessage("storageRecovered", locale));
  }
}

/**
 * Get current locale from active storage for notification purposes
 */
function getCurrentLocale(): SupportedLocale {
  if (typeof window === "undefined") {
    return "en-US";
  }

  // Try to read from document lang attribute (set by useLocale composable)
  const docLang = document.documentElement.lang;
  if (docLang && (docLang === "en-US" || docLang === "zh-CN")) {
    return docLang as SupportedLocale;
  }

  return "en-US";
}

/**
 * Initialize storage system (called on first access)
 */
function initializeStorage(): void {
  if (storageInitialized) {
    return;
  }

  if (typeof window === "undefined") {
    activeStorageLayer = StorageLayer.Memory;
    storageInitialized = true;
    return;
  }

  const bestLayer = detectBestStorageLayer();

  // Notify if we're falling back from localStorage
  if (
    bestLayer !== StorageLayer.LocalStorage &&
    activeStorageLayer === StorageLayer.LocalStorage
  ) {
    const locale = getCurrentLocale();
    notifyStorageChange(activeStorageLayer, bestLayer, locale);
  }

  activeStorageLayer = bestLayer;
  storageInitialized = true;
}

/**
 * Get stored locale from the best available storage
 *
 * @returns The stored locale string, or null if not found
 *
 * @example
 * const stored = getStoredLocale();
 * if (stored) {
 * }
 */
export function getStoredLocale(): string | null {
  initializeStorage();

  const storage = getActiveStorage();

  try {
    if (storage instanceof Map) {
      return storage.get(LOCALE_STORAGE_KEY) || null;
    } else {
      return storage.getItem(LOCALE_STORAGE_KEY);
    }
  } catch { return null; }
}

/**
 * Set locale in the best available storage with automatic fallback
 *
 * If the primary storage fails, automatically falls back to the next
 * available storage tier and notifies the user via toast.
 *
 * @param locale - The locale string to store (e.g., "en-US", "zh-CN")
 *
 * @example
 * setStoredLocale('zh-CN'); // Automatically handles fallback
 */
export function setStoredLocale(locale: string): void {
  initializeStorage();

  const storage = getActiveStorage();
  const previousLayer = activeStorageLayer;

  try {
    if (storage instanceof Map) {
      storage.set(LOCALE_STORAGE_KEY, locale);
    } else {
      storage.setItem(LOCALE_STORAGE_KEY, locale);
    }
  } catch { // If current storage failed, try falling back
    // Storage write failed, attempting fallback

    // Force re-detection of storage
    const newLayer = detectBestStorageLayer();
    if (newLayer !== previousLayer) {
      activeStorageLayer = newLayer;

      // Notify user about fallback
      const currentLocale = getCurrentLocale();
      notifyStorageChange(previousLayer, newLayer, currentLocale || "en-US");

      // Retry with new storage
      const fallbackStorage = getActiveStorage();
      try {
        if (fallbackStorage instanceof Map) {
          fallbackStorage.set(LOCALE_STORAGE_KEY, locale);
        } else {
          fallbackStorage.setItem(LOCALE_STORAGE_KEY, locale);
        }
      } catch (retryError) {
        console.error("All storage layers failed:", retryError);
      }
    }
  }
}
