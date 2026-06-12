import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import DashboardTimeline, { type TimelineActivity } from './DashboardTimeline.vue'
import zhDashboard from '@/i18n/locales/zh-CN/modules/dashboard'
import enDashboard from '@/i18n/locales/en-US/modules/dashboard'
import zhAudit from '@/i18n/locales/zh-CN/modules/audit'
import enAudit from '@/i18n/locales/en-US/modules/audit'

const activities: TimelineActivity[] = [
  {
    id: '1',
    action: 'REVOKE_PERMISSION',
    user: 'admin',
    target: 'PERMISSION',
    time: '2 天前',
  },
]

function mountTimeline(locale: 'zh-CN' | 'en-US', items = activities) {
  const i18n = createI18n({
    legacy: false,
    locale,
    messages: {
      'zh-CN': { dashboard: zhDashboard, audit: zhAudit },
      'en-US': { dashboard: enDashboard, audit: enAudit },
    },
  })

  return mount(DashboardTimeline, {
    props: { activities: items },
    global: { plugins: [i18n] },
  })
}

describe('DashboardTimeline', () => {
  it('localizes actions, target labels, and target types in Chinese', () => {
    const wrapper = mountTimeline('zh-CN')

    expect(wrapper.text()).toContain('撤销权限')
    expect(wrapper.text()).toContain('目标:')
    expect(wrapper.text()).toContain('权限')
    expect(wrapper.text()).not.toContain('PERMISSION')
  })

  it('localizes actions, target labels, and target types in English', () => {
    const wrapper = mountTimeline('en-US')

    expect(wrapper.text()).toContain('Revoke Permission')
    expect(wrapper.text()).toContain('Target:')
    expect(wrapper.text()).toContain('Permission')
  })

  it('uses readable raw values when the backend sends an unknown enum', () => {
    const wrapper = mountTimeline('en-US', [
      { ...activities[0], action: 'CUSTOM_ACTION', target: 'CUSTOM_TARGET' },
    ])

    expect(wrapper.text()).toContain('CUSTOM ACTION')
    expect(wrapper.text()).toContain('CUSTOM TARGET')
    expect(wrapper.text()).not.toContain('dashboard.timeline.activityTypes')
  })
})
