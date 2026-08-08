import { computed, defineComponent, type VNode } from 'vue'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { describe, expect, it, vi } from 'vitest'
import type { Problem } from '@/api/admin/problems'
import { useProblemColumns, type ProblemActions } from './useProblemColumns'

const actions: ProblemActions = {
  viewProblem: vi.fn(),
  viewProblemCode: vi.fn(),
  viewProblemCases: vi.fn(),
  viewFlagInfo: vi.fn(),
  openFlagDialog: vi.fn(),
  openAuditDrawer: vi.fn(),
  unflagProblem: vi.fn(),
  publishProblem: vi.fn(),
  unpublishProblem: vi.fn(),
  confirmDelete: vi.fn(),
}

function mountPublishedCell(isPublished: boolean, isDeleted = false) {
  const Harness = defineComponent({
    setup() {
      const columns = useProblemColumns(
        computed(() => true),
        computed(() => true),
        actions,
      )
      const publishedColumn = columns.find((column) => column.id === 'isPublished')
      const cell = publishedColumn?.cell

      return () =>
        typeof cell === 'function'
          ? (cell({
              row: {
                getValue: () => isPublished,
                original: { isPublished, isDeleted } as Problem,
              },
            } as never) as VNode)
          : null
    },
  })

  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: {
      'zh-CN': {
        problems: {
          published: {
            published: '已发布',
            draft: '草稿',
            deleted: '已删除',
          },
        },
      },
    },
  })

  return mount(Harness, {
    global: { plugins: [i18n] },
  })
}

describe('useProblemColumns published status', () => {
  it('uses a theme-aware success badge for published problems', () => {
    const wrapper = mountPublishedCell(true)

    expect(wrapper.text()).toContain('已发布')
    expect(wrapper.get('.terminal-badge-success').text()).toContain('已发布')
  })

  it('uses semantic neutral and error badges for draft and deleted problems', () => {
    expect(mountPublishedCell(false).get('.terminal-badge-neutral').text()).toContain('草稿')
    expect(mountPublishedCell(false, true).get('.terminal-badge-error').text()).toContain('已删除')
  })
})
