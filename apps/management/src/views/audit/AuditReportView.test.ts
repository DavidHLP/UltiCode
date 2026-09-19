import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AuditReportView from './AuditReportView.vue'
import { auditApi, type AuditStats } from '@/api/admin/audit'
type AuditApiModule = { auditApi: typeof auditApi }

vi.mock('@/api/admin/audit', async () => {
  const actual = await vi.importActual<AuditApiModule>('@/api/admin/audit')
  return {
    ...actual,
    auditApi: {
      ...actual.auditApi,
      getAuditStats: vi.fn(),
    },
  }
})

const SlotStub = { template: '<div><slot /></div>' }
const ButtonStub = {
  inheritAttrs: false,
  emits: ['click'],
  template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>',
}
const InputStub = {
  props: { modelValue: { type: String, default: '' } },
  emits: ['update:modelValue'],
  template: `
    <input
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />
  `,
}

const i18n = createI18n({
  legacy: false,
  locale: 'en-US',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'en-US': {} },
})

const stats: AuditStats = {
  totalActions: 42,
  actionsByEntity: [],
  topPerformers: [],
  actionsByType: [],
}

function mountReport() {
  return mount(AuditReportView, {
    global: {
      plugins: [i18n],
      stubs: {
        Badge: SlotStub,
        Button: ButtonStub,
        CalendarIcon: true,
        Card: SlotStub,
        CardContent: SlotStub,
        CardHeader: SlotStub,
        CardTitle: SlotStub,
        Input: InputStub,
        Label: SlotStub,
        RotateCcw: true,
        Select: SlotStub,
        SelectContent: SlotStub,
        SelectItem: SlotStub,
        SelectTrigger: SlotStub,
        SelectValue: SlotStub,
      },
    },
  })
}

function applyButton(wrapper: ReturnType<typeof mountReport>) {
  return wrapper.findAll('button').find((button) => button.text().includes('auditReport.apply'))
}

describe('AuditReportView stats loading', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(auditApi.getAuditStats).mockResolvedValue(stats)
  })

  it('sends the shared normalized query without pagination', async () => {
    const wrapper = mountReport()
    await flushPromises()

    expect(vi.mocked(auditApi.getAuditStats)).toHaveBeenLastCalledWith(
      {
        search: undefined,
        action: undefined,
        entityType: undefined,
        startDate: undefined,
        endDate: undefined,
        performerId: undefined,
        userId: undefined,
      },
      expect.anything(),
    )

    const [startInput, endInput] = wrapper.findAll('input')
    await startInput?.setValue('2026-09-01')
    await endInput?.setValue('2026-09-19')
    await applyButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(vi.mocked(auditApi.getAuditStats)).toHaveBeenLastCalledWith(
      expect.objectContaining({
        startDate: '2026-09-01T00:00:00',
        endDate: '2026-09-20T00:00:00',
      }),
      expect.anything(),
    )
    wrapper.unmount()
  })

  it('keeps the report visible on a first stats failure and clears the error on retry', async () => {
    vi.mocked(auditApi.getAuditStats).mockRejectedValueOnce(new Error('stats unavailable'))
    const wrapper = mountReport()
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').text()).toContain('stats unavailable')
    expect(wrapper.findAll('.opacity-0')).toHaveLength(0)
    expect(wrapper.text()).toContain('auditReport.totalActions')

    await applyButton(wrapper)?.trigger('click')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('42')
    wrapper.unmount()
  })
})
