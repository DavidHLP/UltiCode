import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import {
  APPEARANCE_OPTIONS, APPEARANCE_PRESETS, DEFAULT_APPEARANCE,
  appearanceStorageKey, parseAppearance, decodeAppearance, setThemeStorage,
  setAppearanceAccount, beginAppearancePreview, previewAppearance,
  cancelAppearancePreview, saveAppearance, useAppearance,
} from '../src'
import { resetAppearanceForTest } from '../src/appearance'

let values: Map<string, string>
beforeEach(() => {
  resetAppearanceForTest()
  values = new Map()
  setThemeStorage({ getItem: key => values.get(key) ?? null, setItem: (key, value) => { values.set(key, value) }, removeItem: key => { values.delete(key) } })
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })))
})
afterEach(() => { resetAppearanceForTest(); setThemeStorage(null); vi.unstubAllGlobals() })

describe('controlled appearance', () => {
  it('rejects unknown, inherited, malformed, and executable settings', () => {
    for (const value of [null, [], 'bad', { accent: 'url(javascript:alert(1))', radius: '-999px', mode: '<script>' }, Object.create({ accent: 'blue' })]) {
      expect(parseAppearance(value)).toEqual(DEFAULT_APPEARANCE)
    }
    expect(decodeAppearance('{')).toEqual(DEFAULT_APPEARANCE)
    expect(parseAppearance({ accent: 'blue', css: 'body{display:none}', html: '<img onerror=alert(1)>' })).toEqual({ ...DEFAULT_APPEARANCE, accent: 'blue' })
  })
  it('every preset uses only integrated options', () => {
    for (const preset of Object.values(APPEARANCE_PRESETS)) expect(parseAppearance(preset)).toEqual(preset)
  })
  it('preview changes the whole root without saving; cancel restores it', () => {
    setAppearanceAccount(7)
    const draft = beginAppearancePreview()
    previewAppearance({ ...draft, accent: 'blue', mode: 'dark', density: 'compact' })
    expect(document.documentElement.dataset.appearanceAccent).toBe('blue')
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.documentElement.dataset.ucDensity).toBe('compact')
    expect(values.has(appearanceStorageKey(7))).toBe(false)
    cancelAppearancePreview()
    expect(useAppearance().settings.value).toEqual(DEFAULT_APPEARANCE)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
  it('saves all options and restores after a singleton reload', () => {
    setAppearanceAccount(7)
    const chosen = { accent: 'violet', base: 'slate', chart: 'contrast', font: 'mono', radius: 'large', density: 'compact', menu: 'solid', mode: 'dark' } as const
    expect(saveAppearance(chosen)).toBe(true)
    resetAppearanceForTest()
    setAppearanceAccount(7)
    expect(useAppearance().settings.value).toEqual(chosen)
    for (const [key, value] of Object.entries(chosen)) {
      expect(document.documentElement.getAttribute(key === 'mode' ? 'data-theme' : `data-appearance-${key}`)).toBe(value)
    }
  })
  it('isolates users and guests; changing identity cancels the old draft', () => {
    setAppearanceAccount(7)
    saveAppearance({ ...DEFAULT_APPEARANCE, accent: 'blue' })
    beginAppearancePreview()
    previewAppearance({ ...DEFAULT_APPEARANCE, accent: 'orange' })
    setAppearanceAccount(8)
    expect(useAppearance().settings.value.accent).toBe('neutral')
    expect(useAppearance().previewing.value).toBe(false)
    saveAppearance({ ...DEFAULT_APPEARANCE, accent: 'violet' })
    setAppearanceAccount(null)
    expect(useAppearance().settings.value.accent).toBe('neutral')
    setAppearanceAccount(7)
    expect(useAppearance().settings.value.accent).toBe('blue')
  })
  it('syncs same-user storage changes while preserving an unsaved draft', () => {
    setAppearanceAccount(7)
    values.set(appearanceStorageKey(7), JSON.stringify({ ...DEFAULT_APPEARANCE, accent: 'blue' }))
    window.dispatchEvent(new StorageEvent('storage', { key: appearanceStorageKey(7) }))
    expect(useAppearance().settings.value.accent).toBe('blue')
    beginAppearancePreview()
    previewAppearance({ ...DEFAULT_APPEARANCE, accent: 'orange' })
    values.set(appearanceStorageKey(7), JSON.stringify({ ...DEFAULT_APPEARANCE, accent: 'violet' }))
    window.dispatchEvent(new StorageEvent('storage', { key: appearanceStorageKey(7) }))
    expect(useAppearance().settings.value.accent).toBe('orange')
    cancelAppearancePreview()
    expect(useAppearance().settings.value.accent).toBe('violet')
  })
  it('reports failed persistence without claiming apply succeeded', () => {
    setAppearanceAccount(7)
    beginAppearancePreview()
    setThemeStorage({ getItem: () => null, setItem: () => {}, removeItem: () => {} })
    expect(saveAppearance({ ...DEFAULT_APPEARANCE, accent: 'blue' })).toBe(false)
    expect(useAppearance().persistenceError.value).toBe(true)
    expect(useAppearance().saved.value).toEqual(DEFAULT_APPEARANCE)
    cancelAppearancePreview()
    expect(useAppearance().persistenceError.value).toBe(false)
  })
  it('reset persists defaults only for the current user', () => {
    setAppearanceAccount(7); saveAppearance(APPEARANCE_PRESETS.warm)
    setAppearanceAccount(8); saveAppearance(APPEARANCE_PRESETS.focus)
    saveAppearance(DEFAULT_APPEARANCE)
    expect(decodeAppearance(values.get(appearanceStorageKey(8)) ?? null)).toEqual(DEFAULT_APPEARANCE)
    setAppearanceAccount(7)
    expect(useAppearance().settings.value).toEqual(APPEARANCE_PRESETS.warm)
  })
  it('bootstrap applies the exact runtime allowlist before Vue mounts', () => {
    const source = readFileSync('bootstrap.js', 'utf8')
    const probe = source.replace('  apply()\n', '  globalThis.bootstrapOptions = OPTIONS\n  apply()\n')
    const root = document.documentElement
    values.set('ulticode-appearance-account', '7')
    values.set(appearanceStorageKey(7), JSON.stringify({ ...APPEARANCE_PRESETS.warm, mode: 'dark', font: '<script>' }))
    const context: Record<string, unknown> = { document, navigator: { language: 'en-US' }, localStorage: { getItem: (key: string) => values.get(key) ?? null }, window: { matchMedia: () => ({ matches: false }) } }
    runInNewContext(probe, context)
    expect(JSON.stringify(context.bootstrapOptions)).toBe(JSON.stringify(APPEARANCE_OPTIONS))
    expect(root.dataset.appearanceAccent).toBe('orange')
    expect(root.dataset.appearanceFont).toBe('system')
    expect(root.classList.contains('dark')).toBe(true)
  })
})
