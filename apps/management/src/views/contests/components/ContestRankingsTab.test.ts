import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ContestRanking } from '@/api/admin/contests'
import ContestRankingsTab from './ContestRankingsTab.vue'

const ranking = {
  userId: 'user-1',
  username: 'contestant',
  score: 100,
  penalty: 0,
} as ContestRanking

const mountTab = (props: Record<string, unknown>) =>
  mount(ContestRankingsTab, {
    props,
    global: { mocks: { $t: (key: string) => key } },
  })

describe('contest rankings states', () => {
  it('shows loading without an empty result count', () => {
    const wrapper = mountTab({ rankings: [], isLoading: true })
    try {
      expect(wrapper.get('[role="status"]').text()).toContain('contests.detail.rankingsLoading')
      expect(wrapper.find('[data-slot="table"]').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('contests.detail.noRankingsYet')
    } finally {
      wrapper.unmount()
    }
  })

  it('shows an error and retries without an empty result count', async () => {
    const wrapper = mountTab({ rankings: [], hasError: true })
    try {
      expect(wrapper.get('[role="alert"]').text()).toContain('contests.detail.rankingsLoadFailed')
      expect(wrapper.text()).not.toContain('contests.detail.noRankingsYet')
      await wrapper.get('button').trigger('click')
      expect(wrapper.emitted('retry')).toHaveLength(1)
    } finally {
      wrapper.unmount()
    }
  })

  it('shows empty state only after a successful empty response', () => {
    const wrapper = mountTab({ rankings: [], isLoading: false, hasError: false })
    try {
      expect(wrapper.text()).toContain('[0]')
      expect(wrapper.text()).toContain('contests.detail.noRankingsYet')
      expect(wrapper.find('[role="status"]').exists()).toBe(false)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    } finally {
      wrapper.unmount()
    }
  })

  it('renders rankings after success', () => {
    const wrapper = mountTab({ rankings: [ranking] })
    try {
      expect(wrapper.text()).toContain('contestant')
      expect(wrapper.text()).toContain('100')
      expect(wrapper.text()).not.toContain('contests.detail.noRankingsYet')
    } finally {
      wrapper.unmount()
    }
  })
})
