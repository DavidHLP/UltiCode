import { shallowMount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import HelpView from './HelpView.vue'
import zhHelp from '@/i18n/locales/zh-CN/modules/help'
import enHelp from '@/i18n/locales/en-US/modules/help'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ userName: 'admin', userRole: 'ADMIN' }) }))
vi.mock('@/composables/usePermissionMap', () => ({
  usePermissionMap: () => ({ read: { value: true }, moderate: { value: true } }),
}))

describe('help documentation entry', () => {
  it.each([
    { locale: 'zh-CN', help: zhHelp, label: '打开文档' },
    { locale: 'en-US', help: enHelp, label: 'Open documentation' },
  ])('opens the repository documentation map in $locale', ({ locale, help, label }) => {
    const view = shallowMount(HelpView, {
      global: {
        renderStubDefaultSlot: true,
        plugins: [createI18n({ legacy: false, locale, messages: { [locale]: { help } } })],
      },
    })
    try {
      const link = view.get('a')
      expect(link.attributes('href')).toBe(
        'https://github.com/DavidHLP/UltiCode/blob/main/docs/index.md',
      )
      expect(link.text()).toBe(label)
      expect(link.attributes('target')).toBe('_blank')
      expect(link.attributes('rel')).toContain('noopener')
    } finally {
      view.unmount()
    }
  })
})
