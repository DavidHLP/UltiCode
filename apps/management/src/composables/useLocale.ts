import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, LOCALE_CONFIGS, type SupportedLocale, type LocaleConfig } from '@/i18n'
import { createUseLocale } from '@ulticode/locale-preference'

/**
 * UI locale is a browser preference; account preferred_language selects a programming language.
 */
export function useLocale() {
  const { locale, t, te, tm, rt, n, d } = useI18n()

  const shared = createUseLocale<SupportedLocale, LocaleConfig>({
    locale,
    supported: SUPPORTED_LOCALES,
    configs: LOCALE_CONFIGS as Record<SupportedLocale, LocaleConfig>,
  })

  return {
    locale: shared.locale,
    localeConfig: shared.localeConfig,
    availableLocales: shared.availableLocales,
    setLocale: shared.setLocale,
    toggleLocale: shared.toggleLocale,
    isCurrentLocale: shared.isCurrentLocale,
    t,
    te,
    tm,
    rt,
    n,
    d,
  }
}
