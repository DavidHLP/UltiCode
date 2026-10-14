// ---------------------------------------------------------------------------
// theme-bootstrap.js — theme + locale FOUC bootstrap (single source of truth).
//
// Runs in the HTML head before the Vue/Vite bundle to apply the persisted
// theme, eliminating the "white flash" on dark-mode reloads. Plain IIFE
// JavaScript so Vite can serve it as <script src="…"> without a TS toolchain
// in the critical render path.
//
// This file (packages/theme/bootstrap.js) is the CANONICAL SOURCE. The copies at
// console/public/theme-bootstrap.js and management/public/theme-bootstrap.js
// are regenerated from it by `pnpm sync:theme-bootstrap`
// (packages/theme/scripts/sync-theme-bootstrap.mjs); `pnpm verify:theme-sync` fails CI if the
// copies drift from this source. Edit here, then sync — never edit the copies.
//
// Logic mirrors packages/theme/src/applyThemeToDOM.ts (isDarkMode) and reads
// THEME_STORAGE_KEY = 'ulticode-theme' (packages/theme/src/ThemeMode.ts). It
// also seeds the locale marker used by the locale-aware design profiles before
// the Vue bundle loads. Those TS modules own the runtime paths; this owns the
// pre-bundle FOUC path.
// ---------------------------------------------------------------------------
;(function () {
  'use strict'

  var STORAGE_KEY = 'ulticode-theme'
  var LOCALE_STORAGE_KEY = 'ulticode-locale'
  var DEFAULT_LOCALE = 'zh-CN'
  var DARK_QUERY = '(prefers-color-scheme: dark)'
  var OPTIONS = {
    accent: ['neutral', 'blue', 'violet', 'emerald', 'orange'],
    base: ['neutral', 'zinc', 'slate', 'stone', 'gray'],
    chart: ['default', 'contrast'], font: ['system', 'serif', 'mono'],
    radius: ['none', 'small', 'medium', 'large'],
    density: ['comfortable', 'compact'], menu: ['subtle', 'solid'],
    mode: ['light', 'dark', 'system'],
  }
  var DEFAULTS = { accent: 'neutral', base: 'neutral', chart: 'default', font: 'system', radius: 'medium', density: 'comfortable', menu: 'subtle', mode: 'system' }

  function applyAppearance() {
    var payload = null
    var account = null
    try {
      account = localStorage.getItem('ulticode-appearance-account')
      var scope = account && /^[a-zA-Z0-9_-]{1,200}$/.test(account) ? 'user:' + encodeURIComponent(account) : 'guest'
      payload = JSON.parse(localStorage.getItem('ulticode-appearance:v1:' + scope) || 'null')
    } catch { /* safe defaults with blocked or malformed storage */ }
    var settings = Object.assign({}, DEFAULTS)
    if (!account) settings.mode = readStored() || 'system'
    Object.keys(OPTIONS).forEach(function (key) {
      if (payload && Object.prototype.hasOwnProperty.call(payload, key) && OPTIONS[key].indexOf(payload[key]) !== -1) settings[key] = payload[key]
      if (key !== 'mode') document.documentElement.setAttribute('data-appearance-' + key, settings[key])
    })
    document.documentElement.setAttribute('data-uc-density', settings.density)
    document.documentElement.setAttribute('data-theme', settings.mode)
    return settings.mode
  }

  function isDark(mode) {
    if (mode === 'dark') return true
    if (mode === 'light') return false
    try {
      return (
        typeof window !== 'undefined' &&
        typeof window.matchMedia === 'function' &&
        window.matchMedia(DARK_QUERY).matches
      )
    } catch {
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

  function readStoredLocale() {
    try {
      return localStorage.getItem(LOCALE_STORAGE_KEY)
    } catch {
      return null
    }
  }

  function isSupportedLocale(locale) {
    return locale === 'zh-CN' || locale === 'en-US'
  }

  function detectBrowserLocale() {
    try {
      var languages =
        Array.isArray(navigator.languages) && navigator.languages.length
          ? navigator.languages
          : [navigator.language]

      for (var i = 0; i < languages.length; i += 1) {
        var language = languages[i]
        if (typeof language !== 'string') continue
        var normalized = language.toLowerCase()
        if (normalized === 'zh' || normalized.indexOf('zh-') === 0) {
          return 'zh-CN'
        }
        if (normalized === 'en' || normalized.indexOf('en-') === 0) {
          return 'en-US'
        }
      }
    } catch {
      // Browser locale detection is best effort; use the shared default.
    }

    return DEFAULT_LOCALE
  }

  function applyLocale() {
    var stored = readStoredLocale()
    var locale = isSupportedLocale(stored) ? stored : detectBrowserLocale()
    document.documentElement.lang = locale
  }

  function apply() {
    var stored = applyAppearance()
    var html = document.documentElement
    if (isDark(stored)) {
      html.classList.add('dark')
    } else {
      html.classList.remove('dark')
    }
    applyLocale()
  }

  apply()
})()
