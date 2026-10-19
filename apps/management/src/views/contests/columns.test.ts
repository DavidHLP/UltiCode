import { computed, defineComponent, type VNode } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import type { Contest } from '@/api/admin/contests'
import { createColumns, type ContestActions } from './columns'
import type { ContestPermissionMap } from '@/composables/domainPermissionMaps'

const actions: ContestActions = {
  viewContest: vi.fn(),
  startContest: vi.fn(),
  endContest: vi.fn(),
  startDeleteContest: vi.fn(),
}

function mountContestCell(accessorKey = 'title') {
  const contest = {
    id: 'contest-1',
    title: 'UltiCode Spring Invitational',
    slug: 'ulticode-spring-invitational',
    startTime: '2026-08-19T10:00:00Z',
    durationMinutes: 90,
  } as Contest

  const Harness = defineComponent({
    setup() {
      const columns = createColumns(
        (key, params) =>
          key === 'contests.scheduleStep.minutes' && typeof params === 'object'
            ? `${params.minutes} minutes`
            : key,
        actions,
        {
          read: computed(() => true),
          create: computed(() => true),
          update: computed(() => true),
          delete: computed(() => true),
        } satisfies ContestPermissionMap,
      )
      const titleColumn = columns.find(
        (column) => 'accessorKey' in column && column.accessorKey === accessorKey,
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
    const wrapper = mountContestCell()
    const icon = wrapper.get('[data-testid="contest-title-icon"]')

    expect(icon.classes()).toContain('bg-[var(--muted)]')
    expect(icon.classes()).not.toContain('dark:bg-[var(--foreground)]')
    expect(wrapper.get('[data-testid="contest-title"]').classes()).toContain(
      'text-[var(--foreground)]',
    )
    expect(wrapper.get('[data-testid="contest-slug"]').classes()).toContain(
      'text-[var(--muted-foreground)]',
    )
  })

  it('renders the durationMinutes response field in the schedule', () => {
    const wrapper = mountContestCell('startTime')
    expect(wrapper.text()).toContain('90 minutes')
  })
})
