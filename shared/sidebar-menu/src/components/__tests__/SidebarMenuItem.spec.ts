import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SidebarMenuItem from '../SidebarMenuItem.vue'

describe('SidebarMenuItem', () => {
  it('reflects isActive via data-active="true" + uc-sidebar-item class', () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { isActive: true, as: 'a' },
    })
    expect(wrapper.attributes('data-active')).toBe('true')
    expect(wrapper.classes()).toContain('uc-sidebar-item')
  })

  it('defaults to data-active="false"', () => {
    const wrapper = mount(SidebarMenuItem, { props: { as: 'a' } })
    expect(wrapper.attributes('data-active')).toBe('false')
  })

  it('renders the badge when provided', () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { badge: 3, as: 'a' },
    })
    expect(wrapper.text()).toContain('3')
  })

  it('omits the badge when undefined', () => {
    const wrapper = mount(SidebarMenuItem, { props: { as: 'a' } })
    expect(wrapper.text()).toBe('')
  })

  it('emits toggle when the chevron is clicked (showChevron)', async () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { showChevron: true, as: 'a' },
    })
    await wrapper.find('[aria-label="toggle section"]').trigger('click')
    expect(wrapper.emitted('toggle')).toBeTruthy()
  })

  it('renders default slot content', () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { as: 'a' },
      slots: { default: 'Home' },
    })
    expect(wrapper.text()).toContain('Home')
  })
})
