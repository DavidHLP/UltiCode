import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import SiteHeader from './SiteHeader.vue'
import { useSearchPalette } from '@/composables/useSearchPalette'

const { push } = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => ({ path: '/help', meta: { titleKey: 'help.title' } }),
}))
vi.mock('@/components/LanguageSwitcher.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/components/ThemeSwitcher.vue', () => ({ default: { template: '<div />' } }))

const mountHeader = () =>
  shallowMount(SiteHeader, {
    attachTo: document.body,
    global: {
      plugins: [
        createI18n({ legacy: false, locale: 'en', missingWarn: false, fallbackWarn: false }),
      ],
      stubs: { Button: { template: '<button><slot /></button>' } },
    },
  })

beforeEach(() => {
  push.mockClear()
  useSearchPalette().close()
})

describe('header navigation shortcuts', () => {
  it('keeps global search in the header after moving tools into the account menu', () => {
    const view = mountHeader()
    try {
      expect(view.findAll('header button')).toHaveLength(1)
      expect(view.findComponent({ name: 'DropdownMenu' }).exists()).toBe(false)
      expect(view.findComponent({ name: 'ThemeSwitcher' }).exists()).toBe(false)
      expect(view.findComponent({ name: 'LanguageSwitcher' }).exists()).toBe(false)
    } finally {
      view.unmount()
    }
  })
  it.each([
    ['d', '/'],
    ['u', '/users'],
    ['p', '/problems'],
    ['c', '/contests'],
    ['a', '/audit'],
    ['y', '/analytics'],
    ['s', '/settings'],
  ])('handles Ctrl+%s without invoking the browser default', (key, path) => {
    const view = mountHeader()
    try {
      const event = new KeyboardEvent('keydown', {
        key,
        ctrlKey: true,
        bubbles: true,
        cancelable: true,
      })
      view.get('button').element.dispatchEvent(event)
      expect(push).toHaveBeenCalledWith(path)
      expect(event.defaultPrevented).toBe(true)
    } finally {
      view.unmount()
    }
  })

  it('keeps Ctrl+C in an input available for native copying', () => {
    const view = mountHeader()
    const input = document.createElement('input')
    document.body.appendChild(input)
    try {
      const event = new KeyboardEvent('keydown', {
        key: 'c',
        ctrlKey: true,
        bubbles: true,
        cancelable: true,
      })
      input.dispatchEvent(event)
      expect(push).not.toHaveBeenCalled()
      expect(event.defaultPrevented).toBe(false)
    } finally {
      input.remove()
      view.unmount()
    }
  })
})
