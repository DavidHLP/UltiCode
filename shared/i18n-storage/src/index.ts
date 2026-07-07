/**
 * Enhanced locale storage with fallback mechanism
 * Supports: localStorage -> sessionStorage -> in-memory storage
 *
 * Extracted from byte-identical copies in console + management (arch review #2).
 * UI notifications (toast) are injected by the consuming app via setStorageNotifier.
 */

export type SupportedLocale = 'en-US' | 'zh-CN'

// ---------------------------------------------------------------------------
// Constants & state
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
// Public API
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
