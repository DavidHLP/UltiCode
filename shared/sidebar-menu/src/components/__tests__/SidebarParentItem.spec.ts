import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SidebarParentItem from '../SidebarParentItem.vue'

describe('SidebarParentItem', () => {
  it('renders the title', () => {
    const wrapper = mount(SidebarParentItem, { props: { title: 'Parent' } })
    expect(wrapper.text()).toContain('Parent')
  })

  it('mode B (no url): does not render a router-link', () => {
    const wrapper = mount(SidebarParentItem, { props: { title: 'P' } })
    expect(wrapper.find('router-link').exists()).toBe(false)
  })

  it('reflects active via data-active="true"', () => {
    const wrapper = mount(SidebarParentItem, {
      props: { title: 'P', active: true },
    })
    expect(wrapper.find('[data-active="true"]').exists()).toBe(true)
  })

  it('mounts a CollapsibleRoot', () => {
    const wrapper = mount(SidebarParentItem, { props: { title: 'P' } })
    expect(wrapper.find('[data-slot="collapsible"]').exists()).toBe(true)
  })

  it('renders default slot content', () => {
    const wrapper = mount(SidebarParentItem, {
      props: { title: 'P' },
      slots: { default: '<ul><li>child</li></ul>' },
    })
    expect(wrapper.text()).toContain('child')
  })
})
