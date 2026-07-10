/**
 * Shared locale composable — extracts the 95%-identical useLocale logic
 * from console + management.
 *
 * The previous `export type SupportedLocale = string` was a meaningless
 * alias that shadowed the tighter `SupportedLocale = 'en-US' | 'zh-CN'`
 * exported by `@ulticode/i18n-storage`, so the two packages disagreed on
 * what the contract actually was (arch review 2026-07-10, candidate #2).
 * This module is now strictly generic over the locale type the consuming
 * app passes in (`L extends string`); the canonical union lives in
 * `@ulticode/i18n-storage` and is re-exported by each app's `@/i18n` module.
 */

import { computed, type ComputedRef } from 'vue'
import { setStoredLocale } from '@ulticode/i18n-storage'

export interface UseLocaleDependencies<L extends string, C = unknown> {
  /** The vue-i18n locale ref (from useI18n().locale). */
  locale: { value: string }
  /** All locale codes the app supports. */
  supported: readonly L[]
  /** Per-locale metadata for display (label, flag, nativeName, etc.). */
  configs: Record<L, C>
  /**
   * Optional side-effect after a locale change (e.g. management's
   * `apiPatch('/users/me', { locale })` call).
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
