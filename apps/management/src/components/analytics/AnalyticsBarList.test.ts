import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AnalyticsBarList from './AnalyticsBarList.vue'

describe('AnalyticsBarList', () => {
  it.each([
    [0, '0%'],
    [57.143, '57.1%'],
    [100, '100%'],
  ] as const)('shows rate %s with a percent unit', (value, expected) => {
    const wrapper = mount(AnalyticsBarList, {
      props: {
        showPercentage: true,
        items: [{ id: 1, label: 'Completion', value }],
      },
    })

    expect(wrapper.text()).toContain(expected)
  })

  it('keeps count values unitless and long labels reachable', () => {
    const label = 'A very long contest title that must remain readable on narrow screens'
    const wrapper = mount(AnalyticsBarList, {
      props: { items: [{ id: 1, label, value: 5 }] },
    })

    expect(wrapper.text()).not.toContain('5%')
    const labelElement = wrapper.findAll('span').find((element) => element.text() === label)!
    expect(labelElement.classes()).not.toContain('truncate')
    expect(labelElement.classes()).toContain('break-words')
  })

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
