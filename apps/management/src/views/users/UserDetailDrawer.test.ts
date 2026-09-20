import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import UserDetailDrawer from './UserDetailDrawer.vue'

const mocks = vi.hoisted(() => ({
  store: {
    currentUser: {
      id: 'user-a',
      username: 'alice',
      name: 'Alice',
      email: 'alice@example.com',
      role: 'USER',
      isActive: true,
      isBanned: false,
      avatar: undefined as string | undefined,
      joinedAt: '2026-01-01T00:00:00Z',
      lastLoginAt: null,
      stats: null,
      permissions: [],
    },
    fetchUser: vi.fn(),
  },
  uploadAvatar: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
  toastWarning: vi.fn(),
}))

vi.mock('@/stores/admin/users', () => ({
  useUsersStore: () => mocks.store,
}))

vi.mock('@/composables/useAvatarUpload', () => ({
  useAvatarUpload: () => ({
    uploading: false,
    progress: 0,
    upload: mocks.uploadAvatar,
  }),
}))

vi.mock('vue-sonner', () => ({
  toast: {
    success: mocks.toastSuccess,
    error: mocks.toastError,
    warning: mocks.toastWarning,
  },
}))

const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  messages: { 'en-US': {} },
  missingWarn: false,
  fallbackWarn: false,
})

const passthroughStub = { template: '<div><slot /></div>' }
const baseDetailDrawerStub = {
  props: ['entity'],
  template: '<div><slot name="content" :entity="entity" /></div>',
}
const buttonStub = {
  inheritAttrs: false,
  template: '<button v-bind="$attrs"><slot /></button>',
}
type DrawerTestUser = {
  id: string
  username: string
  name: string
  email: string
  role: string
  isActive: boolean
  isBanned: boolean
  avatar: string | undefined
  joinedAt: string
  lastLoginAt: null
  stats: null
  permissions: never[]
}

function makeUser(id: string) {
  return {
    id,
    username: id === 'user-a' ? 'alice' : 'bob',
    name: id === 'user-a' ? 'Alice' : 'Bob',
    email: `${id}@example.com`,
    role: 'USER',
    isActive: true,
    isBanned: false,
    avatar: undefined,
    joinedAt: '2026-01-01T00:00:00Z',
    lastLoginAt: null,
    stats: null,
    permissions: [],
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

function mountDrawer(userId = 'user-a') {
  return mount(UserDetailDrawer, {
    props: { open: true, userId },
    global: {
      plugins: [i18n],
      stubs: {
        BaseDetailDrawer: baseDetailDrawerStub,
        Avatar: passthroughStub,
        AvatarFallback: passthroughStub,
        AvatarImage: passthroughStub,
        Button: buttonStub,
        DataBlock: passthroughStub,
        SemanticBadge: passthroughStub,
        IconMail: true,
        IconTrophy: true,
        IconFlame: true,
        IconUpload: true,
        IconLoader2: true,
      },
    },
  })
}

describe('UserDetailDrawer avatar upload', () => {
  beforeEach(() => {
    mocks.store.currentUser = makeUser('user-a')
    mocks.store.fetchUser.mockReset()
    mocks.uploadAvatar.mockReset()
    mocks.toastSuccess.mockReset()
    mocks.toastError.mockReset()
    mocks.toastWarning.mockReset()
  })

  it('ignores completion when the selected user changes during upload', async () => {
    const pending = deferred<string>()
    mocks.uploadAvatar.mockReturnValue(pending.promise)
    const wrapper = mountDrawer()
    const input = wrapper.find('input[type="file"]')
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [file],
    })

    const change = input.trigger('change')
    await flushPromises()
    await wrapper.setProps({ userId: 'user-b' })

    pending.resolve('/api/users/avatars/user-a/new.png')
    await change
    await flushPromises()

    expect(mocks.store.fetchUser).not.toHaveBeenCalled()
    expect(mocks.toastSuccess).not.toHaveBeenCalled()
    expect(mocks.store.currentUser.avatar).toBeUndefined()
    wrapper.unmount()
  })

  it('does not commit a stale refresh after the drawer switches users', async () => {
    const pending = deferred<DrawerTestUser>()
    mocks.uploadAvatar.mockResolvedValue('/api/users/avatars/user-a/new.png')
    mocks.store.fetchUser.mockImplementation(
      async (id: string, commitCurrent = true) => {
        const user = id === 'user-a' ? await pending.promise : makeUser(id)
        if (commitCurrent) {
          mocks.store.currentUser = user
        }
        return user
      },
    )
    const wrapper = mountDrawer()
    const input = wrapper.find('input[type="file"]')
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', {
      configurable: true,
      value: [file],
    })

    const change = input.trigger('change')
    await flushPromises()
    expect(mocks.store.fetchUser).toHaveBeenCalledWith('user-a', false)

    mocks.store.currentUser = makeUser('user-b')
    await wrapper.setProps({ userId: 'user-b' })
    pending.resolve(makeUser('user-a'))
    await change
    await flushPromises()

    expect(mocks.store.currentUser.id).toBe('user-b')
    wrapper.unmount()
  })

  it('keeps the newest refresh when same-user requests resolve out of order', async () => {
    const older = deferred<DrawerTestUser>()
    const newer = deferred<DrawerTestUser>()
    let request = 0
    mocks.store.fetchUser.mockImplementation(async () => {
      const user = await (request++ === 0 ? older.promise : newer.promise)
      return user
    })
    const wrapper = mountDrawer()

    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    await wrapper.setProps({ open: false })
    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(mocks.store.fetchUser).toHaveBeenCalledTimes(2)

    newer.resolve({ ...makeUser('user-a'), name: 'Newer' })
    await flushPromises()
    expect(mocks.store.currentUser.name).toBe('Newer')

    older.resolve({ ...makeUser('user-a'), name: 'Older' })
    await flushPromises()
    expect(mocks.store.currentUser.name).toBe('Newer')
    wrapper.unmount()
  })
})
