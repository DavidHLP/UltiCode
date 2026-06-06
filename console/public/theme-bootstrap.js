// ---------------------------------------------------------------------------
// theme-bootstrap.js — runs in the HTML head to apply the persisted theme
// BEFORE Vue/Vite bundles load. Eliminates the "white flash" on dark-mode
// reloads. Mirrors the logic in shared/theme/src/applyThemeToDOM.ts so the
// FOUC and post-mount paths cannot diverge.
//
// This file is plain ES module JavaScript (no TypeScript) because it lives
// in the static `public/` directory and is served as-is by Vite.
// ---------------------------------------------------------------------------
;(function () {
  'use strict'

  var STORAGE_KEY = 'ulticode-theme'
  var DARK_QUERY = '(prefers-color-scheme: dark)'

  function isDark(mode) {
    if (mode === 'dark') return true
    if (mode === 'light') return false
    try {
      return (
        typeof window !== 'undefined' &&
        typeof window.matchMedia === 'function' &&
        window.matchMedia(DARK_QUERY).matches
      )
      // eslint-disable-next-line @typescript-eslint/no-unused-vars -- intentional: ignore matchMedia throws
    } catch (_) {
      return false
    }
  }

  function readStored() {
    try {
      return localStorage.getItem(STORAGE_KEY)
    } catch (e) {
      console.error('[ulticode/theme] Failed to read theme from localStorage:', e)
      return null
    }
  }

  function apply() {
    var stored = readStored() || 'system'
    var html = document.documentElement
    if (isDark(stored)) {
      html.classList.add('dark')
    } else {
      html.classList.remove('dark')
    }
  }

  apply()
})()
