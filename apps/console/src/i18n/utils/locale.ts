import { isRef } from "vue";
import i18n, {
  DEFAULT_LOCALE,
  SUPPORTED_LOCALES,
  type SupportedLocale,
} from "@/i18n";

export function getActiveLocale(): SupportedLocale {
  const localeRef = i18n.global.locale;
  const localeValue = isRef(localeRef) ? localeRef.value : localeRef;

  if (SUPPORTED_LOCALES.includes(localeValue as SupportedLocale)) {
    return localeValue as SupportedLocale;
  }

  return DEFAULT_LOCALE;
}
