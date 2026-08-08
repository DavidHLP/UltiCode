import { defineComponent, type VNode } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { Contest } from '@/api/admin/contests'
import { createColumns, type ContestActions } from './columns'

const actions: ContestActions = {
  viewContest: vi.fn(),
  startContest: vi.fn(),
  endContest: vi.fn(),
  startDeleteContest: vi.fn(),
}

function mountContestTitleCell() {
  const contest = {
    id: 'contest-1',
    title: 'UltiCode Spring Invitational',
    slug: 'ulticode-spring-invitational',
  } as Contest

  const Harness = defineComponent({
    setup() {
      const columns = createColumns(
        (key) => key,
        actions,
        () => true,
        () => true,
      )
      const titleColumn = columns.find(
        (column) => 'accessorKey' in column && column.accessorKey === 'title',
      )
      const cell = titleColumn?.cell

      return () =>
        typeof cell === 'function' ? (cell({ row: { original: contest } } as never) as VNode) : null
    },
  })

  return mount(Harness)
}

describe('contest title column', () => {
  it('uses theme-aware surfaces and text colors', () => {
    const wrapper = mountContestTitleCell()
    const icon = wrapper.get('[data-testid="contest-title-icon"]')

    expect(icon.classes()).toContain('bg-[var(--surface-sunken)]')
    expect(icon.classes()).not.toContain('dark:bg-[var(--silver-800)]')
    expect(wrapper.get('[data-testid="contest-title"]').classes()).toContain(
      'text-[var(--foreground)]',
    )
    expect(wrapper.get('[data-testid="contest-slug"]').classes()).toContain(
      'text-[var(--muted-foreground)]',
    )
  })
})
