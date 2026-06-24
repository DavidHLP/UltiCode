import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SidebarGroupCollapsible from '../SidebarGroupCollapsible.vue'

describe('SidebarGroupCollapsible', () => {
  it('mounts a CollapsibleRoot (data-slot="collapsible")', () => {
    const wrapper = mount(SidebarGroupCollapsible, { props: { defaultOpen: true } })
    expect(wrapper.find('[data-slot="collapsible"]').exists()).toBe(true)
  })

  it('renders the .uc-sidebar-group-label when title is provided', () => {
    const wrapper = mount(SidebarGroupCollapsible, { props: { title: 'Section' } })
    expect(wrapper.find('.uc-sidebar-group-label').exists()).toBe(true)
    expect(wrapper.text()).toContain('Section')
  })

  it('omits the label row when no title', () => {
    const wrapper = mount(SidebarGroupCollapsible)
    expect(wrapper.find('.uc-sidebar-group-label').exists()).toBe(false)
  })

  it('reflects active on the label via data-active="true" (CSS contract, not a hand-written class)', () => {
    const wrapper = mount(SidebarGroupCollapsible, {
      props: { title: 'Sec', active: true },
    })
    expect(wrapper.find('.uc-sidebar-group-label').attributes('data-active')).toBe('true')
  })

  it('defaults the label to data-active="false"', () => {
    const wrapper = mount(SidebarGroupCollapsible, { props: { title: 'Sec' } })
    expect(wrapper.find('.uc-sidebar-group-label').attributes('data-active')).toBe('false')
  })

  it('is uncontrolled — forwards defaultOpen, does NOT bind :open', () => {
    // Binding :open=undefined makes reka treat the root as controlled-closed
    // (fc266ce10 regression). This component is uncontrolled by design, so the
    // root must mount in its defaultOpen state. Callers place CollapsibleContent
    // in the default slot (as console does) to gate children on open.
    const wrapper = mount(SidebarGroupCollapsible, { props: { defaultOpen: true } })
    expect(wrapper.find('[data-slot="collapsible"]').exists()).toBe(true)
  })

  it('renders the default slot', () => {
    const wrapper = mount(SidebarGroupCollapsible, {
      props: { defaultOpen: true },
      slots: { default: 'body' },
    })
    expect(wrapper.text()).toContain('body')
  })
})
