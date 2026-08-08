import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SidebarNavUser from '../SidebarNavUser.vue'

describe('SidebarNavUser', () => {
  it('renders name, email and role', () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: 'Alice', email: 'a@b.c', role: 'ADMIN' } },
    })
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('a@b.c')
    expect(wrapper.text()).toContain('ADMIN')
  })

  it('shows initials fallback when no avatar', () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: 'Bob' } },
    })
    expect(wrapper.text()).toContain('B')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('renders avatar img when provided', () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: 'C', avatar: 'https://x/y.png' } },
    })
    expect(wrapper.find('img').attributes('src')).toBe('https://x/y.png')
  })

  it('falls back to initials when the avatar fails to load (@error)', async () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: 'Dan', avatar: 'https://broken/x.png' } },
    })
    expect(wrapper.find('img').exists()).toBe(true)
    await wrapper.find('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('D')
  })

  it('handles an empty name without crashing', () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: '' } },
    })
    expect(wrapper.find('img').exists()).toBe(false)
    // name.charAt(0) is '' → initials span renders empty; no throw.
    expect(wrapper.find('div.min-w-0').exists()).toBe(true)
  })

  it('renders #menu slot', () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: 'A' } },
      slots: { menu: '<button>menu</button>' },
    })
    expect(wrapper.text()).toContain('menu')
  })
})
