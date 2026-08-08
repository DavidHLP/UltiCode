import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalyticsTagCloud from './AnalyticsTagCloud.vue'

describe('AnalyticsTagCloud', () => {
  it('removes the base card spacing in the default layout', () => {
    const wrapper = mount(AnalyticsTagCloud, {
      props: {
        tags: [{ id: 1, label: 'Array', value: 100 }],
      },
    })

    expect(wrapper.get('[data-slot="card"]').classes()).toEqual(
      expect.arrayContaining(['h-full', 'gap-0', 'py-0']),
    )
  })

  it('uses content height and compact spacing in compact mode', () => {
    const wrapper = mount(AnalyticsTagCloud, {
      props: {
        compact: true,
        tags: [{ id: 1, label: 'Array', value: 100 }],
      },
    })

    expect(wrapper.get('[data-slot="card"]').classes()).toContain('self-start')
    expect(wrapper.get('[data-slot="card"]').classes()).toContain('gap-0')
    expect(wrapper.get('[data-slot="card"]').classes()).toContain('py-0')
    expect(wrapper.get('[data-slot="card-content"]').classes()).toContain('p-4')
    expect(wrapper.get('[data-testid="tag-cloud"]').classes()).toContain('gap-1.5')
    expect(wrapper.get('[data-testid="tag-item"]').classes()).toContain('py-0.5')
  })
})
