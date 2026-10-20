import { createI18n } from 'vue-i18n'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ColumnDef, PaginationState } from '@tanstack/vue-table'
import DataTable from './DataTable.vue'
import { createSelectionColumn } from './selectionColumn'

interface Row {
  id: string | number
}

const rows: Row[] = [{ id: '1' }, { id: '2' }]

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

function mountTable(
  props: {
    data?: Row[]
    loading?: boolean
    pagination?: PaginationState
    rowCount?: number
    selectedRows?: Row[]
  },
  slots?: Record<string, string>,
) {
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
      .find((button) => button.text().includes('table.goToNextPage'))

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

  it('clears selected rows when loading starts and when row data changes', async () => {
    const selectionColumns = createSelectionColumn<Row>((key) => key)
    const wrapper = mount(DataTable, {
      props: {
        columns: [...selectionColumns, ...columns],
        data: rows,
        loading: false,
        pagination: { pageIndex: 0, pageSize: 10 },
        rowCount: rows.length,
        selectedRows: [],
      },
      global: {
        plugins: [createI18n({ legacy: false, locale: 'en', messages: { en: {} } })],
      },
    })

    try {
      await wrapper.findAll('[role="checkbox"]')[1].trigger('click')
      expect(wrapper.emitted('update:selectedRows')?.[0]).toEqual([rows.slice(0, 1)])

      await wrapper.setProps({ loading: true })
      let updates = wrapper.emitted('update:selectedRows') ?? []
      expect(updates[updates.length - 1]).toEqual([[]])

      await wrapper.setProps({ loading: false })
      await wrapper.findAll('[role="checkbox"]')[1].trigger('click')
      await wrapper.setProps({ data: [], rowCount: 0 })
      updates = wrapper.emitted('update:selectedRows') ?? []
      expect(updates[updates.length - 1]).toEqual([[]])

      await wrapper.setProps({ data: [{ id: '3' }, { id: '4' }], rowCount: 2 })
      expect(wrapper.findAll('[role="checkbox"]')[1].attributes('aria-checked')).toBe('false')
    } finally {
      wrapper.unmount()
    }
  })

  it('renders the explicit empty slot when idle with no rows', () => {
    const wrapper = mountTable(
      { data: [], rowCount: 0 },
      { empty: '<div data-test="empty">No rows</div>' },
    )

    try {
      expect(wrapper.find('[data-test="empty"]').text()).toBe('No rows')
      expect(wrapper.get('[data-test="empty"]').element.parentElement?.classList).not.toContain(
        'h-96',
      )
    } finally {
      wrapper.unmount()
    }
  })

  it('retains the default empty presentation height without a custom slot', () => {
    const wrapper = mountTable({ data: [], rowCount: 0 })
    try {
      expect(wrapper.find('.h-96').exists()).toBe(true)
    } finally {
      wrapper.unmount()
    }
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
