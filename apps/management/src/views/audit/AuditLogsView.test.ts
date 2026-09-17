import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type * as AuditApiModule from '@/api/admin/audit'
import AuditLogsView from './AuditLogsView.vue'
import { auditApi } from '@/api/admin/audit'

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

const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'en-US': {} },
})

function mountAuditLogsView() {
  return mount(AuditLogsView, {
    global: {
      plugins: [i18n],
      stubs: {
        Button: ButtonStub,
        Input: InputStub,
        DataTable: { template: '<div><slot name="toolbar-left" /></div>' },
        AuditLogDetailDrawer: SlotStub,
        Select: SlotStub,
        SelectContent: SlotStub,
        SelectGroup: SlotStub,
        SelectItem: SlotStub,
        SelectLabel: SlotStub,
        SelectTrigger: SlotStub,
        SelectValue: SlotStub,
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

  it('refreshes logs and statistics from the toolbar and retries both after stats fail', async () => {
    const wrapper = mountAuditLogsView()
    await flushPromises()

    const refreshButton = wrapper
      .findAll('button')
      .find((button) => button.attributes('title') === 'common.refresh')
    expect(refreshButton).toBeDefined()

    vi.mocked(auditApi.getAuditStats)
      .mockRejectedValueOnce(new Error('stats unavailable'))
      .mockResolvedValue(stats)

    await refreshButton?.trigger('click')
    await flushPromises()

    const retryButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('common.retry'))
    expect(retryButton).toBeDefined()

    await retryButton?.trigger('click')
    await flushPromises()

    expect(auditApi.getAuditLogs).toHaveBeenCalledTimes(3)
    expect(auditApi.getAuditStats).toHaveBeenCalledTimes(3)
  })
})
