import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type * as AuditApiModule from '@/api/admin/audit'
import AuditLogViewer from './AuditLogViewer.vue'
import { auditApi } from '@/api/admin/audit'

vi.mock('@/api/admin/audit', async () => {
  const actual = await vi.importActual<AuditApiModule>('@/api/admin/audit')
  return {
    ...actual,
    auditApi: {
      ...actual.auditApi,
      getAuditLogs: vi.fn(),
      exportAuditLogs: vi.fn(),
    },
  }
})

const ButtonStub = {
  inheritAttrs: false,
  emits: ['click'],
  template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>',
}
const SlotStub = { template: '<div><slot /></div>' }
const InputStub = { inheritAttrs: false, template: '<input v-bind="$attrs" />' }
const emptyPage = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 20,
  totalPages: 0,
}
const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'en-US': {} },
})

function mountViewer() {
  return mount(AuditLogViewer, {
    props: { entityType: 'USER', entityId: 'user-1' },
    global: {
      plugins: [i18n],
      stubs: {
        Button: ButtonStub,
        Input: InputStub,
        Badge: SlotStub,
        Card: SlotStub,
        CardContent: SlotStub,
        CardHeader: SlotStub,
        ScrollArea: SlotStub,
        Select: SlotStub,
        SelectContent: SlotStub,
        SelectItem: SlotStub,
        SelectTrigger: SlotStub,
        SelectValue: SlotStub,
        IconChevronDown: true,
        IconChevronUp: true,
        IconDownload: true,
        IconSearch: true,
      },
    },
  })
}

describe('AuditLogViewer export contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(auditApi.getAuditLogs).mockResolvedValue(emptyPage)
    vi.mocked(auditApi.exportAuditLogs).mockResolvedValue(undefined)
  })

  it('omits the all-actions sentinel when exporting without a specific action', async () => {
    const wrapper = mountViewer()
    await flushPromises()

    const exportButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('audit.export'))
    expect(exportButton).toBeDefined()

    await exportButton?.trigger('click')
    await flushPromises()

    expect(auditApi.exportAuditLogs).toHaveBeenCalledWith({
      entityType: 'USER',
      entityId: 'user-1',
      search: undefined,
      action: undefined,
      format: 'csv',
    })
  })
})
