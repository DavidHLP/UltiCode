import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import type { AuditLog } from '@/api/admin/audit'
import ProblemAuditDrawer from './ProblemAuditDrawer.vue'

const mocks = vi.hoisted(() => ({ getProblemAuditLogs: vi.fn() }))
vi.mock('@/api/admin/audit', () => ({ auditApi: mocks }))

const passthrough = { template: '<div><slot /></div>' }
function mountDrawer() {
  return mount(ProblemAuditDrawer, {
    props: { open: true, problemId: 1 },
    global: {
      plugins: [
        createI18n({
          legacy: false,
          locale: 'en-US',
          messages: { 'en-US': { audit: { problemDrawer: { noLogs: 'No audit logs' } } } },
          missingWarn: false,
          fallbackWarn: false,
        }),
      ],
      stubs: {
        Drawer: passthrough,
        DrawerContent: passthrough,
        DrawerHeader: passthrough,
        DrawerTitle: passthrough,
        DrawerDescription: passthrough,
        ScrollArea: passthrough,
        SemanticBadge: true,
      },
    },
  })
}

describe('ProblemAuditDrawer response states', () => {
  it('renders the endpoint array through the real detail container', async () => {
    const log: AuditLog = {
      id: 'audit-1',
      action: 'UPDATE',
      createdAt: '2026-05-30T10:00:00Z',
      oldValues: null,
      newValues: null,
      performer: { id: '1', username: 'audit-reviewer', name: 'Reviewer', role: 'ADMIN' },
    }
    mocks.getProblemAuditLogs.mockResolvedValue([log])
    const wrapper = mountDrawer()
    await flushPromises()
    expect(mocks.getProblemAuditLogs).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('audit-reviewer')
    expect(wrapper.text()).not.toContain('No audit logs')
    wrapper.unmount()
  })

  it('renders the empty array state', async () => {
    mocks.getProblemAuditLogs.mockResolvedValue([])
    const wrapper = mountDrawer()
    await flushPromises()
    expect(wrapper.text()).toContain('No audit logs')
    wrapper.unmount()
  })

  it('shows request errors instead of hiding them as empty history', async () => {
    mocks.getProblemAuditLogs.mockRejectedValue(new Error('Audit request failed'))
    const wrapper = mountDrawer()
    await flushPromises()
    expect(wrapper.text()).toContain('Audit request failed')
    expect(wrapper.text()).not.toContain('No audit logs')
    wrapper.unmount()
  })
})
