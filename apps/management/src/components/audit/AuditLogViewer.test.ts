import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuditLogViewer from './AuditLogViewer.vue'
import { auditApi } from '@/api/admin/audit'
type AuditApiModule = { auditApi: typeof auditApi }

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
    </select>
  `,
}

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

function mountViewer(props: { entityType?: string; entityId?: string } = { entityType: 'USER', entityId: 'user-1' }) {
  return mount(AuditLogViewer, {
    props,
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
        Select: SelectStub,
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
    wrapper.unmount()
  })

  it('preserves a specific action when exporting', async () => {
    const wrapper = mountViewer()
    await flushPromises()

    await wrapper.find('select').setValue('CREATE_USER')
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
      action: 'CREATE_USER',
      format: 'csv',
    })
    wrapper.unmount()
  })

  it('does not show a perpetual loading state without an entity scope', async () => {
    const wrapper = mountViewer({})
    await flushPromises()

    expect(wrapper.text()).toContain('audit.noLogs')
    expect(wrapper.text()).not.toContain('common.loading')
    expect(auditApi.getAuditLogs).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
