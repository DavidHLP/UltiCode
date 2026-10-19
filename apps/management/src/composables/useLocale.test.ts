import { afterEach, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { useLocale } from './useLocale'

const locale = ref('zh-CN')
const { apiPatch } = vi.hoisted(() => ({ apiPatch: vi.fn().mockResolvedValue({}) }))

vi.mock('vue-i18n', () => ({ useI18n: () => ({ locale }) }))
vi.mock('@/i18n', () => ({
  SUPPORTED_LOCALES: ['zh-CN', 'en-US'],
  LOCALE_CONFIGS: { 'zh-CN': { code: 'zh-CN' }, 'en-US': { code: 'en-US' } },
}))
vi.mock('@/utils/request', () => ({ apiPatch }))

afterEach(() => {
  localStorage.removeItem('ulticode-locale')
  document.documentElement.lang = 'zh-CN'
})

it('switches and persists the UI locale without calling an unsupported profile endpoint', () => {
  useLocale().setLocale('en-US')

  expect(locale.value).toBe('en-US')
  expect(document.documentElement.lang).toBe('en-US')
  expect(localStorage.getItem('ulticode-locale')).toBe('en-US')
  expect(apiPatch).not.toHaveBeenCalled()
})
