// ---------------------------------------------------------------------------
// ThemeMode — discriminated string-literal union for the three supported
// color themes. Module-scope constants are exported alongside the type so
// consumers (cycle button, dropdown order, persisted preferences) all
// reference the same authority.
// ---------------------------------------------------------------------------

export const THEME_STORAGE_KEY = 'ulticode-theme'

export const THEME_MODES = ['light', 'dark', 'system'] as const

export type ThemeMode = (typeof THEME_MODES)[number]

/** Read-only cycle order used by `AuthThemeToggle.cycleTheme`. */
export const THEME_CYCLE: readonly ThemeMode[] = THEME_MODES

export function isThemeMode(value: unknown): value is ThemeMode {
  return value === 'light' || value === 'dark' || value === 'system'
}

/**
 * Coerce an arbitrary localStorage payload into a valid {@link ThemeMode}.
 * Returns the provided fallback (default: `'system'`) for null / unknown values.
 */
export function parseThemeMode(value: unknown, fallback: ThemeMode = 'system'): ThemeMode {
  return isThemeMode(value) ? value : fallback
}
