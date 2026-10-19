import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { ContestStatus, ContestType, type Contest } from '@/api/admin/contests'
import ContestProblemsTab from './ContestProblemsTab.vue'

const contest: Contest = {
  id: '1',
  slug: 'linked-list-special',
  title: 'Contest',
  contestType: ContestType.ICPC,
  status: ContestStatus.FINISHED,
  startTime: '2026-09-03T12:13:00Z',
  durationMinutes: 90,
  isVisible: true,
  isPremium: false,
  isPublished: true,
  problemCount: 3,
  participantCount: 2,
}

describe('contest problem availability', () => {
  it.each([undefined, []])(
    'does not claim missing details are an empty contest (%s)',
    (problemIds) => {
      const wrapper = mount(ContestProblemsTab, {
        props: { contest: { ...contest, problemIds }, canUpdate: false },
        global: { mocks: { $t: (key: string) => key } },
      })
      try {
        expect(wrapper.text()).toContain('contests.detail.problemDetailsUnavailable')
      expect(wrapper.get('td').classes()).toContain('whitespace-normal')
        expect(wrapper.text()).not.toContain('contests.detail.noProblemsAdded')
      } finally {
        wrapper.unmount()
      }
    },
  )

  it('keeps an actual zero-count empty state distinct', () => {
    const wrapper = mount(ContestProblemsTab, {
      props: { contest: { ...contest, problemCount: 0, problemIds: [] }, canUpdate: false },
      global: { mocks: { $t: (key: string) => key } },
    })
    try {
      expect(wrapper.text()).toContain('contests.detail.noProblemsAdded')
      expect(wrapper.text()).not.toContain('contests.detail.problemDetailsUnavailable')
    } finally {
      wrapper.unmount()
    }
  })
})
