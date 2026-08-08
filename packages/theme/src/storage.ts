// ---------------------------------------------------------------------------
// ThemeStorage — thin wrapper around `localStorage` so the rest of the
// package doesn't have to deal with `try/catch` and the test environment
// can swap a stub in. Defaults to `localStorage` if available, falling
// back to an in-memory shim when running in environments without one.
//
// Error policy: read failures log a warning (degraded read), write
// failures log an error (user's explicit choice was not persisted). This
// matches the strategy in `useTheme.ts` so operators see consistent
// diagnostics whether the failure surfaces in storage or in the setter.
// ---------------------------------------------------------------------------

export interface ThemeStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

class LocalStorageBackend implements ThemeStorage {
  getItem(key: string): string | null {
    try {
      return globalThis.localStorage?.getItem(key) ?? null
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('[ulticode/theme] Failed to read theme from storage:', e)
      return null
    }
  }
  setItem(key: string, value: string): void {
    try {
      globalThis.localStorage?.setItem(key, value)
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error('[ulticode/theme] Failed to persist theme to storage:', e)
    }
  }
  removeItem(key: string): void {
    try {
      globalThis.localStorage?.removeItem(key)
    } catch (e) {
      // eslint-disable-next-line no-console
      console.warn('[ulticode/theme] Failed to remove theme from storage:', e)
    }
  }
}

class MemoryStorageBackend implements ThemeStorage {
  private readonly map = new Map<string, string>()
  getItem(key: string): string | null {
    return this.map.get(key) ?? null
  }
  setItem(key: string, value: string): void {
    this.map.set(key, value)
  }
  removeItem(key: string): void {
    this.map.delete(key)
  }
}

let backend: ThemeStorage | null = null

/** Lazily resolves a working storage backend. */
export function getThemeStorage(): ThemeStorage {
  if (backend) return backend
  try {
    if (typeof globalThis !== 'undefined' && globalThis.localStorage) {
      // Probe the API to make sure it isn't a vitest 4 `Object` stub.
      const ls = globalThis.localStorage as Storage
      if (typeof ls.getItem === 'function' && typeof ls.setItem === 'function') {
        backend = new LocalStorageBackend()
        return backend
      }
    }
  } catch {
    /* fall through — environment without usable localStorage */
  }
  backend = new MemoryStorageBackend()
  return backend
}

/** Inject a custom backend (mainly for tests). */
export function setThemeStorage(instance: ThemeStorage | null): void {
  backend = instance
}
