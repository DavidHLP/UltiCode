import {
  SUPPORTED_LOCALES,
  DEFAULT_LOCALE,
  type SupportedLocale,
} from "../types";
import { getStoredLocale } from "./storage";

/**
 * Detect browser locale and match to supported locales
 */
export function detectBrowserLocale(): SupportedLocale | null {
  if (typeof navigator === "undefined") return null;

  const languages = navigator.languages || [navigator.language];

  for (const lang of languages) {
    // Exact match
    if (SUPPORTED_LOCALES.includes(lang as SupportedLocale)) {
      return lang as SupportedLocale;
    }

    // Language code match (e.g., zh matches zh-CN)
    const langCode = lang.split("-")[0] ?? "";
    for (const supportedLocale of SUPPORTED_LOCALES) {
      if (supportedLocale.startsWith(langCode)) {
        return supportedLocale;
      }
    }
  }

  return null;
}

/**
 * Get initial locale based on:
 * 1. Stored preference
 * 2. Browser language
 * 3. Default locale
 */
export function getInitialLocale(): SupportedLocale {
  // 1. Check stored preference
  const stored = getStoredLocale();
  if (stored && SUPPORTED_LOCALES.includes(stored as SupportedLocale)) {
    return stored as SupportedLocale;
  }

  // 2. Detect browser locale
  const detected = detectBrowserLocale();
  if (detected) {
    return detected;
  }

  // 3. Use default locale
  return DEFAULT_LOCALE;
}
