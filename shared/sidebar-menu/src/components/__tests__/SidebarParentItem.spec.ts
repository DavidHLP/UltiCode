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

  it('mode A (url): renders a router-link (stubbed <a>) for the title with :to + data-active', () => {
    const wrapper = mount(SidebarParentItem, {
      props: { title: 'P', url: '/parent', active: true },
    })
    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
    expect(link.attributes('to')).toBe('/parent')
    expect(link.attributes('data-active')).toBe('true')
  })

  it('mode A (url): renders a separate chevron trigger button', () => {
    const wrapper = mount(SidebarParentItem, {
      props: { title: 'P', url: '/parent' },
    })
    // defaultOpen=true (default) → isOpen → aria-label is "collapse section"
    expect(wrapper.find('button[aria-label="collapse section"]').exists()).toBe(true)
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

  it('defaultOpen=true: renders default slot (CollapsibleContent open)', () => {
    const wrapper = mount(SidebarParentItem, {
      props: { title: 'P', defaultOpen: true },
      slots: { default: '<span data-testid="child">child</span>' },
    })
    expect(wrapper.find('[data-testid="child"]').exists()).toBe(true)
  })

  it('defaultOpen=false: does NOT render default slot (CollapsibleContent hidden)', () => {
    // Regression guard: verifies CollapsibleContent actually gates the slot.
    // The pre-fc266ce10 spec could not catch a controlled-closed regression
    // because it only ever exercised defaultOpen=true.
    const wrapper = mount(SidebarParentItem, {
      props: { title: 'P', defaultOpen: false },
      slots: { default: '<span data-testid="child">child</span>' },
    })
    expect(wrapper.find('[data-testid="child"]').exists()).toBe(false)
  })
})
