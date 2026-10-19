import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import MonitoringView from './MonitoringView.vue'
import { monitoringApi } from '@/api/admin/monitoring'

vi.mock('@/api/admin/monitoring', () => ({ monitoringApi: { getAllStats: vi.fn() } }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }))
vi.mock('@/i18n/utils', () => ({ formatDateTimeByLocale: (value: string) => value }))
afterEach(() => vi.clearAllMocks())

it('shows failed probes as unavailable without zero, empty-queue, or NaN claims', async () => {
  vi.mocked(monitoringApi.getAllStats).mockResolvedValue({
    system: null,
    resources: null,
    database: null,
    queues: null,
    redis: null,
    health: null,
  })
  const wrapper = shallowMount(MonitoringView, { global: { renderStubDefaultSlot: true } })
  try {
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toBe('system.monitoring.unavailable')
    expect(wrapper.text()).not.toContain('NaN')
    expect(wrapper.text()).not.toContain('0.0 B')
    expect(wrapper.text()).not.toContain('system.monitoring.noQueues')
    expect(wrapper.text()).not.toContain('< 1m')
    expect(wrapper.text()).toContain('—')
    expect(wrapper.findComponent({ name: 'SemanticBadge' }).props('label')).toBe(
      'system.monitoring.status.unknown',
    )
  } finally {
    wrapper.unmount()
  }
})

it('does not divide by a zero heap limit', async () => {
  vi.mocked(monitoringApi.getAllStats).mockResolvedValue({
    system: null,
    database: null,
    queues: [],
    redis: null,
    health: null,
    resources: {
      memory: { heapUsed: 0, heapMax: 0, nonHeapUsed: 0 },
      cpu: { processCpuLoad: 0, systemCpuLoad: 0, availableProcessors: 0 },
      threadCount: 0,
    },
  })
  const wrapper = shallowMount(MonitoringView, { global: { renderStubDefaultSlot: true } })
  try {
    await flushPromises()
    expect(wrapper.text()).not.toContain('NaN')
    expect(wrapper.text()).not.toContain('Infinity')
    expect(wrapper.text()).not.toContain('0.0%')
    expect(wrapper.text()).toContain('system.monitoring.noQueues')
  } finally {
    wrapper.unmount()
  }
})
