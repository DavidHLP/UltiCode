import { mount, flushPromises } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import AccountView from '../AccountView.vue'
import { accountApi, type AccountProfile } from '@/api/admin/account'

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

it.each([
  ['SUPER_ADMIN', 'purple'],
  ['ADMIN', 'info'],
  ['MODERATOR', 'warning'],
  ['USER', 'neutral'],
  ['FUTURE_ROLE', 'neutral'],
  ['', 'neutral'],
  ['admin', 'neutral'],
  ['toString', 'neutral'],
  ['__proto__', 'neutral'],
])('renders role %j with the %s badge color', async (role, color) => {
  vi.mocked(accountApi.getProfile).mockResolvedValueOnce({
    id: 'test',
    username: 'test',
    name: '',
    email: '',
    role,
    joined_at: '',
  })
  const wrapper = mount(AccountView)
  try {
    await flushPromises()
    expect(wrapper.getComponent(SemanticBadge).props()).toMatchObject({ color, label: role })
  } finally {
    wrapper.unmount()
  }
})

it('updates the role badge when a refreshed profile is returned after saving', async () => {
  const profile: AccountProfile = {
    id: 'test',
    username: 'test',
    name: '',
    email: '',
    role: 'ADMIN',
    joined_at: '',
  }
  vi.mocked(accountApi.getProfile).mockResolvedValueOnce(profile)
  vi.mocked(accountApi.updateProfile).mockResolvedValueOnce({ ...profile, role: 'SUPER_ADMIN' })
  const wrapper = mount(AccountView)
  try {
    await flushPromises()
    expect(wrapper.getComponent(SemanticBadge).props('color')).toBe('info')
    await wrapper.find('input').setValue('Updated name')
    const saveButton = wrapper.findAll('button').find((button) => button.text() === 'account.actions.save')!
    await saveButton.trigger('click')
    await flushPromises()
    expect(wrapper.getComponent(SemanticBadge).props()).toMatchObject({
      color: 'purple',
      label: 'SUPER_ADMIN',
    })
  } finally {
    wrapper.unmount()
  }
})
