import { mount } from '@vue/test-utils'
import { Users as IconUsers } from '@lucide/vue'

import { describe, expect, it } from 'vitest'
import StatCards, { type StatItem } from './StatCards.vue'

const stats: StatItem[] = [
  {
    title: 'Users',
    value: '18',
    change: '+6',
    trend: 'up',
    description: 'Active this week',
    icon: IconUsers,
    href: '/users',
  },
  {
    title: 'Problems',
    value: '6',
    change: '6 published',
    trend: 'neutral',
    description: '0 unpublished',
  },
]

describe('StatCards', () => {
  it('uses official cards while preserving values, icons and non-color trend labels', () => {
    const wrapper = mount(StatCards, {
      props: { stats },
    })

    const cards = wrapper.findAll('[data-testid="dashboard-stat-card"]')

    expect(cards).toHaveLength(2)
    expect(cards[0].find('[data-slot="card"]').exists()).toBe(true)
    expect(cards[0].find('[data-slot="card-title"]').text()).toBe('Users')
    expect(cards[0].text()).toContain('18')
    expect(cards[0].find('[data-slot="badge"]').text()).toBe('+6')
    expect(cards[0].find('[data-slot="badge"] svg').classes()).toContain('text-status-success-mark')
    expect(cards[0].text()).toContain('Active this week')
  })

  it('preserves link semantics for navigable stats', () => {
    const wrapper = mount(StatCards, {
      props: { stats },
    })

    expect(wrapper.get('a[data-testid="dashboard-stat-card"]').attributes('href')).toBe('/users')
    expect(wrapper.findAll('div[data-testid="dashboard-stat-card"]')).toHaveLength(1)
  })
})
