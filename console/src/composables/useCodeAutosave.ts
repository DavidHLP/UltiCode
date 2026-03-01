import { ref, onMounted, onBeforeUnmount } from "vue";
import { useDebounceFn } from "@vueuse/core";

export interface AutosaveOptions {
  /** Unique key for the problem (e.g., problem slug or id) */
  problemKey: string;
  /** Language identifier */
  language: string;
  /** Autosave interval in milliseconds (default: 30000 = 30 seconds) - currently not used, kept for API compatibility */
  interval?: number;
  /** Enable or disable autosave (default: true) */
  enabled?: boolean;
  /** Maximum age of saved code in milliseconds (default: 7 days) */
  maxAge?: number;
}

export interface AutosaveStatus {
  /** Whether autosave is currently enabled */
  enabled: boolean;
  /** Whether code was restored from autosave */
  restored: boolean;
  /** Timestamp of last autosave */
  lastSaved: Date | null;
  /** Whether autosave is in progress */
  saving: boolean;
}

const STORAGE_PREFIX = "ulticode_autosave_";
const DEFAULT_INTERVAL = 30000; // 30 seconds
const DEFAULT_MAX_AGE = 7 * 24 * 60 * 60 * 1000; // 7 days

/**
 * Composable for autosaving code to localStorage
 *
 * Features:
 * - Automatic debounced save to localStorage
 * - Restore code on page reload
 * - Cleanup old saves (older than maxAge)
 * - Status tracking for UI feedback
 */
export function useCodeAutosave(options: AutosaveOptions) {
  const {
    problemKey,
    language,
    interval: _interval = DEFAULT_INTERVAL, // eslint-disable-line @typescript-eslint/no-unused-vars
    enabled = true,
    maxAge = DEFAULT_MAX_AGE,
  } = options;

  const status = ref<AutosaveStatus>({
    enabled,
    restored: false,
    lastSaved: null,
    saving: false,
  });

  // Generate storage key for this problem + language combination
  const getStorageKey = () => `${STORAGE_PREFIX}${problemKey}_${language}`;

  // Get metadata key for tracking save time
  const getMetaKey = () => `${STORAGE_PREFIX}${problemKey}_${language}_meta`;

  interface SaveMetadata {
    savedAt: string;
    language: string;
    problemKey: string;
  }

  /**
   * Save code to localStorage with metadata
   */
  const saveCode = (code: string): void => {
    if (typeof window === "undefined" || !enabled) return;

    try {
      const storageKey = getStorageKey();
      const metaKey = getMetaKey();

      const metadata: SaveMetadata = {
        savedAt: new Date().toISOString(),
        language,
        problemKey,
      };

      localStorage.setItem(storageKey, code);
      localStorage.setItem(metaKey, JSON.stringify(metadata));

      status.value.lastSaved = new Date();
      status.value.saving = false;
    } catch (error) {
      console.error("Failed to autosave code:", error);
      // If storage is full, try to clean up old saves
      cleanupOldSaves();
    }
  };

  /**
   * Debounced save function to avoid excessive writes
   */
  const debouncedSave = useDebounceFn(saveCode, 2000); // 2 second debounce

  /**
   * Load saved code from localStorage
   */
  const loadCode = (): string | null => {
    if (typeof window === "undefined") return null;

    try {
      const storageKey = getStorageKey();
      const metaKey = getMetaKey();
      const savedCode = localStorage.getItem(storageKey);
      const metaData = localStorage.getItem(metaKey);

      if (savedCode && metaData) {
        const meta: SaveMetadata = JSON.parse(metaData);
        const savedAt = new Date(meta.savedAt);
        const age = Date.now() - savedAt.getTime();

        // Check if save is too old
        if (age > maxAge) {
          localStorage.removeItem(storageKey);
          localStorage.removeItem(metaKey);
          return null;
        }

        status.value.restored = true;
        status.value.lastSaved = savedAt;
        return savedCode;
      }
    } catch (error) {
      console.error("Failed to load autosaved code:", error);
    }

    return null;
  };

  /**
   * Clear saved code for this problem + language
   */
  const clearSavedCode = (): void => {
    if (typeof window === "undefined") return;

    try {
      const storageKey = getStorageKey();
      const metaKey = getMetaKey();
      localStorage.removeItem(storageKey);
      localStorage.removeItem(metaKey);
      status.value.restored = false;
      status.value.lastSaved = null;
    } catch (error) {
      console.error("Failed to clear autosaved code:", error);
    }
  };

  /**
   * Cleanup old saves across all problems
   */
  const cleanupOldSaves = (): void => {
    if (typeof window === "undefined") return;

    try {
      const keysToRemove: string[] = [];
      const now = Date.now();

      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        if (key?.startsWith(STORAGE_PREFIX) && key.endsWith("_meta")) {
          const metaData = localStorage.getItem(key);
          if (metaData) {
            try {
              const meta: SaveMetadata = JSON.parse(metaData);
              const savedAt = new Date(meta.savedAt);
              const age = now - savedAt.getTime();

              if (age > maxAge) {
                // Remove both the code and metadata
                const codeKey = key.replace("_meta", "");
                keysToRemove.push(key, codeKey);
              }
            } catch {
              // Invalid metadata, remove it
              keysToRemove.push(key);
            }
          }
        }
      }

      keysToRemove.forEach((key) => localStorage.removeItem(key));

      if (keysToRemove.length > 0) {
        console.log(
          `Cleaned up ${keysToRemove.length / 2} old autosave entries`,
        );
      }
    } catch (error) {
      console.error("Failed to cleanup old saves:", error);
    }
  };

  /**
   * Enable or disable autosave
   */
  const setEnabled = (value: boolean): void => {
    status.value.enabled = value;
  };

  // Cleanup old saves on mount
  onMounted(() => {
    cleanupOldSaves();
  });

  // Save before page unload
  const handleBeforeUnload = () => {
    // Immediately save (no debounce) when page is closing
    // This is a synchronous operation for beforeunload
  };

  onMounted(() => {
    if (typeof window !== "undefined") {
      window.addEventListener("beforeunload", handleBeforeUnload);
    }
  });

  onBeforeUnmount(() => {
    if (typeof window !== "undefined") {
      window.removeEventListener("beforeunload", handleBeforeUnload);
    }
  });

  return {
    status,
    saveCode: debouncedSave,
    saveCodeImmediate: saveCode,
    loadCode,
    clearSavedCode,
    setEnabled,
    cleanupOldSaves,
  };
}
