// ---------------------------------------------------------------------------
// @ulticode/theme — public API
// ---------------------------------------------------------------------------

// Types & constants
export {
  THEME_CYCLE,
  THEME_MODES,
  THEME_STORAGE_KEY,
  isThemeMode,
  parseThemeMode,
  type ThemeMode,
} from './ThemeMode'

// Storage abstraction (test-friendly)
export { getThemeStorage, setThemeStorage, type ThemeStorage } from './storage'

// Pure DOM helpers (also used by the FOUC bootstrap script)
export { applyThemeToDOM, isDarkMode } from './applyThemeToDOM'

// Typography token metadata + density helper (see typography.css)
export {
  TYPOGRAPHY_DENSITIES,
  TYPOGRAPHY_DENSITY,
  applyTypographyDensity,
  getTypographyDensity,
  typographyCssVariables,
  typographyFoundationPrefixes,
  typographySizes,
  typographyUtilityClasses,
  type TypographyCssVariable,
  type TypographyDensity,
  type TypographySizeToken,
  type TypographyUtilityClass,
} from './typography'

// Vue composable
// Note: `__resetForTest` is intentionally NOT re-exported from the public
// barrel — it is a test-only entry point and consumers should import it
// via the relative path (`from '@ulticode/theme/src/useTheme'`) only from
// __tests__/ files. See useTheme.spec.ts for the canonical import.
export { cycleTheme, initTheme, setTheme, useColorTheme, useTheme } from './useTheme'
