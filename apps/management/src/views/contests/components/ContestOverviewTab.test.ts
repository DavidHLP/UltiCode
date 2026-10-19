import { shallowMount } from '@vue/test-utils'
import { expect, it } from 'vitest'
import { Trophy } from '@lucide/vue'
import { ContestStatus, ContestType, type Contest } from '@/api/admin/contests'
import ContestOverviewTab from './ContestOverviewTab.vue'

it('stacks contest statistics by card width without shrinking icons away', () => {
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
  const wrapper = shallowMount(ContestOverviewTab, {
    props: { contest },
    global: { mocks: { $t: (key: string) => key } },
  })
  try {
    expect(wrapper.find('.grid-cols-1').classes()).toContain('@xs:grid-cols-2')
    expect(wrapper.find('.grid-cols-1').element.closest('.\\@container')).not.toBeNull()
    expect(wrapper.findComponent(Trophy).classes()).toContain('shrink-0')
  } finally {
    wrapper.unmount()
  }
})
