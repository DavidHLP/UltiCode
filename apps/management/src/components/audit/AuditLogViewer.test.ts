import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuditLogViewer from './AuditLogViewer.vue'
import { auditApi, type AuditLog } from '@/api/admin/audit'
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
const auditLog: AuditLog = {
  id: 'log-1',
  createdAt: '2026-05-28T11:00:00.000Z',
  performer: { id: 'admin-1', username: 'admin_legacy', name: 'Admin Legacy', role: 'ADMIN' },
  action: 'UPDATE_PROBLEM',
  entityType: 'PROBLEM',
  entityId: '1',
  oldValues: null,
  newValues: null,
}
const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'en-US': {} },
})

function mountViewer(
  props: { entityType?: string; entityId?: string } = { entityType: 'USER', entityId: 'user-1' },
) {
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

describe('AuditLogViewer', () => {
  it('updates the closed action label when the language or selection changes', async () => {
    const translations = createI18n({
      legacy: false,
      locale: 'zh-CN',
      missingWarn: false,
      messages: {
        'zh-CN': { audit: { allActions: '全部操作', actionTypes: { CREATE_USER: '创建用户' } } },
        'en-US': {
          audit: { allActions: 'All Actions', actionTypes: { CREATE_USER: 'Create User' } },
        },
      },
    })
    const wrapper = shallowMount(AuditLogViewer, {
      props: { entityType: 'PROBLEM', entityId: '7' },
      global: { plugins: [translations], renderStubDefaultSlot: true },
    })
    try {
      await flushPromises()
      const value = wrapper.findComponent({ name: 'SelectValue' })
      expect(value.text()).toBe('全部操作')
      translations.global.locale.value = 'en-US'
      await flushPromises()
      expect(value.text()).toBe('All Actions')
      wrapper.findComponent({ name: 'Select' }).vm.$emit('update:modelValue', 'CREATE_USER')
      await flushPromises()
      expect(value.text()).toBe('Create User')
      translations.global.locale.value = 'zh-CN'
      await flushPromises()
      expect(value.text()).toBe('创建用户')
    } finally {
      wrapper.unmount()
    }
  })

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(auditApi.getAuditLogs).mockResolvedValue(emptyPage)
    vi.mocked(auditApi.exportAuditLogs).mockResolvedValue(undefined)
  })

  it('keeps audit timestamps visible when card metadata wraps', async () => {
    vi.mocked(auditApi.getAuditLogs).mockResolvedValue({
      ...emptyPage,
      items: [auditLog],
      total: 1,
      totalPages: 1,
    })
    const wrapper = mountViewer()
    await flushPromises()

    const timestamp = wrapper
      .findAll('span')
      .find((span) => span.classes().includes('tabular-nums'))
    expect(wrapper.find('.w-full.min-w-0.flex-wrap').exists()).toBe(true)
    expect(timestamp?.classes()).toContain('shrink-0')
    expect(timestamp?.classes()).not.toContain('truncate')
    expect(wrapper.text()).toContain('admin_legacy')
    wrapper.unmount()
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

  it('does not issue unscoped requests without an entity scope', async () => {
    const wrapper = mountViewer({})
    await flushPromises()

    const searchButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('common.search'))
    const exportButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('audit.export'))
    expect(searchButton).toBeDefined()
    expect(exportButton).toBeDefined()
    expect(exportButton?.attributes('disabled')).toBeDefined()

    await searchButton?.trigger('click')
    await exportButton?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('audit.noLogs')
    expect(wrapper.text()).not.toContain('common.loading')
    expect(auditApi.getAuditLogs).not.toHaveBeenCalled()
    expect(auditApi.exportAuditLogs).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
