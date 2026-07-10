import { useI18n } from 'vue-i18n'
import { SUPPORTED_LOCALES, LOCALE_CONFIGS, type SupportedLocale, type LocaleConfig } from '@/i18n'
import { createUseLocale } from '@/shared/locale-preference/src'
import { apiPatch } from '@/utils/request'

/**
 * Management locale composable — delegates to the shared locale-preference
 * module. Backend API sync is the one per-app variation, supplied as the
 * onLocaleChange adapter hook.
 */
export function useLocale() {
  const { locale, t, te, tm, rt, n, d } = useI18n()

  const shared = createUseLocale<SupportedLocale, LocaleConfig>({
    locale,
    supported: SUPPORTED_LOCALES,
    configs: LOCALE_CONFIGS as Record<SupportedLocale, LocaleConfig>,
    onLocaleChange: (newLocale) => {
      apiPatch('/users/me', { locale: newLocale }).catch(() => {})
    },
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
