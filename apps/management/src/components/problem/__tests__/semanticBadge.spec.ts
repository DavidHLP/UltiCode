import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { badge } from '@ulticode/badge-config'

describe('shared semantic badge renderer', () => {
  it('uses the official Badge with status tokens and readable text', () => {
    const wrapper = mount(
      defineComponent({
        setup: () => () => badge({ color: 'warning', label: 'Pending', size: 'xs', dot: true }),
      }),
    )
    const element = wrapper.get('[data-slot="badge"]')
    expect(element.attributes('data-variant')).toBe('secondary')
    expect(element.classes()).toContain('text-status-warning-mark')
    expect(element.classes()).toContain('text-xs')
    expect(element.find('.bg-status-warning-mark').exists()).toBe(true)
    expect(element.text()).toBe('Pending')
    wrapper.unmount()
  })

  it('renders labels as text rather than HTML', () => {
    const label = '<img src=x onerror="window.badgeUnsafe=true">'
    const wrapper = mount(
      defineComponent({ setup: () => () => h('div', [badge({ color: 'neutral', label })]) }),
    )
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toBe(label)
    wrapper.unmount()
  })
})
