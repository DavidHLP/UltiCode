import { shallowMount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import NavUser from './NavUser.vue'

const { push, logout, hasPermission } = vi.hoisted(() => ({
  push: vi.fn(),
  logout: vi.fn(),
  hasPermission: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ logout, hasPermission }) }))
vi.mock('@ulticode/auth-ui', () => ({
  useAvatar: () => ({ normalizedAvatar: computed(() => '') }),
}))
vi.mock('@ulticode/design-system/ui/sidebar', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useSidebar: () => ({ isMobile: ref(false) }),
}))

const mountMenu = (systemItems: { title: string; url: string; onClick?: () => void }[] = []) =>
  shallowMount(NavUser, {
    props: { user: { name: 'Admin', email: 'admin@example.test' }, systemItems },
    global: {
      plugins: [
        createI18n({ legacy: false, locale: 'en', missingWarn: false, fallbackWarn: false }),
      ],
      renderStubDefaultSlot: true,
    },
  })

beforeEach(() => {
  vi.clearAllMocks()
  hasPermission.mockReturnValue(true)
})

describe('management account menu', () => {
  it('keeps quick actions, account, and notifications at their existing destinations', async () => {
    const view = mountMenu()
    try {
      const item = (label: string) =>
        view
          .findAllComponents({ name: 'DropdownMenuItem' })
          .find((entry) => entry.text() === label)!
      await item('dashboard.quickActions.createProblem').vm.$emit('select')
      expect(push).toHaveBeenLastCalledWith('/problems')
      await item('dashboard.quickActions.createContest').vm.$emit('select')
      expect(push).toHaveBeenLastCalledWith('/contests')
      await item('nav.account').vm.$emit('click')
      expect(push).toHaveBeenLastCalledWith('/account')
      await item('nav.notifications').vm.$emit('click')
      expect(push).toHaveBeenLastCalledWith('/notifications')
      expect(view.findComponent({ name: 'ThemeSwitcher' }).exists()).toBe(true)
      expect(view.findComponent({ name: 'LanguageSwitcher' }).exists()).toBe(true)
    } finally {
      view.unmount()
    }
  })

  it('preserves System navigation and the search callback', async () => {
    const openSearch = vi.fn()
    const view = mountMenu([
      { title: 'Settings', url: '/settings' },
      { title: 'Help', url: '/help' },
      { title: 'Search', url: '/search', onClick: openSearch },
    ])
    try {
      const items = view.findAllComponents({ name: 'DropdownMenuItem' })
      await items.find((item) => item.text() === 'Settings')!.vm.$emit('select')
      expect(push).toHaveBeenLastCalledWith('/settings')
      await items.find((item) => item.text() === 'Help')!.vm.$emit('select')
      expect(push).toHaveBeenLastCalledWith('/help')
      push.mockClear()
      await items.find((item) => item.text() === 'Search')!.vm.$emit('select')
      expect(openSearch).toHaveBeenCalledOnce()
      expect(push).not.toHaveBeenCalled()
    } finally {
      view.unmount()
    }
  })

  it('omits inaccessible quick actions and an empty System group', () => {
    hasPermission.mockReturnValue(false)
    const view = mountMenu()
    try {
      expect(view.text()).not.toContain('dashboard.quickActions')
      expect(view.text()).not.toContain('nav.system')
      expect(view.text()).toContain('nav.account')
    } finally {
      view.unmount()
    }
  })
})
