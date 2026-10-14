import { computed, ref } from "vue";
import { applyThemeToDOM } from "./applyThemeToDOM";
import { getThemeStorage } from "./storage";
import { THEME_STORAGE_KEY, parseThemeMode, type ThemeMode } from "./ThemeMode";
import {
  APPEARANCE_ACCOUNT_KEY,
  APPEARANCE_OPTIONS,
  DEFAULT_APPEARANCE,
  appearanceStorageKey,
  appearanceWithMode,
  decodeAppearance,
  parseAppearance,
  type Appearance,
  type AppearanceKey,
} from "./appearance-model";

const saved = ref<Appearance>({ ...DEFAULT_APPEARANCE });
const current = ref<Appearance>({ ...DEFAULT_APPEARANCE });
const account = ref<string | number | null>(null);
const previewing = ref(false);
const settingsOpen = ref(false);
const persistenceError = ref(false);
let initialized = false;
let mediaQuery: MediaQueryList | null = null;

export function applyAppearanceToDOM(settings: Appearance): void {
  if (typeof document === "undefined") return;
  const safe = parseAppearance(settings);
  const root = document.documentElement;
  for (const key of Object.keys(APPEARANCE_OPTIONS) as AppearanceKey[]) {
    if (key !== "mode") root.setAttribute(`data-appearance-${key}`, safe[key]);
  }
  root.dataset.ucDensity = safe.density;
  root.dataset.theme = safe.mode;
  applyThemeToDOM(safe.mode);
}

function render(): void {
  applyAppearanceToDOM(current.value);
}
function read(): Appearance {
  const storage = getThemeStorage();
  let payload: string | null = null;
  try {
    payload = storage.getItem(appearanceStorageKey(account.value));
  } catch {
    /* use defaults */
  }
  if (payload !== null) return decodeAppearance(payload);
  const defaults = { ...DEFAULT_APPEARANCE };
  if (account.value === null) {
    try {
      defaults.mode = parseThemeMode(storage.getItem(THEME_STORAGE_KEY));
    } catch {
      /* use system */
    }
  }
  return defaults;
}

function onStorage(event: StorageEvent): void {
  if (event.key !== null && event.key !== appearanceStorageKey(account.value))
    return;
  saved.value = read();
  if (!previewing.value) {
    current.value = { ...saved.value };
    render();
  }
}

export function initAppearance(): void {
  if (initialized) return;
  initialized = true;
  try {
    const hint = getThemeStorage().getItem(APPEARANCE_ACCOUNT_KEY);
    if (hint && /^[a-zA-Z0-9_-]{1,200}$/.test(hint)) account.value = hint;
  } catch {
    /* authenticated identity will select the scope after bootstrap */
  }
  saved.value = read();
  current.value = { ...saved.value };
  render();
  if (typeof window !== "undefined") {
    window.addEventListener("storage", onStorage);
    if (typeof window.matchMedia === "function") {
      mediaQuery = window.matchMedia("(prefers-color-scheme: dark)");
      mediaQuery.addEventListener("change", render);
    }
  }
}

export function setAppearanceAccount(id: string | number | null): void {
  initAppearance();
  if (account.value === id) return;
  previewing.value = false;
  settingsOpen.value = false;
  persistenceError.value = false;
  account.value = id;
  saved.value = read();
  current.value = { ...saved.value };
  try {
    if (id === null) getThemeStorage().removeItem(APPEARANCE_ACCOUNT_KEY);
    else getThemeStorage().setItem(APPEARANCE_ACCOUNT_KEY, String(id));
  } catch {
    /* The marker is only a pre-paint hint, not identity. */
  }
  render();
}

export function beginAppearancePreview(): Appearance {
  initAppearance();
  previewing.value = true;
  persistenceError.value = false;
  current.value = { ...saved.value };
  return { ...saved.value };
}

export function previewAppearance(value: unknown): void {
  if (!previewing.value) return;
  current.value = parseAppearance(value);
  render();
}

export function cancelAppearancePreview(): void {
  previewing.value = false;
  persistenceError.value = false;
  current.value = { ...saved.value };
  render();
}

export function saveAppearance(value: unknown): boolean {
  initAppearance();
  const next = parseAppearance(value);
  const payload = JSON.stringify(next);
  const key = appearanceStorageKey(account.value);
  try {
    const storage = getThemeStorage();
    storage.setItem(key, payload);
    if (storage.persistent === false || storage.getItem(key) !== payload)
      throw new Error("Appearance storage unavailable");
  } catch {
    persistenceError.value = true;
    return false;
  }
  saved.value = next;
  current.value = { ...next };
  previewing.value = false;
  persistenceError.value = false;
  render();
  return true;
}

export function setAppearanceMode(mode: ThemeMode): void {
  initAppearance();
  const next = appearanceWithMode(saved.value, mode);
  if (!saveAppearance(next)) {
    current.value = next;
    render();
  }
  // Keep the previous mode key readable during the storage-format transition.
  if (account.value === null) {
    try {
      getThemeStorage().setItem(THEME_STORAGE_KEY, next.mode);
    } catch {
      /* best effort */
    }
  }
}

export function useAppearance() {
  initAppearance();
  return {
    settings: computed(() => current.value),
    saved: computed(() => saved.value),
    account,
    previewing,
    persistenceError,
    settingsOpen,
    beginPreview: beginAppearancePreview,
    preview: previewAppearance,
    cancel: cancelAppearancePreview,
    apply: saveAppearance,
  };
}

export function resetAppearanceForTest(): void {
  mediaQuery?.removeEventListener("change", render);
  mediaQuery = null;
  if (typeof window !== "undefined")
    window.removeEventListener("storage", onStorage);
  initialized = false;
  settingsOpen.value = false;
  account.value = null;
  previewing.value = false;
  persistenceError.value = false;
  saved.value = { ...DEFAULT_APPEARANCE };
  current.value = { ...DEFAULT_APPEARANCE };
}

export function openAppearanceSettings(): void {
  settingsOpen.value = true;
}
