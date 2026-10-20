import { shallowMount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import NotificationCreateDialog from './NotificationCreateDialog.vue'

vi.mock('@/stores/admin/notifications', () => ({ useNotificationsStore: () => ({}) }))

describe('notification dialog short-screen layout', () => {
  it('scrolls the entire form instead of shrinking its fields to zero height', () => {
    const wrapper = shallowMount(NotificationCreateDialog, {
      props: { open: true },
      global: {
        plugins: [
          createI18n({ legacy: false, locale: 'en-US', missingWarn: false, fallbackWarn: false }),
        ],
        renderStubDefaultSlot: true,
      },
    })
    try {
      const dialog = wrapper.findComponent({ name: 'DialogContent' })
      expect(dialog.classes()).not.toContain('flex')
      expect(wrapper.get('form').classes()).not.toContain('min-h-0')
      const fields = wrapper.findAllComponents({ name: 'FieldGroup' })[0]!
      expect(fields.classes()).not.toContain('min-h-0')
      expect(fields.classes()).not.toContain('overflow-y-auto')
      expect(fields.classes()).not.toContain('max-h-[60vh]')
      expect(wrapper.findComponent({ name: 'DialogFooter' }).exists()).toBe(true)
    } finally {
      wrapper.unmount()
    }
  })
})
