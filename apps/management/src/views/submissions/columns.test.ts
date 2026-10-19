import { defineComponent, type VNode } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createColumns, getStatusLabel, type SubmissionActions } from './columns'
import type { SubmissionListItem } from '@/api/admin/submissions'
import { getActiveLocale } from '@/i18n'

vi.mock('@/i18n', () => ({ getActiveLocale: vi.fn(() => 'zh-CN') }))

const submission: SubmissionListItem = {
  id: 'submission-1',
  problemId: 1,
  problemTitle: 'Example',
  problemSlug: 'example',
  userId: 'user-1',
  username: 'User',
  language: 'python',
  status: 'Wrong Answer',
  runtime: 0,
  memory: 11,
  codeLength: 337,
  createdAt: '2026-10-12T00:00:00Z',
}

function mountCell(key: string, actions: SubmissionActions) {
  const Harness = defineComponent({
    setup() {
      const columns = createColumns((key) => key, actions)
      const column = columns.find(
        (column) => ('accessorKey' in column ? column.accessorKey : column.id) === key,
      )
      return () =>
        typeof column?.cell === 'function'
          ? (column.cell({ row: { original: submission } } as never) as VNode)
          : null
    },
  })
  return mount(Harness)
}

describe('submission columns', () => {
  afterEach(() => vi.useRealTimers())

  it('shares normalized verdict labels between rows and details', () => {
    const t = (key: string) => (key === 'submissions.statusLabels.WRONG_ANSWER' ? '答案错误' : key)
    expect(getStatusLabel(t, 'Wrong Answer')).toBe('答案错误')
    expect(getStatusLabel(t, 'WRONG_ANSWER')).toBe('答案错误')
  })

  it('names the detail and rejudge actions and invokes only the chosen callback', async () => {
    const actions = { viewSubmission: vi.fn(), openRejudgeDialog: vi.fn() }
    const wrapper = mountCell('actions', actions)
    await wrapper.get('button[aria-label="submissions.actions.view"]').trigger('click')
    expect(actions.viewSubmission).toHaveBeenCalledWith(submission.id)
    expect(actions.openRejudgeDialog).not.toHaveBeenCalled()
    await wrapper.get('button[aria-label="submissions.actions.rejudge"]').trigger('click')
    expect(actions.openRejudgeDialog).toHaveBeenCalledWith(submission.id)
    wrapper.unmount()
  })

  it('formats relative submission time in the active language', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-15T00:00:00Z'))
    vi.mocked(getActiveLocale).mockReturnValue('zh-CN')
    const actions = { viewSubmission: vi.fn(), openRejudgeDialog: vi.fn() }
    const zh = mountCell('createdAt', actions)
    expect(zh.text()).toContain('3天前')
    zh.unmount()
    vi.mocked(getActiveLocale).mockReturnValue('en-US')
    const en = mountCell('createdAt', actions)
    expect(en.text()).toContain('3 days ago')
    en.unmount()
  })
})
