import { ref } from "vue";

export interface ShortcutHandler {
  key: string;
  ctrl?: boolean;
  shift?: boolean;
  alt?: boolean;
  meta?: boolean;
  handler: () => void;
  description?: string;
  preventDefault?: boolean;
}

const isMac =
  typeof navigator !== "undefined" && /Mac/.test(navigator.platform);

// Global registry for shortcuts
const globalShortcuts = ref<Map<string, ShortcutHandler>>(new Map());
export const isModalOpen = ref(false);

/**
 * Register a global keyboard shortcut
 */
export function registerGlobalShortcut(shortcut: ShortcutHandler): () => void {
  const key = buildShortcutKey(shortcut);
  globalShortcuts.value.set(key, shortcut);

  // Return unregister function
  return () => {
    globalShortcuts.value.delete(key);
  };
}

/**
 * Build a unique key for the shortcut
 */
function buildShortcutKey(shortcut: ShortcutHandler): string {
  const parts: string[] = [];
  if (shortcut.ctrl) parts.push("ctrl");
  if (shortcut.shift) parts.push("shift");
  if (shortcut.alt) parts.push("alt");
  if (shortcut.meta) parts.push("meta");
  parts.push(shortcut.key.toLowerCase());
  return parts.join("+");
}

/**
 * Check if event matches shortcut
 */
function matchesShortcut(
  event: KeyboardEvent,
  shortcut: ShortcutHandler,
): boolean {
  const ctrlKey = isMac ? event.metaKey : event.ctrlKey;

  return (
    event.key.toLowerCase() === shortcut.key.toLowerCase() &&
    ctrlKey === !!shortcut.ctrl &&
    event.shiftKey === !!shortcut.shift &&
    event.altKey === !!shortcut.alt &&
    (isMac ? event.ctrlKey : event.metaKey) === !!shortcut.meta
  );
}

/**
 * Global keyboard event handler
 */
function handleKeyDown(event: KeyboardEvent): void {
  // Don't process if modal is open (except for escape)
  if (isModalOpen.value && event.key !== "Escape") {
    return;
  }

  // Don't process if typing in input/textarea (unless Escape)
  const target = event.target as HTMLElement;
  const isInput =
    target.tagName === "INPUT" ||
    target.tagName === "TEXTAREA" ||
    target.isContentEditable;

  for (const [, shortcut] of globalShortcuts.value) {
    if (matchesShortcut(event, shortcut)) {
      // Allow Escape in inputs
      if (isInput && shortcut.key.toLowerCase() !== "escape") {
        continue;
      }

      if (shortcut.preventDefault !== false) {
        event.preventDefault();
      }
      shortcut.handler();
      return;
    }
  }
}

/**
 * Initialize global keyboard listener
 */
let isInitialized = false;

export function initGlobalShortcuts(): void {
  if (isInitialized || typeof window === "undefined") return;

  window.addEventListener("keydown", handleKeyDown);
  isInitialized = true;
}

// Auto-initialize on mount if in browser
if (typeof window !== "undefined") {
  initGlobalShortcuts();
}
