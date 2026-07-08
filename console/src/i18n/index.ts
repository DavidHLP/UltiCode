import { createI18n } from "vue-i18n";
import zhCN from "./locales/zh-CN";
import enUS from "./locales/en-US";
import { getInitialLocale } from "./utils/detector";
import { FALLBACK_LOCALE } from "./types";

// Re-export all types and constants from types.ts (single source of truth)
export {
  SUPPORTED_LOCALES,
  LOCALE_CONFIGS,
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
} from "./types";

// Re-export types separately (required when verbatimModuleSyntax is enabled)
export type { SupportedLocale, LocaleConfig, MessageSchema } from "./types";

// Create i18n instance with simple typing
export const i18n = createI18n({
  legacy: false, // Use Composition API
  globalInjection: true, // Inject $t globally
  locale: getInitialLocale(),
  fallbackLocale: FALLBACK_LOCALE,
  missingWarn: import.meta.env.DEV, // true in development, false in production
  messages: {
    "zh-CN": zhCN,
    "en-US": enUS,
  },
});

export default i18n;
