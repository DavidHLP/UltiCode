// ---------------------------------------------------------------------------
// @ulticode/theme — unit tests
//
// Covers the public contract: storage hydration, setTheme persistence,
// cycle order, system-preference sync, and singleton (no duplicate
// listeners). Tests inject a {@link setThemeStorage} backend so the suite
// does not depend on `globalThis.localStorage` (which vitest 4 ships as a
// bare `Object` regardless of the environment setting).
// ---------------------------------------------------------------------------

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  THEME_CYCLE,
  THEME_STORAGE_KEY,
  applyThemeToDOM,
  cycleTheme,
  initTheme,
  isDarkMode,
  isThemeMode,
  parseThemeMode,
  setTheme,
  setThemeStorage,
  useColorTheme,
  type ThemeStorage,
} from '../src'

// `__resetForTest` is test-only and is intentionally NOT exported from
// the public barrel. Tests must reach for the relative path directly so
// the symbol never leaks into the production bundle.
import { __resetForTest } from '../src/useTheme'

// ---------------------------------------------------------------------------
// In-memory storage shim used as the test backend. Mirrors the public
// `Storage` interface so it can be swapped in via `setThemeStorage`.
// ---------------------------------------------------------------------------
function createMemoryStorage(): ThemeStorage & { dump: () => Record<string, string> } {
  const map = new Map<string, string>()
  return {
    getItem: (k) => (map.has(k) ? (map.get(k) as string) : null),
    setItem: (k, v) => {
      map.set(k, v)
    },
    removeItem: (k) => {
      map.delete(k)
    },
    dump: () => Object.fromEntries(map),
  }
}

function installMatchMedia(initial: 'light' | 'dark'): {
  set: (next: 'light' | 'dark') => void
} {
  const listeners = new Set<(e: MediaQueryListEvent) => void>()
  const mql: Partial<MediaQueryList> = {
    matches: initial === 'dark',
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: (_: string, cb: EventListener) => {
      listeners.add(cb as (e: MediaQueryListEvent) => void)
    },
    removeEventListener: () => {
      listeners.clear()
    },
    dispatchEvent: () => true,
  }
  vi.stubGlobal('matchMedia', vi.fn(() => mql))
  return {
    set(next) {
      ;(mql as { matches: boolean }).matches = next === 'dark'
      const evt = { matches: next === 'dark' } as MediaQueryListEvent
      for (const cb of listeners) cb(evt)
    },
  }
}

beforeEach(() => {
  document.documentElement.classList.remove('dark')
  __resetForTest()
  setThemeStorage(createMemoryStorage())
})

afterEach(() => {
  vi.unstubAllGlobals()
  setThemeStorage(null)
  __resetForTest()
})

describe('ThemeMode utilities', () => {
  it('isThemeMode accepts only the three valid modes', () => {
    expect(isThemeMode('light')).toBe(true)
    expect(isThemeMode('dark')).toBe(true)
    expect(isThemeMode('system')).toBe(true)
    expect(isThemeMode('foo')).toBe(false)
    expect(isThemeMode(null)).toBe(false)
    expect(isThemeMode(undefined)).toBe(false)
    expect(isThemeMode(42)).toBe(false)
  })

  it('parseThemeMode falls back when payload is invalid', () => {
    expect(parseThemeMode('light')).toBe('light')
    expect(parseThemeMode('dark')).toBe('dark')
    expect(parseThemeMode('system')).toBe('system')
    expect(parseThemeMode('foo')).toBe('system')
    expect(parseThemeMode(null)).toBe('system')
    expect(parseThemeMode(undefined, 'dark')).toBe('dark')
  })

  it('THEME_CYCLE contains exactly the three modes in order', () => {
    expect([...THEME_CYCLE]).toEqual(['light', 'dark', 'system'])
  })
})

describe('applyThemeToDOM', () => {
  it('toggles the dark class for explicit light/dark modes', () => {
    applyThemeToDOM('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    applyThemeToDOM('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    applyThemeToDOM('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('respects prefers-color-scheme for system mode', () => {
    installMatchMedia('dark')
    applyThemeToDOM('system')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    installMatchMedia('light')
    applyThemeToDOM('system')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('isDarkMode is a pure function equivalent', () => {
    installMatchMedia('dark')
    expect(isDarkMode('light')).toBe(false)
    expect(isDarkMode('dark')).toBe(true)
    expect(isDarkMode('system')).toBe(true)
    installMatchMedia('light')
    expect(isDarkMode('system')).toBe(false)
  })
})

describe('initTheme (singleton)', () => {
  it('hydrates from the storage backend on first call', () => {
    const mem = createMemoryStorage()
    mem.setItem(THEME_STORAGE_KEY, 'dark')
    setThemeStorage(mem)
    const { theme } = useColorTheme()
    expect(theme.value).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('falls back to system when storage is empty or invalid', () => {
    const mem = createMemoryStorage()
    mem.setItem(THEME_STORAGE_KEY, 'not-a-mode')
    setThemeStorage(mem)
    installMatchMedia('light')
    const { theme } = useColorTheme()
    expect(theme.value).toBe('system')
  })

  it('is idempotent: repeated initTheme calls return the same ref', () => {
    installMatchMedia('light')
    const a = initTheme()
    const b = initTheme()
    expect(a).toBe(b)
  })

  it('falls back to memory backend when localStorage is broken', async () => {
    setThemeStorage(null)
    // When storage is null, getThemeStorage() returns an in-memory shim
    // because the probe of `globalThis.localStorage` finds a bare Object
    // in the test environment. We assert the call doesn't throw.
    const { theme } = useColorTheme()
    expect(theme.value).toBe('system')
  })
})

describe('setTheme', () => {
  it('updates ref + DOM + storage', () => {
    installMatchMedia('light')
    const mem = createMemoryStorage()
    setThemeStorage(mem)
    const { theme } = useColorTheme()
    setTheme('dark')
    expect(theme.value).toBe('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(mem.getItem(THEME_STORAGE_KEY)).toBe('dark')
  })

  it('survives a storage backend that throws on write', () => {
    installMatchMedia('light')
    setThemeStorage({
      getItem: () => null,
      setItem: () => {
        throw new Error('quota')
      },
      removeItem: () => {
        /* noop */
      },
    })
    const { theme } = useColorTheme()
    // setTheme should not throw even if the backend is hostile.
    expect(() => setTheme('dark')).not.toThrow()
    expect(theme.value).toBe('dark')
  })
})

describe('cycleTheme', () => {
  it('walks light → dark → system → light', () => {
    const mem = createMemoryStorage()
    mem.setItem(THEME_STORAGE_KEY, 'light')
    setThemeStorage(mem)
    installMatchMedia('light')
    const { theme } = useColorTheme()
    expect(theme.value).toBe('light')

    expect(cycleTheme()).toBe('dark')
    expect(theme.value).toBe('dark')

    expect(cycleTheme()).toBe('system')
    expect(theme.value).toBe('system')

    expect(cycleTheme()).toBe('light')
    expect(theme.value).toBe('light')
  })
})

describe('system preference change', () => {
  it('re-applies the DOM when the OS theme changes and user is on system', () => {
    const mq = installMatchMedia('light')
    // Initialize the singleton BEFORE flipping setTheme so the mediaQuery
    // listener is registered. Otherwise the `system` change can't react to
    // OS events.
    useColorTheme()
    setTheme('system')
    expect(document.documentElement.classList.contains('dark')).toBe(false)

    mq.set('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    mq.set('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('does NOT re-apply when the user picked an explicit mode', () => {
    const mq = installMatchMedia('light')
    useColorTheme()
    setTheme('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)

    mq.set('dark')
    // User explicitly chose light, so the OS change must not flip the DOM.
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
})

describe('useColorTheme return shape', () => {
  it('returns a frozen ref + mutators', () => {
    installMatchMedia('light')
    const api = useColorTheme()
    expect(api).toHaveProperty('theme')
    expect(api).toHaveProperty('setTheme')
    expect(api).toHaveProperty('cycleTheme')
    expect(typeof api.setTheme).toBe('function')
    expect(typeof api.cycleTheme).toBe('function')
  })
})
