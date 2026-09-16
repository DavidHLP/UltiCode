import { createI18n } from 'vue-i18n'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ColumnDef, PaginationState } from '@tanstack/vue-table'
import DataTable from './DataTable.vue'
import { createSelectionColumn } from './selectionColumn'

interface Row {
  id: string | number
}

const rows: Row[] = [
  { id: '1' },
  { id: '2' },
]

const columns: ColumnDef<Row>[] = [
  {
    accessorKey: 'id',
    header: 'ID',
  },
]

const ResizeObserverStub = vi.hoisted(() => {
  class Stub {
    observe() {
      return undefined
    }
    unobserve() {
      return undefined
    }
    disconnect() {
      return undefined
    }
  }
  vi.stubGlobal('ResizeObserver', Stub)
  return Stub
})

function mountTable(props: {
  data?: Row[]
  loading?: boolean
  pagination?: PaginationState
  rowCount?: number
  selectedRows?: Row[]
}, slots?: Record<string, string>) {
  return mount(DataTable, {
    props: {
      columns,
      data: rows,
      pagination: { pageIndex: 0, pageSize: 10 },
      rowCount: rows.length,
      ...props,
    },
    slots,
    global: {
      plugins: [createI18n({ legacy: false, locale: 'en', messages: { en: {} } })],
    },
  })
}

beforeEach(() => {
  vi.stubGlobal('ResizeObserver', ResizeObserverStub)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('DataTable public contract', () => {
  it('emits pagination updates without mutating the controlled prop', async () => {
    const wrapper = mountTable({ rowCount: 21 })
    const nextButton = wrapper
      .findAll('button')
      .find((button) => button.attributes('aria-label') === 'table.goToNextPage')

    expect(nextButton).toBeDefined()
    await nextButton?.trigger('click')

    expect(wrapper.emitted('update:pagination')).toEqual([[{ pageIndex: 1, pageSize: 10 }]])
    expect(wrapper.props('pagination')).toEqual({ pageIndex: 0, pageSize: 10 })
  })

  it('emits selected row data while keeping selection presentation local', async () => {
    const selectionColumns = createSelectionColumn<Row>((key) => key)
    const wrapper = mount(DataTable, {
      props: {
        columns: [...selectionColumns, ...columns],
        data: rows,
        pagination: { pageIndex: 0, pageSize: 10 },
        rowCount: rows.length,
        selectedRows: [],
      },
      global: {
        plugins: [createI18n({ legacy: false, locale: 'en', messages: { en: {} } })],
      },
    })
    const checkboxes = wrapper.findAll('[role="checkbox"]')

    expect(checkboxes).toHaveLength(3)
    await checkboxes[1].trigger('click')

    expect(wrapper.emitted('update:selectedRows')).toEqual([[rows.slice(0, 1)]])
  })

  it('renders the explicit empty slot when idle with no rows', () => {
    const wrapper = mountTable(
      { data: [], rowCount: 0 },
      { empty: '<div data-test="empty">No rows</div>' },
    )

    expect(wrapper.find('[data-test="empty"]').text()).toBe('No rows')
  })

  it('renders loading skeletons instead of the empty state while loading', () => {
    const wrapper = mountTable(
      { data: [], loading: true, rowCount: 0 },
      { empty: '<div data-test="empty">No rows</div>' },
    )

    expect(wrapper.find('[data-test="empty"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-slot="skeleton"]').length).toBeGreaterThan(0)
  })
})
