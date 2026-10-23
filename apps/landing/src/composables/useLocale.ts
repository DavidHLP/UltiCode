import { useI18n } from "vue-i18n";

export function useLocale() {
  const { locale, t } = useI18n();

  function toggleLocale() {
    const next = locale.value === "zh-CN" ? "en-US" : "zh-CN";
    locale.value = next;
    document.documentElement.lang = next;
    try {
      window.localStorage.setItem("ulticode-locale", next);
    } catch {
      // Keep locale switching available when browser storage is blocked.
    }
  }

  return {
    locale,
    t,
    toggleLocale,
  };
}
