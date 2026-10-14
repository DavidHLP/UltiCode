import { createI18n } from "vue-i18n";
import enUS from "./i18n/locales/en-US/landing";
import zhCN from "./i18n/locales/zh-CN/landing";

export const SUPPORTED_LOCALES = ["zh-CN", "en-US"] as const;
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

function initialLocale(): SupportedLocale {
  if (typeof window !== "undefined") {
    try {
      const stored = window.localStorage.getItem("ulticode-locale");
      if (SUPPORTED_LOCALES.includes(stored as SupportedLocale)) {
        return stored as SupportedLocale;
      }
    } catch {
      // Use browser language when storage is unavailable.
    }
  }

  const languages =
    typeof navigator === "undefined"
      ? []
      : [
          ...(Array.isArray(navigator.languages) ? navigator.languages : []),
          navigator.language,
        ];
  for (const language of languages) {
    const locale = SUPPORTED_LOCALES.find((supported) =>
      language.toLowerCase().startsWith(supported.slice(0, 2).toLowerCase()),
    );
    if (locale) return locale;
  }
  return "zh-CN";
}

const locale = initialLocale();
if (typeof document !== "undefined") document.documentElement.lang = locale;

export default createI18n({
  legacy: false,
  globalInjection: true,
  locale,
  fallbackLocale: "zh-CN",
  messages: {
    "zh-CN": { landing: zhCN },
    "en-US": { landing: enUS },
  },
});
