import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import StepSchedule from './StepSchedule.vue'

describe('Schedule publish control', () => {
  it('reflects the draft and emits a boolean patch from the real switch', async () => {
    const wrapper = mount(StepSchedule, {
      props: { slice: { startTimeLocal: '', duration: 120, isPublished: true } },
      global: {
        plugins: [
          createI18n({
            legacy: false,
            locale: 'en',
            messages: { en: {} },
            missingWarn: false,
            fallbackWarn: false,
          }),
        ],
      },
    })
    const control = wrapper.get('[role="switch"]')
    expect(control.attributes('aria-checked')).toBe('true')
    expect(wrapper.get('label[for]').attributes('for')).toBe(control.attributes('id'))
    await control.trigger('click')
    expect(wrapper.emitted('patch')).toEqual([[{ isPublished: false }]])
    wrapper.unmount()
  })
})
