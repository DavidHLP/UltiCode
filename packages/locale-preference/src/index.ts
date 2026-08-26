/**
 * Deep locale-preference module — owns the whole locale-change lifecycle:
 * switch → persist → fallback → notify → DOM language.
 *
 * Collapses the pre-2026-07-10 split where two shallow shared packages
 * jointly owned one lifecycle: {@code @ulticode/i18n-storage} (persist +
 * fallback + toast) and {@code @ulticode/locale-composable} (switch + DOM
 * language, which reached across to call {@code setStoredLocale}). One user
 * action crossed every shallow module, and the switching layer depended on
 * the storage layer via a bare {@code @ulticode/i18n-storage} specifier with
 * no tsconfig mapping — fragile resolution that this merge removes.
 *
 * The app variation that is genuinely per-app (management's backend
 * {@code PUT /users/me} sync) stays an adapter: the app passes an
 * {@link UseLocaleDependencies.onLocaleChange} hook. Everything else —
 * storage layer detection, localized fallback messages, DOM
 * {@code document.documentElement.lang} sync, toggle — lives here.
 *
 * Arch review 2026-07-10, candidate #3 ("Collapse locale preference into
 * one module"). UI toast injection stays via {@link setStorageNotifier}
 * (called once per app bootstrap).
 */

import { computed, type ComputedRef } from 'vue'

// ---------------------------------------------------------------------------
// Public type
// ---------------------------------------------------------------------------

export type SupportedLocale = 'en-US' | 'zh-CN'

// ---------------------------------------------------------------------------
// Storage constants & state
// ---------------------------------------------------------------------------

const LOCALE_STORAGE_KEY = 'ulticode-locale'

enum StorageLayer {
  LocalStorage = 'localStorage',
  SessionStorage = 'sessionStorage',
  Memory = 'memory',
}

const memoryCache = new Map<string, string>()
let activeStorageLayer: StorageLayer = StorageLayer.LocalStorage
let storageInitialized = false
let lastNotificationTime = 0
const NOTIFICATION_DEBOUNCE_MS = 5000

type ToastLevel = 'warning' | 'info' | 'success'
let storageNotifier: ((level: ToastLevel, message: string) => void) | null = null

// ---------------------------------------------------------------------------
// Public: inject toast notifier (called by consuming app)
// ---------------------------------------------------------------------------

/**
 * Inject a UI notifier for storage-layer fallback events.
 * The consuming app calls this once during bootstrap with its toast function.
 * If never called, fallback events are silently ignored (headless / SSR safe).
 */
export function setStorageNotifier(
  fn: ((level: ToastLevel, message: string) => void) | null,
): void {
  storageNotifier = fn
}

// ---------------------------------------------------------------------------
// Storage detection
// ---------------------------------------------------------------------------

function isStorageAccessible(storage: Storage): boolean {
  if (typeof window === 'undefined') return false
  try {
    const testKey = '__storage_test__'
    storage.setItem(testKey, 'test')
    storage.removeItem(testKey)
    return true
  } catch {
    return false
  }
}

function detectBestStorageLayer(): StorageLayer {
  if (typeof window === 'undefined') return StorageLayer.Memory
  if (isStorageAccessible(window.localStorage)) return StorageLayer.LocalStorage
  if (isStorageAccessible(window.sessionStorage)) return StorageLayer.SessionStorage
  return StorageLayer.Memory
}

function getActiveStorage(): Storage | Map<string, string> {
  switch (activeStorageLayer) {
    case StorageLayer.LocalStorage:
      return typeof window !== 'undefined' ? window.localStorage : memoryCache
    case StorageLayer.SessionStorage:
      return typeof window !== 'undefined' ? window.sessionStorage : memoryCache
    case StorageLayer.Memory:
      return memoryCache
    default:
      return memoryCache
  }
}

// ---------------------------------------------------------------------------
// Localized messages
// ---------------------------------------------------------------------------

type StorageMessageKey =
  | 'sessionStorageFallback'
  | 'memoryStorageFallback'
  | 'storageRecovered'

function getLocalizedMessage(key: StorageMessageKey, locale: SupportedLocale): string {
  const messages: Record<StorageMessageKey, Record<SupportedLocale, string>> = {
    sessionStorageFallback: {
      'en-US': 'Language preference will persist during this session only',
      'zh-CN': '语言偏好设置仅在此会话期间保持',
    },
    memoryStorageFallback: {
      'en-US': 'Language preference set for current page only',
      'zh-CN': '语言偏好设置仅在当前页面有效',
    },
    storageRecovered: {
      'en-US': 'Language preference will now persist normally',
      'zh-CN': '语言偏好设置现在可以正常保存',
    },
  }
  return messages[key][locale] || messages[key]['en-US']
}

function notifyStorageChange(from: StorageLayer, to: StorageLayer, locale: SupportedLocale): void {
  const now = Date.now()
  if (now - lastNotificationTime < NOTIFICATION_DEBOUNCE_MS) return
  lastNotificationTime = now

  if (!storageNotifier) return

  if (to === StorageLayer.SessionStorage && from === StorageLayer.LocalStorage) {
    storageNotifier('warning', getLocalizedMessage('sessionStorageFallback', locale))
  } else if (to === StorageLayer.Memory) {
    storageNotifier('info', getLocalizedMessage('memoryStorageFallback', locale))
  } else if (to === StorageLayer.LocalStorage && from !== StorageLayer.LocalStorage) {
    storageNotifier('success', getLocalizedMessage('storageRecovered', locale))
  }
}

function getCurrentLocale(): SupportedLocale {
  if (typeof window === 'undefined') return 'en-US'
  const docLang = document.documentElement.lang
  if (docLang && (docLang === 'en-US' || docLang === 'zh-CN')) {
    return docLang as SupportedLocale
  }
  return 'en-US'
}

// ---------------------------------------------------------------------------
// Initialization
// ---------------------------------------------------------------------------

function initializeStorage(): void {
  if (storageInitialized) return
  if (typeof window === 'undefined') {
    activeStorageLayer = StorageLayer.Memory
    storageInitialized = true
    return
  }
  const bestLayer = detectBestStorageLayer()
  if (bestLayer !== StorageLayer.LocalStorage && activeStorageLayer === StorageLayer.LocalStorage) {
    notifyStorageChange(activeStorageLayer, bestLayer, getCurrentLocale())
  }
  activeStorageLayer = bestLayer
  storageInitialized = true
}

// ---------------------------------------------------------------------------
// Public storage API (also used at app bootstrap to seed vue-i18n)
// ---------------------------------------------------------------------------

export function getStoredLocale(): string | null {
  initializeStorage()
  const storage = getActiveStorage()
  try {
    if (storage instanceof Map) {
      return storage.get(LOCALE_STORAGE_KEY) || null
    } else {
      return storage.getItem(LOCALE_STORAGE_KEY)
    }
  } catch {
    return null
  }
}

export function setStoredLocale(locale: string): void {
  initializeStorage()
  const storage = getActiveStorage()
  const previousLayer = activeStorageLayer
  try {
    if (storage instanceof Map) {
      storage.set(LOCALE_STORAGE_KEY, locale)
    } else {
      storage.setItem(LOCALE_STORAGE_KEY, locale)
    }
  } catch {
    const newLayer = detectBestStorageLayer()
    if (newLayer !== previousLayer) {
      activeStorageLayer = newLayer
      notifyStorageChange(previousLayer, newLayer, getCurrentLocale())
      const fallbackStorage = getActiveStorage()
      try {
        if (fallbackStorage instanceof Map) {
          fallbackStorage.set(LOCALE_STORAGE_KEY, locale)
        } else {
          fallbackStorage.setItem(LOCALE_STORAGE_KEY, locale)
        }
      } catch (retryError) {
        console.error('All storage layers failed:', retryError)
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Initial locale resolution
// ---------------------------------------------------------------------------

/** Match browser language preferences to an application-supported locale. */
export function detectBrowserLocale<L extends string>(
  supported: readonly L[],
): L | null {
  if (typeof navigator === 'undefined') return null

  try {
    const languages = [
      ...(Array.isArray(navigator.languages) ? navigator.languages : []),
      navigator.language,
    ]

    for (const language of languages) {
      if (!language) continue
      const normalized = language.toLowerCase()
      const exact = supported.find((locale) => locale.toLowerCase() === normalized)
      if (exact) return exact

      const languageCode = normalized.split('-')[0]
      const regional = supported.find(
        (locale) => locale.toLowerCase().split('-')[0] === languageCode,
      )
      if (regional) return regional
    }
  } catch {
    // Browser locale detection is best effort; use the app fallback.
  }

  return null
}

/** Resolve the initial locale with one policy shared by both frontends. */
export function resolveInitialLocale<L extends string>(
  supported: readonly L[],
  fallback: L,
): L {
  const stored = getStoredLocale()
  const storedLocale = stored
    ? supported.find((locale) => locale === stored)
    : undefined

  return storedLocale ?? detectBrowserLocale(supported) ?? fallback
}

// ---------------------------------------------------------------------------
// Locale switching (the composable layer, now in the same module as storage)
// ---------------------------------------------------------------------------

export interface UseLocaleDependencies<L extends string, C = unknown> {
  /** The vue-i18n locale ref (from useI18n().locale). */
  locale: { value: string }
  /** All locale codes the app supports. */
  supported: readonly L[]
  /** Per-locale metadata for display (label, nativeName, etc.). */
  configs: Record<L, C>
  /**
   * Optional side-effect after a locale change (e.g. management's
   * `apiPatch('/users/me', { locale })` call). This is the only per-app
   * variation; everything else in the lifecycle lives in this module.
   */
  onLocaleChange?: (locale: L) => void
}

export interface UseLocaleReturn<L extends string, C = unknown> {
  locale: ComputedRef<L>
  localeConfig: ComputedRef<C | undefined>
  availableLocales: ComputedRef<C[]>
  setLocale: (newLocale: L) => void
  toggleLocale: () => void
  isCurrentLocale: (localeCode: L) => boolean
}

export function createUseLocale<L extends string, C = unknown>(
  deps: UseLocaleDependencies<L, C>,
): UseLocaleReturn<L, C> {
  const { locale, supported, configs, onLocaleChange } = deps

  const currentLocale = computed<L>(() => locale.value as L)

  const currentLocaleConfig = computed<C | undefined>(
    () => (configs[currentLocale.value as L] ?? undefined) as C | undefined,
  )

  const availableLocales = computed<C[]>(
    () => supported.map((code) => configs[code] as C),
  )

  function setLocale(newLocale: L): void {
    if (!supported.includes(newLocale)) {
      return
    }
    locale.value = newLocale
    setStoredLocale(newLocale)
    document.documentElement.lang = newLocale
    if (onLocaleChange) {
      try {
        onLocaleChange(newLocale)
      } catch {
        // Side-effect failure should not block locale change
      }
    }
  }

  function toggleLocale(): void {
    const currentIndex = supported.indexOf(currentLocale.value as L)
    const nextIndex = (currentIndex + 1) % supported.length
    const nextLocale = supported[nextIndex]
    if (nextLocale) {
      setLocale(nextLocale)
    }
  }

  function isCurrentLocale(localeCode: L): boolean {
    return currentLocale.value === localeCode
  }

  return {
    locale: currentLocale,
    localeConfig: currentLocaleConfig,
    availableLocales,
    setLocale,
    toggleLocale,
    isCurrentLocale,
  }
}
