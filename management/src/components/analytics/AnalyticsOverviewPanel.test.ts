import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalyticsOverviewPanel from './AnalyticsOverviewPanel.vue'

const IconStub = defineComponent({ template: '<svg />' })

describe('AnalyticsOverviewPanel', () => {
  it('renders all metric groups inside one continuous panel', () => {
    const wrapper = mount(AnalyticsOverviewPanel, {
      props: {
        groups: [
          {
            title: 'Users',
            icon: IconStub,
            items: [{ label: 'Daily active', value: '18' }],
          },
          {
            title: 'Problems',
            icon: IconStub,
            items: [{ label: 'Attempts', value: '75' }],
          },
        ],
      },
    })

    expect(wrapper.findAll('[data-slot="card"]')).toHaveLength(1)
    expect(wrapper.findAll('section')).toHaveLength(2)
    expect(wrapper.text()).toContain('Daily active')
    expect(wrapper.text()).toContain('Attempts')
  })
})
