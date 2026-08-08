import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalyticsBarList from './AnalyticsBarList.vue'

describe('AnalyticsBarList', () => {
  it('removes the base card spacing in the default layout', () => {
    const wrapper = mount(AnalyticsBarList, {
      props: {
        items: [{ id: 1, label: 'ICPC', value: 5 }],
      },
    })

    expect(wrapper.get('[data-slot="card"]').classes()).toEqual(
      expect.arrayContaining(['h-full', 'gap-0', 'py-0']),
    )
  })

  it('stretches to fill grid cell and uses reduced spacing in compact mode', () => {
    const wrapper = mount(AnalyticsBarList, {
      props: {
        compact: true,
        items: [{ id: 1, label: 'ICPC', value: 5 }],
      },
    })

    expect(wrapper.get('[data-slot="card"]').classes()).toEqual(
      expect.arrayContaining(['h-full', 'gap-0', 'py-0']),
    )
    expect(wrapper.get('[data-slot="card-content"]').classes()).toContain('p-4')
    expect(wrapper.get('[data-testid="bar-list"]').classes()).toContain('space-y-2')
  })
})
