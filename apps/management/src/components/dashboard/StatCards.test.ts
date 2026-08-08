import { mount } from '@vue/test-utils'
import { IconUsers } from '@tabler/icons-vue'
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
  it('uses theme-aware card primitives with per-card accent colors', () => {
    const wrapper = mount(StatCards, {
      props: { stats },
    })

    const cards = wrapper.findAll('[data-testid="dashboard-stat-card"]')

    expect(cards).toHaveLength(2)
    expect(cards[0].attributes('style')).toContain('--stat-accent: var(--solarized-blue)')
    expect(cards[1].attributes('style')).toContain('--stat-accent: var(--solarized-cyan)')
    expect(cards[0].classes()).toContain('dashboard-stat-card')
    expect(cards[0].find('.dashboard-stat-card__header').exists()).toBe(true)
    expect(cards[0].find('.dashboard-stat-card__icon').exists()).toBe(true)
  })

  it('preserves link semantics for navigable stats', () => {
    const wrapper = mount(StatCards, {
      props: { stats },
    })

    expect(wrapper.get('a[data-testid="dashboard-stat-card"]').attributes('href')).toBe('/users')
    expect(wrapper.findAll('div[data-testid="dashboard-stat-card"]')).toHaveLength(1)
  })
})
