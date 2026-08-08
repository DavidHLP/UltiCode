import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, LOCALE_CONFIGS, type SupportedLocale, type LocaleConfig } from '@/i18n'
import { createUseLocale } from '@/shared/locale-preference/src'

/**
 * Console locale composable — delegates to the shared locale-preference
 * module; console has no backend locale sync, so no onLocaleChange adapter.
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
    t, te, tm, rt, n, d,
  }
}
