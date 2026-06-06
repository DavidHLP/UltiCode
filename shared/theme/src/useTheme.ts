// ---------------------------------------------------------------------------
// useColorTheme — module-singleton color-theme composable shared by all
// UltiCode frontends. Replaces the two duplicated copies in console/ and
// management/. The ref + mediaQueryList live at module scope so every
// component sees the same value and the listener is registered exactly
// once per app instance.
//
// Legacy `useTheme` name is kept as a deprecated alias to avoid breaking
// the in-flight diff; new code should use `useColorTheme`.
// ---------------------------------------------------------------------------

import { ref, type Ref } from 'vue'

import { applyThemeToDOM } from './applyThemeToDOM'
import { getThemeStorage } from './storage'
import {
  THEME_CYCLE,
  THEME_STORAGE_KEY,
  isThemeMode,
  parseThemeMode,
  type ThemeMode,
} from './ThemeMode'

const theme: Ref<ThemeMode> = ref<ThemeMode>('system')
let mediaQueryList: MediaQueryList | null = null
let initialized = false

/**
 * Initialize the singleton. Idempotent: subsequent calls are no-ops so that
 * components calling `useColorTheme()` during `onMounted` don't re-run the
 * hydration work. The `main.ts` early-init path also calls this; whichever
 * runs first wins.
 *
 * The return is the underlying `Ref<ThemeMode>` (NOT a Vue `Readonly<…>`
 * proxy) so consumers can call `theme.value` and templates can auto-unwrap
 * without vue-tsc 3.x type confusion. The "read-only" contract is
 * documented; direct mutation will desync the singleton.
 */
export function initTheme(): Ref<ThemeMode> {
  if (initialized) return theme
  initialized = true

  const storage = getThemeStorage()
  const stored = storage.getItem(THEME_STORAGE_KEY)
  theme.value = parseThemeMode(stored)

  applyThemeToDOM(theme.value)

  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    mediaQueryList = window.matchMedia('(prefers-color-scheme: dark)')
    mediaQueryList.addEventListener('change', handleSystemThemeChange)
  }

  return theme
}

/**
 * Persist + apply a new theme mode.
 *
 * Error policy: the production {@link LocalStorageBackend} swallows its
 * own errors and logs them, so the happy path never throws. Custom /
 * hostile backends (typically tests) may still throw, so we keep a
 * defensive outer try/catch to keep `setTheme` total — losing the
 * persisted preference must not break the in-memory state or the DOM
 * update. We deliberately do NOT log here, because the production
 * backend has already logged and we don't want duplicate diagnostics.
 */
export function setTheme(mode: ThemeMode): void {
  theme.value = mode
  try {
    getThemeStorage().setItem(THEME_STORAGE_KEY, mode)
  } catch {
    /* swallow — see policy above */
  }
  applyThemeToDOM(mode)
}

/** Cycle to the next mode in {@link THEME_CYCLE}. */
export function cycleTheme(): ThemeMode {
  const currentIndex = THEME_CYCLE.indexOf(theme.value)
  const next = THEME_CYCLE[(currentIndex + 1) % THEME_CYCLE.length]
  setTheme(next)
  return next
}

/** Re-apply the `system` preference when the OS-level theme changes. */
function handleSystemThemeChange(): void {
  if (theme.value === 'system') {
    applyThemeToDOM('system')
  }
}

/**
 * Vue composable returning the shared read-only theme ref plus mutators.
 * Calling this in a component does NOT re-initialize the singleton — it
 * just returns the shared state.
 */
export function useColorTheme(): {
  /**
   * Reactive theme mode. Intentionally typed as a plain `Ref<ThemeMode>`
   * (NOT `Readonly<Ref<…>>`) on purpose: vue-tsc 3.x only auto-unwraps
   * `Ref<T>` inside template expressions (`v-if`, function arguments, etc.)
   * and the deep-readonly proxy returned by Vue's `readonly()` defeats
   * that detection. The "read-only" contract is documented here; mutating
   * `theme.value` directly will desync the singleton — call `setTheme`.
   */
  theme: Ref<ThemeMode>
  setTheme: (mode: ThemeMode) => void
  cycleTheme: () => ThemeMode
} {
  if (!initialized) initTheme()
  return {
    theme,
    setTheme,
    cycleTheme,
  }
}

/**
 * Test-only helper: drop the singleton state and detach the system-theme
 * listener. Production code MUST NOT call this — it exists so the test
 * suite can verify the bootstrap path on every spec without manual module
 * re-imports.
 *
 * @internal
 */
export function __resetForTest(): void {
  if (mediaQueryList) {
    mediaQueryList.removeEventListener('change', handleSystemThemeChange)
    mediaQueryList = null
  }
  theme.value = 'system'
  initialized = false
}

/**
 * @deprecated Use {@link useColorTheme} instead. This alias exists for
 * in-flight imports and will be removed once the migration is complete.
 */
export const useTheme = useColorTheme

// Re-export the type guard so consumers can validate arbitrary payloads
// without importing ThemeMode directly.
export { isThemeMode }
