import { computed } from "vue";
import { useI18n } from "vue-i18n";
import {
  SUPPORTED_LOCALES,
  LOCALE_CONFIGS,
  type SupportedLocale,
} from "@/i18n";
import { setStoredLocale } from "@/i18n/utils/storage";

/**
 * Composable for managing application locale
 */
export function useLocale() {
  const { locale, t, te, tm, rt, n, d } = useI18n();

  const currentLocale = computed<SupportedLocale>(
    () => locale.value as SupportedLocale,
  );

  const currentLocaleConfig = computed(
    () => LOCALE_CONFIGS[currentLocale.value],
  );

  const availableLocales = computed(() =>
    SUPPORTED_LOCALES.map((code) => LOCALE_CONFIGS[code]),
  );

  /**
   * Set the application locale
   */
  function setLocale(newLocale: SupportedLocale) {
    if (!SUPPORTED_LOCALES.includes(newLocale)) {
      console.warn(`Unsupported locale: ${newLocale}`);
      return;
    }

    locale.value = newLocale;
    setStoredLocale(newLocale);
    document.documentElement.lang = newLocale;
  }

  /**
   * Toggle between available locales
   */
  function toggleLocale() {
    const currentIndex = SUPPORTED_LOCALES.indexOf(currentLocale.value);
    const nextIndex = (currentIndex + 1) % SUPPORTED_LOCALES.length;
    const nextLocale = SUPPORTED_LOCALES[nextIndex];
    if (nextLocale) {
      setLocale(nextLocale);
    }
  }

  /**
   * Check if a specific locale is the current one
   */
  function isCurrentLocale(localeCode: SupportedLocale) {
    return currentLocale.value === localeCode;
  }

  return {
    // Current state
    locale: currentLocale,
    localeConfig: currentLocaleConfig,
    availableLocales,

    // Actions
    setLocale,
    toggleLocale,
    isCurrentLocale,

    // i18n utilities
    t,
    te, // Translation exists
    tm, // Translation message
    rt, // Resolve translation
    n, // Number formatting
    d, // Date formatting
  };
}
