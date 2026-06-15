// ---------------------------------------------------------------------------
// Re-export the shared @ulticode/theme composable so existing imports
// (`@/composables/useTheme`) continue to work. The implementation lives in
// shared/theme/src/useTheme.ts — see that file for the singleton design and
// hydration contract.
//
// `useTheme` is kept as a deprecated alias for `useColorTheme` to avoid
// breaking the in-flight diff. New code should call `useColorTheme`.
// ---------------------------------------------------------------------------

export {
  THEME_CYCLE,
  THEME_MODES,
  THEME_STORAGE_KEY,
  applyThemeToDOM,
  cycleTheme,
  initTheme,
  isDarkMode,
  isThemeMode,
  parseThemeMode,
  setTheme,
  useColorTheme,
  /** @deprecated Use `useColorTheme` instead. Will be removed once the migration is complete. */
  useTheme,
  type ThemeMode,
} from "@/shared/theme/src";
