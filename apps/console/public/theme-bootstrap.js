// ---------------------------------------------------------------------------
// theme-bootstrap.js — FOUC bootstrap (single source of truth).
//
// Runs in the HTML head before the Vue/Vite bundle to apply the persisted
// theme, eliminating the "white flash" on dark-mode reloads. Plain IIFE
// JavaScript so Vite can serve it as <script src="…"> without a TS toolchain
// in the critical render path.
//
// This file (packages/theme/bootstrap.js) is the CANONICAL SOURCE. The copies at
// console/public/theme-bootstrap.js and management/public/theme-bootstrap.js
// are regenerated from it by `pnpm sync:theme-bootstrap`
// (scripts/sync-theme-bootstrap.mjs); `pnpm verify:theme-sync` fails CI if the
// copies drift from this source. Edit here, then sync — never edit the copies.
//
// Logic mirrors packages/theme/src/applyThemeToDOM.ts (isDarkMode) and reads
// TEME_STORAGE_KEY = 'ulticode-theme' (packages/theme/src/ThemeMode.ts). Those
// TS modules own the runtime path; this owns the pre-bundle FOUC path.
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
