import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { getActiveLocale, setLocale, type SupportedLocale } from '../i18n'
import { LOCALE_CONFIGS } from '../i18n/types'
import { getSupportedLocales } from '../i18n/utils'

/**
 * Composable for locale management
 * Provides type-safe access to i18n functionality
 */
export function useLocale() {
  const { t } = useI18n()

  // Current locale
  const currentLocale = computed<SupportedLocale>(() => {
    const active = getActiveLocale()
    return active as SupportedLocale
  })

  // Current locale configuration
  const localeConfig = computed(() => {
    const locale = currentLocale.value
    return LOCALE_CONFIGS[locale]
  })

  // All supported locales
  const supportedLocales = computed(() => getSupportedLocales())

  // Check if current locale is RTL
  const isRtl = computed(() => localeConfig.value.dir === 'rtl')

  // Switch locale
  const switchLocale = (newLocale: SupportedLocale) => {
    setLocale(newLocale)
  }

  // Check if a specific locale is active
  const isLocale = (locale: SupportedLocale): boolean => {
    return currentLocale.value === locale
  }

  return {
    // Translation function
    t,

    // Locale state
    currentLocale,
    localeConfig,
    supportedLocales,
    isRtl,

    // Locale actions
    switchLocale,
    isLocale,
  }
}

/**
 * Type-safe translation keys helper
 * Use this to get autocomplete for translation keys
 */
export type TranslationKey = string

/**
 * Create a typed translation function for a specific namespace
 */
export function useNamespacedTranslations(namespace: string) {
  const { t } = useI18n()

  const tt = (key: string, params?: Record<string, unknown>) => {
    return t(`${namespace}.${key}`, params ?? {})
  }

  return { t: tt }
}
