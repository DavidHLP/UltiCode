import { afterEach, describe, expect, it, vi } from 'vitest'

type StorageState = {
  localStorage: Storage
  documentElement: { lang: string }
}

function createStorage(initial?: string): Storage {
  const values = new Map<string, string>()
  if (initial) values.set('ulticode-locale', initial)

  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value)
    },
    removeItem: (key) => {
      values.delete(key)
    },
    clear: () => values.clear(),
    key: (index) => [...values.keys()][index] ?? null,
    get length() {
      return values.size
    },
  }
}

function installBrowser(languages: string[], stored?: string): StorageState {
  const localStorage = createStorage(stored)
  const documentElement = { lang: '' }

  vi.stubGlobal('window', {
    localStorage,
    sessionStorage: createStorage(),
  })
  vi.stubGlobal('navigator', {
    languages,
    language: languages[0] ?? '',
  })

  return { localStorage, documentElement }
}

async function loadLocalePreference() {
  vi.resetModules()
  return import('./index')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('locale preference lifecycle', () => {
  it('matches the first supported browser language, including regional variants', async () => {
    installBrowser(['fr-FR', 'en-GB'])
    const { detectBrowserLocale } = await loadLocalePreference()

    expect(detectBrowserLocale(['zh-CN', 'en-US'] as const)).toBe('en-US')
  })

  it('resolves stored locale before browser language and fallback', async () => {
    installBrowser(['en-US'], 'zh-CN')
    const { resolveInitialLocale } = await loadLocalePreference()

    expect(resolveInitialLocale(['zh-CN', 'en-US'] as const, 'en-US')).toBe(
      'zh-CN',
    )
  })

  it('uses the shared fallback when browser languages are unsupported', async () => {
    installBrowser(['ja-JP'])
    const { resolveInitialLocale } = await loadLocalePreference()

    expect(resolveInitialLocale(['zh-CN', 'en-US'] as const, 'zh-CN')).toBe(
      'zh-CN',
    )
  })

  it('switches vue state, persistence, and document language together', async () => {
    const environment = installBrowser(['zh-CN'])
    const { createUseLocale } = await loadLocalePreference()
    vi.stubGlobal('document', { documentElement: environment.documentElement })
    const locale = { value: 'zh-CN' }
    const supported = ['zh-CN', 'en-US'] as const

    const api = createUseLocale({
      locale,
      supported,
      configs: {
        'zh-CN': { code: 'zh-CN' },
        'en-US': { code: 'en-US' },
      },
    })

    api.setLocale('en-US')

    expect(locale.value).toBe('en-US')
    expect(environment.documentElement.lang).toBe('en-US')
    expect(environment.localStorage.getItem('ulticode-locale')).toBe('en-US')
  })
})
