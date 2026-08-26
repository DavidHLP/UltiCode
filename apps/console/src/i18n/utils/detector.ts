import {
  SUPPORTED_LOCALES,
  DEFAULT_LOCALE,
  type SupportedLocale,
} from "../types";
import {
  detectBrowserLocale as detectSharedBrowserLocale,
  resolveInitialLocale,
} from "@/shared/locale-preference/src";

/**
 * Detect browser locale and match to supported locales
 */
export function detectBrowserLocale(): SupportedLocale | null {
  return detectSharedBrowserLocale(SUPPORTED_LOCALES);
}

/**
 * Get initial locale based on:
 * 1. Stored preference
 * 2. Browser language
 * 3. Default locale
 */
export function getInitialLocale(): SupportedLocale {
  return resolveInitialLocale(SUPPORTED_LOCALES, DEFAULT_LOCALE);
}
