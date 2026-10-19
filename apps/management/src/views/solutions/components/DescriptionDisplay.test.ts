import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it } from 'vitest'
import type { Solution } from '@/api/admin/solutions'
import StatusBadge from '@ulticode/design-system/StatusBadge.vue'
import DescriptionDisplay from './DescriptionDisplay.vue'

function mountTags(tags?: string) {
  const solution: Solution = {
    id: 'solution-1',
    problemId: 1,
    userId: 'user-1',
    title: 'Solution',
    content: '',
    language: 'python',
    tags,
    views: 0,
    isPublished: true,
    isFlagged: false,
    isDeleted: false,
    createdAt: '2026-08-29T00:00:00Z',
    updatedAt: '2026-08-29T00:00:00Z',
    author: { id: 'user-1', username: 'alice', name: 'Alice' },
    problem: { id: '1', slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY' },
  }
  return mount(DescriptionDisplay, {
    props: { solution },
    global: {
      plugins: [
        createI18n({
          legacy: false,
          locale: 'en-US',
          messages: { 'en-US': {} },
          missingWarn: false,
          fallbackWarn: false,
        }),
      ],
      stubs: { DescriptionMarkdown: true, DetailField: true },
    },
  })
}

describe('solution description tags', () => {
  it('decodes the JSON string into complete tag labels', () => {
    const wrapper = mountTags('["哈希表","数组"]')
    expect(wrapper.findAllComponents(StatusBadge).map((badge) => badge.props('label'))).toEqual([
      '哈希表',
      '数组',
    ])
    wrapper.unmount()
  })

  it.each(['未编码标签', '["数组",', '{"tag":"数组"}', '[1,"数组"]'])(
    'preserves unsupported encoding as one literal label: %s',
    (raw) => {
      const wrapper = mountTags(raw)
      expect(wrapper.findAllComponents(StatusBadge).map((badge) => badge.props('label'))).toEqual([
        raw,
      ])
      wrapper.unmount()
    },
  )

  it.each([undefined, '', ' ', '[]'])('omits empty tags: %s', (raw) => {
    const wrapper = mountTags(raw)
    expect(wrapper.findAllComponents(StatusBadge)).toHaveLength(0)
    wrapper.unmount()
  })

  it('renders HTML-shaped labels as text', () => {
    const raw = '<img src=x onerror=alert(1)>'
    const wrapper = mountTags(JSON.stringify([raw]))
    expect(wrapper.findComponent(StatusBadge).text()).toContain(raw)
    expect(wrapper.find('img').exists()).toBe(false)
    wrapper.unmount()
  })
})
