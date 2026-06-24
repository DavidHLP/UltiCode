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

  it('renders the default slot', () => {
    const wrapper = mount(SidebarGroupCollapsible, {
      props: { defaultOpen: true },
      slots: { default: 'body' },
    })
    expect(wrapper.text()).toContain('body')
  })
})
