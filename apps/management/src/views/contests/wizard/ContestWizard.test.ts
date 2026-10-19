import { shallowMount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import ContestWizard from './ContestWizard.vue'

describe('contest wizard short-screen layout', () => {
  it('scrolls the whole dialog instead of squeezing the steps into a fixed-height body', () => {
    const wrapper = shallowMount(ContestWizard, {
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
      expect(dialog.classes()).toContain('w-[calc(100%-2rem)]')
      expect(dialog.classes()).not.toContain('h-[80vh]')
      expect(dialog.classes()).not.toContain('overflow-hidden')
      expect(dialog.classes()).not.toContain('flex')
      const steps = wrapper.findComponent({ name: 'Stepper' })
      const body = steps.element.parentElement!.parentElement!
      expect(body.classList).not.toContain('min-h-0')
      expect(body.classList).not.toContain('overflow-y-auto')
      expect(wrapper.findAllComponents({ name: 'StepperItem' })).toHaveLength(5)
      expect(wrapper.findComponent({ name: 'DialogFooter' }).exists()).toBe(true)
    } finally {
      wrapper.unmount()
    }
  })
})
