import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AuditLogsView from './AuditLogsView.vue'
import { auditApi, type AuditStats } from '@/api/admin/audit'
import auditEn from '@/i18n/locales/en-US/modules/audit'
import auditZh from '@/i18n/locales/zh-CN/modules/audit'
type AuditApiModule = { auditApi: typeof auditApi }

vi.mock('@/api/admin/audit', async () => {
  const actual = await vi.importActual<AuditApiModule>('@/api/admin/audit')
  return {
    ...actual,
    auditApi: {
      ...actual.auditApi,
      getAuditLogs: vi.fn(),
      getAuditStats: vi.fn(),
    },
  }
})
const ResizeObserverStub = vi.hoisted(() => {
  class Stub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  vi.stubGlobal('ResizeObserver', Stub)
  return Stub
})

const emptyPage = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 50,
  totalPages: 0,
}
const stats = {
  totalActions: 0,
  actionsByEntity: [],
  topPerformers: [],
  actionsByType: [],
}

const ButtonStub = {
  inheritAttrs: false,
  emits: ['click'],
  template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>',
}
const SlotStub = { template: '<div><slot /></div>' }
const InputStub = { inheritAttrs: false, template: '<input v-bind="$attrs" />' }
const SelectStub = {
  inheritAttrs: false,
  props: {
    modelValue: {
      type: String,
      default: 'all',
    },
  },
  emits: ['update:modelValue'],
  template: `
    <select
      v-bind="$attrs"
      :value="modelValue"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <option value="all">all</option>
      <option value="CREATE_USER">CREATE_USER</option>
      <option value="PROBLEM">PROBLEM</option>
    </select>
    <slot />
  `,
}

const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'en-US': {}, 'zh-CN': {} },
})

function mountAuditLogsView(useNativeInput = false) {
  return mount(AuditLogsView, {
    global: {
      plugins: [i18n],
      stubs: {
        Button: ButtonStub,
        Input: useNativeInput ? false : InputStub,
        DataTable: { template: '<div><slot name="toolbar-left" /></div>' },
        AuditLogDetailDrawer: SlotStub,
        Select: SelectStub,
        SelectContent: SlotStub,
        SelectGroup: SlotStub,
        SelectItem: SlotStub,
        SelectLabel: SlotStub,
        SelectTrigger: SlotStub,
        SelectValue: { template: '<span data-test="selected-filter-label"><slot /></span>' },
        IconChevronDown: true,
        IconChevronUp: true,
        IconDatabase: true,
        IconInfoCircle: true,
        IconRefresh: true,
        IconSearch: true,
        IconX: true,
      },
    },
  })
}

describe('AuditLogsView refresh contract', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub)
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(auditApi.getAuditLogs).mockResolvedValue(emptyPage)
    vi.mocked(auditApi.getAuditStats).mockResolvedValue(stats)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('bounds its custom search toolbar to the available card width', async () => {
    const wrapper = mountAuditLogsView()
    try {
      await flushPromises()
      const search = wrapper.find('input').element.parentElement!
      expect(search.classList.contains('min-w-0')).toBe(true)
      expect(search.classList.contains('max-w-[260px]')).toBe(true)
      expect(search.parentElement!.classList.contains('max-w-full')).toBe(true)
    } finally {
      wrapper.unmount()
    }
  })

  it('removes both date bounds from logs and statistics after native inputs are cleared', async () => {
    vi.useFakeTimers()
    const wrapper = mountAuditLogsView(true)
    try {
      await flushPromises()
      await wrapper
        .findAll('button')
        .find((button) => button.text().includes('audit.filters.advancedFilters'))
        ?.trigger('click')
      const dates = wrapper.findAll('input[type="date"]')
      expect(dates).toHaveLength(2)
      await dates[0]?.setValue('2026-05-30')
      await dates[1]?.setValue('2026-05-30')
      await vi.advanceTimersByTimeAsync(500)
      await flushPromises()
      expect(vi.mocked(auditApi.getAuditLogs).mock.lastCall?.[0]).toMatchObject({
        startDate: '2026-05-30T00:00:00',
        endDate: '2026-05-31T00:00:00',
      })

      await dates[0]?.setValue('')
      await dates[1]?.setValue('')
      await vi.advanceTimersByTimeAsync(500)
      await flushPromises()
      expect(vi.mocked(auditApi.getAuditLogs).mock.lastCall?.[0]).toMatchObject({
        startDate: undefined,
        endDate: undefined,
      })
      expect(vi.mocked(auditApi.getAuditStats).mock.lastCall?.[0]).toMatchObject({
        startDate: undefined,
        endDate: undefined,
      })
    } finally {
      wrapper.unmount()
    }
  })

  it('updates selected filter labels and accessible names when the locale changes', async () => {
    i18n.global.setLocaleMessage('en-US', { audit: auditEn })
    i18n.global.setLocaleMessage('zh-CN', { audit: auditZh })
    const wrapper = mountAuditLogsView()
    try {
      await flushPromises()
      const labels = () =>
        wrapper.findAll('[data-test="selected-filter-label"]').map((node) => node.text())
      expect(labels()).toEqual(['All Actions', 'All Entities'])
      await wrapper.findAll('select')[0]?.setValue('CREATE_USER')
      await wrapper.findAll('select')[1]?.setValue('PROBLEM')
      expect(labels()).toEqual([auditEn.actionTypes.CREATE_USER, auditEn.entityTypes.PROBLEM])

      i18n.global.locale.value = 'zh-CN'
      await flushPromises()
      expect(labels()).toEqual([auditZh.actionTypes.CREATE_USER, auditZh.entityTypes.PROBLEM])
      expect(wrapper.find('[aria-label="操作"]').exists()).toBe(true)
      expect(wrapper.find('[aria-label="实体类型"]').exists()).toBe(true)
      await wrapper
        .findAll('button')
        .find((button) => button.text().includes(auditZh.filters.advancedFilters))
        ?.trigger('click')
      expect(wrapper.find('input[aria-label="开始日期"]').exists()).toBe(true)
      expect(wrapper.find('input[aria-label="结束日期"]').exists()).toBe(true)
    } finally {
      wrapper.unmount()
      i18n.global.locale.value = 'en-US'
      i18n.global.setLocaleMessage('en-US', {})
    }
  })

  it('refreshes logs and statistics from the toolbar and retries both after stats fail', async () => {
    vi.useFakeTimers()
    const wrapper = mountAuditLogsView()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    const actionSelect = wrapper.findAll('select')[0]
    expect(actionSelect).toBeDefined()
    await actionSelect?.setValue('CREATE_USER')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    const refreshButton = wrapper
      .findAll('button')
      .find((button) => button.attributes('title') === 'common.refresh')
    expect(refreshButton).toBeDefined()
    const initialLogCalls = vi.mocked(auditApi.getAuditLogs).mock.calls.length
    const initialStatsCalls = vi.mocked(auditApi.getAuditStats).mock.calls.length

    vi.mocked(auditApi.getAuditStats)
      .mockRejectedValueOnce(new Error('stats unavailable'))
      .mockResolvedValue(stats)

    await refreshButton?.trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    const retryButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('common.retry'))
    expect(retryButton).toBeDefined()

    await retryButton?.trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    expect(auditApi.getAuditLogs).toHaveBeenCalledTimes(initialLogCalls + 2)
    expect(auditApi.getAuditStats).toHaveBeenCalledTimes(initialStatsCalls + 2)

    const expectedQuery = { action: 'CREATE_USER', page: 1, limit: 50 }
    const logCalls = vi.mocked(auditApi.getAuditLogs).mock.calls
    const statsCalls = vi.mocked(auditApi.getAuditStats).mock.calls
    expect(logCalls[initialLogCalls]?.[0]).toMatchObject(expectedQuery)
    expect(logCalls[initialLogCalls + 1]?.[0]).toMatchObject(expectedQuery)
    expect(statsCalls[initialStatsCalls]?.[0]).toMatchObject(expectedQuery)
    expect(statsCalls[initialStatsCalls + 1]?.[0]).toMatchObject(expectedQuery)
    wrapper.unmount()
  })

  it('cancels stale statistics when the audit query changes', async () => {
    vi.useFakeTimers()
    let firstStatsSignal: AbortSignal | undefined
    let rejectFirstStats: ((reason?: unknown) => void) | undefined
    vi.mocked(auditApi.getAuditStats)
      .mockImplementationOnce((_params, signal) => {
        firstStatsSignal = signal
        return new Promise<AuditStats>((_resolve, reject) => {
          rejectFirstStats = reject
        })
      })
      .mockResolvedValue(stats)

    const wrapper = mountAuditLogsView()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()

    const actionSelect = wrapper.findAll('select')[0]
    await actionSelect?.setValue('CREATE_USER')
    expect(firstStatsSignal?.aborted).toBe(true)

    rejectFirstStats?.(new Error('stale stats unavailable'))
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)

    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    wrapper.unmount()
  })

  it('cancels statistics when the view unmounts', async () => {
    vi.useFakeTimers()
    let statsSignal: AbortSignal | undefined
    vi.mocked(auditApi.getAuditStats).mockImplementation((_params, signal) => {
      statsSignal = signal
      return new Promise<AuditStats>(() => {})
    })

    const wrapper = mountAuditLogsView()
    await flushPromises()
    await vi.advanceTimersByTimeAsync(500)
    await flushPromises()
    expect(statsSignal).toBeDefined()

    wrapper.unmount()
    expect(statsSignal?.aborted).toBe(true)
  })
})
