import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SidebarMenuSubItem from '../SidebarMenuSubItem.vue'

describe('SidebarMenuSubItem', () => {
  it('reflects isActive via data-active + uc-sidebar-sub-item class', () => {
    const wrapper = mount(SidebarMenuSubItem, { props: { isActive: true } })
    expect(wrapper.attributes('data-active')).toBe('true')
    expect(wrapper.classes()).toContain('uc-sidebar-sub-item')
  })

  it('defaults to data-active="false"', () => {
    const wrapper = mount(SidebarMenuSubItem)
    expect(wrapper.attributes('data-active')).toBe('false')
  })

  it('exposes the size via data-size', () => {
    const wrapper = mount(SidebarMenuSubItem, { props: { size: 'sm' } })
    expect(wrapper.attributes('data-size')).toBe('sm')
  })

  it('renders the badge when provided', () => {
    const wrapper = mount(SidebarMenuSubItem, {
      props: { badge: 'new' },
      slots: { default: 'Child' },
    })
    expect(wrapper.text()).toContain('new')
    expect(wrapper.text()).toContain('Child')
  })
})
