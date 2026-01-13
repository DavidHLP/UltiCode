import type enUS from './locales/en-US'

export type MessageSchema = typeof enUS

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: SupportedLocale = 'zh-CN'
export const FALLBACK_LOCALE: SupportedLocale = 'zh-CN'

export interface LocaleConfig {
  code: SupportedLocale
  name: string
  nativeName: string
  dir: 'ltr' | 'rtl'
}

export const LOCALE_CONFIGS: Record<SupportedLocale, LocaleConfig> = {
  'zh-CN': {
    code: 'zh-CN',
    name: 'Chinese (Simplified)',
    nativeName: '简体中文',
    dir: 'ltr',
  },
  'en-US': {
    code: 'en-US',
    name: 'English (US)',
    nativeName: 'English',
    dir: 'ltr',
  },
}
