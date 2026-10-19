import { mount, flushPromises } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import AccountView from '../AccountView.vue'
import { accountApi } from '@/api/admin/account'

vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/i18n/utils', () => ({
  formatDateByLocale: () => '-',
  formatDateTimeByLocale: () => '-',
}))
vi.mock('@/api/admin/account', () => ({
  accountApi: {
    getProfile: vi.fn().mockResolvedValue({
      id: 'test',
      username: 'test',
      name: '',
      email: '',
      role: 'ADMIN',
      joined_at: '',
    }),
    updateProfile: vi.fn(),
    changePassword: vi.fn(),
  },
}))

it('returns focus to the password entry when cancelling without writing account data', async () => {
  const wrapper = mount(AccountView, { attachTo: document.body })
  const button = (key: string) => wrapper.findAll('button').find((item) => item.text() === key)!
  try {
    await flushPromises()
    button('account.actions.changePassword').element.focus()
    await button('account.actions.changePassword').trigger('click')
    expect(wrapper.findAll('input[type="password"]')).toHaveLength(3)
    button('account.actions.cancel').element.focus()
    await button('account.actions.cancel').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('input[type="password"]')).toHaveLength(0)
    expect(document.activeElement).toBe(button('account.actions.changePassword').element)
    expect(accountApi.changePassword).not.toHaveBeenCalled()
    expect(accountApi.updateProfile).not.toHaveBeenCalled()
  } finally {
    wrapper.unmount()
  }
})
