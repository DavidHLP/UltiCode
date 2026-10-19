import { flushPromises, shallowMount } from '@vue/test-utils'
import { expect, it, vi } from 'vitest'
import ContestDetailView from './ContestDetailView.vue'

vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => ({ params: { id: 'contest-1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('@/composables/usePermissionMap', () => ({
  usePermissionMap: () => ({ update: false, delete: false }),
}))
vi.mock('@/stores/admin/contests', () => ({
  useContestsStore: () => ({
    currentContest: { title: 'Contest', status: 'FINISHED', contestType: 'ICPC' },
    currentRankings: [],
    fetchContest: vi.fn(),
  }),
}))

it('keeps all four contest tabs in a two-column narrow layout', async () => {
  vi.useFakeTimers()
  const wrapper = shallowMount(ContestDetailView, {
    global: { renderStubDefaultSlot: true },
  })
  try {
    await flushPromises()
    const list = wrapper.findComponent({ name: 'TabsList' })
    expect(list.classes()).toEqual(
      expect.arrayContaining([
        'grid',
        'grid-cols-2',
        'w-full',
        'group-data-horizontal/tabs:h-auto',
        'sm:grid-cols-4',
      ]),
    )
    expect(wrapper.findAllComponents({ name: 'TabsTrigger' })).toHaveLength(4)
  } finally {
    wrapper.unmount()
    vi.clearAllTimers()
    vi.useRealTimers()
  }
})
