import { flushPromises, shallowMount } from '@vue/test-utils'
import { nextTick, reactive, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { moderationQueueApi, type ModerationQueueItem } from '@/api/admin/moderation'
import { toast } from 'vue-sonner'
import ModerationQueueView from './ModerationQueueView.vue'

const route = reactive<{ query: { queueId?: string | string[] } }>({ query: {} })
vi.mock('vue-router', async (original) => ({
  ...(await original<typeof import('vue-router')>()),
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('vue-i18n', async (original) => ({
  ...(await original<typeof import('vue-i18n')>()),
  useI18n: () => ({ t: (key: string) => key }),
}))
vi.mock('vue-sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }))
vi.mock('@/stores/admin/moderation', () => ({
  useModerationStore: () => ({ queue: {}, queueTotal: 0, stats: null, fetchStats: vi.fn() }),
}))
vi.mock('@/components/table/DataTable.vue', async () => {
  const { defineComponent, h } = await import('vue')
  return {
    default: defineComponent({
      name: 'DataTable',
      props: { columns: { type: Array, default: () => [] } },
      setup(props) {
        return () => {
          type TestColumn = {
            id?: string
            cell?: (context: { row: { original: { id: string; entityId: string } } }) => unknown
          }
          const actionColumn = (props.columns as unknown as TestColumn[]).find(
            (column) => column.id === 'actions',
          )
          return actionColumn?.cell?.({
            row: { original: { id: 'mod-queue-006', entityId: 'fpost-007' } },
          }) ?? h('div')
        }
      },
    }),
  }
})
vi.mock('@/components/table/entityActions', async () => {
  const { h } = await import('vue')
  return {
    createEntityActionsMenu: (nodes: Array<{ label?: string; onSelect?: () => void }>) => {
      const viewDetails = nodes.find((node) => node.label === 'moderation.queue.viewDetails')
      return h('div', [
        h('button', { id: 'action-trigger', type: 'button' }, 'Actions'),
        h('div', { role: 'menu', 'aria-labelledby': 'action-trigger' }, [
          h(
            'button',
            {
              role: 'menuitem',
              onClick: (event: MouseEvent) => {
                const target = event.currentTarget as HTMLElement
                target.focus()
                viewDetails?.onSelect?.()
              },
            },
            viewDetails?.label,
          ),
        ]),
      ])
    },
  }
})
vi.mock('@/composables/useRemoteTable', () => ({
  useRemoteTable: () => ({
    query: ref({ filters: { status: 'all', category: 'all', entityType: 'all' } }),
    searchQuery: ref(''),
    tablePagination: ref({ pageIndex: 0, pageSize: 10 }),
    loading: ref(false),
    data: ref([]),
    total: ref(0),
    refresh: vi.fn(),
    setFilters: vi.fn(),
  }),
}))

const item = { id: 'mod-queue-006', entityId: 'fpost-007' } as ModerationQueueItem
const mountView = () => shallowMount(ModerationQueueView)

beforeEach(() => {
  vi.useFakeTimers()
  route.query = {}
})
afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.clearAllMocks()
})

describe('moderation queue deep links', () => {
  it('labels filtered totals separately from global queue statistics', () => {
    const wrapper = mountView()
    try {
      for (const key of ['filteredTotal', 'globalPending', 'globalUnderReview', 'globalResolved']) {
        expect(wrapper.text()).toContain(`moderation.terminal.${key}`)
      }
    } finally {
      wrapper.unmount()
    }
  })

  it('opens the requested item rather than depending on the current table page', async () => {
    route.query.queueId = item.id
    const getItem = vi.spyOn(moderationQueueApi, 'getQueueItem').mockResolvedValue(item)
    const wrapper = mountView()
    try {
      await flushPromises()
      expect(getItem).toHaveBeenCalledWith(item.id, expect.any(AbortSignal))
      expect(wrapper.findComponent({ name: 'BaseDetailDrawer' }).props()).toMatchObject({
        open: true,
        entity: item,
      })
    } finally {
      wrapper.unmount()
    }
  })

  it('encodes path characters from the query as one ID', async () => {
    route.query.queueId = '../other?value=#target'
    const getItem = vi.spyOn(moderationQueueApi, 'getQueueItem').mockResolvedValue(item)
    const wrapper = mountView()
    try {
      await flushPromises()
      expect(getItem).toHaveBeenCalledWith(
        '..%2Fother%3Fvalue%3D%23target',
        expect.any(AbortSignal),
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('keeps a newer target when an old request resolves late', async () => {
    route.query.queueId = 'first'
    let resolveFirst!: (value: ModerationQueueItem) => void
    const getItem = vi
      .spyOn(moderationQueueApi, 'getQueueItem')
      .mockImplementationOnce(
        () =>
          new Promise((done) => {
            resolveFirst = done
          }),
      )
      .mockResolvedValue(item)
    const wrapper = mountView()
    try {
      route.query.queueId = item.id
      await flushPromises()
      expect(getItem.mock.calls[0]?.[1]?.aborted).toBe(true)
      resolveFirst({ ...item, id: 'first' })
      await flushPromises()
      expect(wrapper.findComponent({ name: 'BaseDetailDrawer' }).props('entity').id).toBe(item.id)
    } finally {
      wrapper.unmount()
    }
  })

  it('ignores repeated query values instead of fetching an ambiguous target', async () => {
    route.query.queueId = ['one', 'two']
    const getItem = vi.spyOn(moderationQueueApi, 'getQueueItem')
    const wrapper = mountView()
    try {
      await flushPromises()
      expect(getItem).not.toHaveBeenCalled()
    } finally {
      wrapper.unmount()
    }
  })

  it('cancels stale targets and does not reopen a drawer after unmount', async () => {
    route.query.queueId = 'first'
    let resolve!: (value: ModerationQueueItem) => void
    const getItem = vi.spyOn(moderationQueueApi, 'getQueueItem').mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const wrapper = mountView()
    await flushPromises()
    const signal = getItem.mock.calls[0]?.[1]
    wrapper.unmount()
    resolve(item)
    await flushPromises()
    expect(signal?.aborted).toBe(true)
    expect(toast.error).not.toHaveBeenCalled()
  })

  it('reports unavailable targets without opening a success drawer', async () => {
    route.query.queueId = 'missing'
    vi.spyOn(moderationQueueApi, 'getQueueItem').mockRejectedValue(new Error('Not found'))
    const wrapper = mountView()
    try {
      await flushPromises()
      expect(toast.error).toHaveBeenCalled()
      expect(wrapper.findComponent({ name: 'BaseDetailDrawer' }).props('open')).toBe(false)
    } finally {
      wrapper.unmount()
    }
  })
})

describe('moderation queue drawer focus return', () => {
  it('returns focus to the action trigger after the drawer closes', async () => {
    const wrapper = shallowMount(ModerationQueueView, {
      global: {
        stubs: {
          DataTable: false,
          BaseDetailDrawer: {
            name: 'BaseDetailDrawer',
            props: ['open', 'entity'],
            emits: ['update:open', 'closeAutoFocus'],
            template: '<div />',
          },
        },
      },
      attachTo: document.body,
    })
    try {
      await wrapper.get('[role="menuitem"]').trigger('click')
      const active = document.activeElement as HTMLElement
      expect(active.getAttribute('role')).toBe('menuitem')
      expect(active.closest('[role="menu"]')?.getAttribute('aria-labelledby')).toBe('action-trigger')
      const drawer = wrapper.findComponent({ name: 'BaseDetailDrawer' })
      expect(drawer.props('open')).toBe(true)
      drawer.vm.$emit('update:open', false)
      await nextTick()
      expect(drawer.vm.$.vnode.props?.onCloseAutoFocus).toBeTypeOf('function')

      const event = new Event('closeAutoFocus', { cancelable: true })
      drawer.vm.$emit('closeAutoFocus', event)
      expect(event.defaultPrevented).toBe(true)
      expect(document.activeElement).toBe(wrapper.get('#action-trigger').element)
    } finally {
      wrapper.unmount()
    }
  })
})
