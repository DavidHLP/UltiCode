// ---------------------------------------------------------------------------
// applyThemeToDOM — pure, framework-agnostic function that toggles the
// `dark` class on `<html>` based on the requested mode. The same logic runs
// in three places: the FOUC bootstrap (public/theme-bootstrap.js), the
// `main.ts` early-init call, and the singleton's lazy init. Keeping it pure
// (no Vue, no localStorage) means the same source can be ported to the
// inline-script equivalent without a TypeScript toolchain.
// ---------------------------------------------------------------------------

import type { ThemeMode } from './ThemeMode'

/** Returns whether `<html>` should have the `dark` class for the given mode. */
export function isDarkMode(mode: ThemeMode): boolean {
  if (mode === 'dark') return true
  if (mode === 'light') return false
  // mode === 'system'
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

/**
 * Toggles the `dark` class on `document.documentElement`. Safe to call on
 * the server (no-op) and in browsers without `matchMedia` (treated as light).
 */
export function applyThemeToDOM(mode: ThemeMode): void {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  if (isDarkMode(mode)) {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
}
