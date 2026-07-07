/**
 * Shared locale composable — extracts the 95%-identical useLocale logic
 * from console + management (arch review candidate #5).
 */

import { computed, type ComputedRef } from 'vue'
import { setStoredLocale } from '@ulticode/i18n-storage'

export type SupportedLocale = string

export interface UseLocaleDependencies<L extends SupportedLocale, C = unknown> {
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

export interface UseLocaleReturn<L extends SupportedLocale, C = unknown> {
  locale: ComputedRef<L>
  localeConfig: ComputedRef<C | undefined>
  availableLocales: ComputedRef<C[]>
  setLocale: (newLocale: L) => void
  toggleLocale: () => void
  isCurrentLocale: (localeCode: L) => boolean
}

export function createUseLocale<L extends SupportedLocale, C = unknown>(
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
