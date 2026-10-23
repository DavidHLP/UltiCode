import { mount, shallowMount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import { ContestType } from '@/api/admin/contests'
import enContests from '@/i18n/locales/en-US/modules/contests'
import zhContests from '@/i18n/locales/zh-CN/modules/contests'
import ContestWizard from './ContestWizard.vue'
import StepReview from './StepReview.vue'

describe('contest wizard short-screen layout', () => {
  it.each([
    ['en-US', false, 'Draft'],
    ['en-US', true, 'Published'],
    ['zh-CN', false, '草稿'],
    ['zh-CN', true, '已发布'],
  ] as const)('localizes review visibility in %s (published: %s)', (locale, isPublished, label) => {
    const wrapper = mount(StepReview, {
      props: {
        slice: {
          title: 'Unsaved contest',
          slug: 'unsaved-contest',
          description: '',
          contestType: ContestType.ICPC,
          scoringRuleId: '',
          startTimeLocal: '',
          duration: 120,
          isPublished,
          problems: [],
        },
      },
      global: {
        plugins: [
          createI18n({
            legacy: false,
            locale,
            missingWarn: false,
            fallbackWarn: false,
            messages: {
              'en-US': { contests: enContests },
              'zh-CN': { contests: zhContests },
            },
          }),
        ],
      },
    })
    try {
      expect(wrapper.findAll('[data-slot="badge"]').at(-1)?.text()).toBe(label)
    } finally {
      wrapper.unmount()
    }
  })

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
