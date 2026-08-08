import { describe, expect, it, vi } from 'vitest'
import { createSelectionColumn } from '../selectionColumn'
import type { HeaderContext, CellContext } from '@tanstack/vue-table'
import type { ColumnDef } from '@tanstack/vue-table'

const mockT = (key: string) => key

// Helpers to build minimally-typed context objects
function makeHeaderCtx(table: {
  getIsAllPageRowsSelected: () => boolean
  getIsSomePageRowsSelected: () => boolean
  toggleAllPageRowsSelected: (v: boolean) => void
}): HeaderContext<unknown, unknown> {
  return {
    table,
    column: {} as HeaderContext<unknown, unknown>['column'],
    header: {} as HeaderContext<unknown, unknown>['header'],
    getState: vi.fn(),
    getContext: vi.fn(),
  } as unknown as HeaderContext<unknown, unknown>
}

function makeCellCtx(row: {
  getIsSelected: () => boolean
  toggleSelected: (v: boolean) => void
}): CellContext<unknown, unknown> {
  return {
    row: row as CellContext<unknown, unknown>['row'],
    column: {} as CellContext<unknown, unknown>['column'],
    getValue: vi.fn(),
    renderValue: vi.fn(),
    getContext: vi.fn(),
    table: {} as CellContext<unknown, unknown>['table'],
  } as unknown as CellContext<unknown, unknown>
}

describe('createSelectionColumn', () => {
  it('returns a single-column array with id=select and sorting/hiding disabled', () => {
    const cols = createSelectionColumn(mockT)
    expect(cols).toHaveLength(1)
    expect(cols[0].id).toBe('select')
    expect(cols[0].enableSorting).toBe(false)
    expect(cols[0].enableHiding).toBe(false)
  })

  describe('header tri-state', () => {
    it('modelValue is true when all rows selected', () => {
      const table = {
        getIsAllPageRowsSelected: () => true,
        getIsSomePageRowsSelected: () => true,
        toggleAllPageRowsSelected: vi.fn(),
      }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      expect(vnode.props).toMatchObject({ modelValue: true })
    })

    it('modelValue is false when no rows selected', () => {
      const table = {
        getIsAllPageRowsSelected: () => false,
        getIsSomePageRowsSelected: () => false,
        toggleAllPageRowsSelected: vi.fn(),
      }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      expect(vnode.props).toMatchObject({ modelValue: false })
    })

    it('modelValue is indeterminate when some rows selected', () => {
      const table = {
        getIsAllPageRowsSelected: () => false,
        getIsSomePageRowsSelected: () => true,
        toggleAllPageRowsSelected: vi.fn(),
      }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      expect(vnode.props).toMatchObject({ modelValue: 'indeterminate' })
    })

    it('aria-label defaults to t("table.selectAll")', () => {
      const table = { getIsAllPageRowsSelected: () => true, getIsSomePageRowsSelected: () => true, toggleAllPageRowsSelected: vi.fn() }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      expect(vnode.props).toMatchObject({ 'aria-label': 'table.selectAll' })
    })

    it('onUpdate:modelValue(true) calls toggleAllPageRowsSelected(true)', () => {
      const toggleAll = vi.fn()
      const table = { getIsAllPageRowsSelected: () => false, getIsSomePageRowsSelected: () => false, toggleAllPageRowsSelected: toggleAll }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      const onUpdate = vnode.props!['onUpdate:modelValue'] as (v: boolean | 'indeterminate') => void
      onUpdate(true)
      expect(toggleAll).toHaveBeenCalledWith(true)
    })

    it('onUpdate:modelValue(false) calls toggleAllPageRowsSelected(false)', () => {
      const toggleAll = vi.fn()
      const table = { getIsAllPageRowsSelected: () => true, getIsSomePageRowsSelected: () => true, toggleAllPageRowsSelected: toggleAll }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      const onUpdate = vnode.props!['onUpdate:modelValue'] as (v: boolean | 'indeterminate') => void
      onUpdate(false)
      expect(toggleAll).toHaveBeenCalledWith(false)
    })
  })

  describe('cell row-selection', () => {
    it('modelValue is true when row is selected', () => {
      const row = { getIsSelected: () => true, toggleSelected: vi.fn() }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].cell as ColumnDef<unknown>['cell'])(makeCellCtx(row))
      expect(vnode.props).toMatchObject({ modelValue: true })
    })

    it('modelValue is false when row is not selected', () => {
      const row = { getIsSelected: () => false, toggleSelected: vi.fn() }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].cell as ColumnDef<unknown>['cell'])(makeCellCtx(row))
      expect(vnode.props).toMatchObject({ modelValue: false })
    })

    it('aria-label defaults to t("common.select")', () => {
      const row = { getIsSelected: () => false, toggleSelected: vi.fn() }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].cell as ColumnDef<unknown>['cell'])(makeCellCtx(row))
      expect(vnode.props).toMatchObject({ 'aria-label': 'common.select' })
    })

    it('onUpdate:modelValue(true) calls row.toggleSelected(true)', () => {
      const toggle = vi.fn()
      const row = { getIsSelected: () => false, toggleSelected: toggle }
      const cols = createSelectionColumn(mockT)
      const vnode = (cols[0].cell as ColumnDef<unknown>['cell'])(makeCellCtx(row))
      const onUpdate = vnode.props!['onUpdate:modelValue'] as (v: boolean | 'indeterminate') => void
      onUpdate(true)
      expect(toggle).toHaveBeenCalledWith(true)
    })
  })

  describe('custom options', () => {
    it('selectAllAriaLabel overrides the header aria-label', () => {
      const table = { getIsAllPageRowsSelected: () => true, getIsSomePageRowsSelected: () => true, toggleAllPageRowsSelected: vi.fn() }
      const cols = createSelectionColumn(mockT, { selectAllAriaLabel: '全选' })
      const vnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      expect(vnode.props).toMatchObject({ 'aria-label': '全选' })
    })

    it('selectRowAriaLabel overrides the cell aria-label', () => {
      const row = { getIsSelected: () => false, toggleSelected: vi.fn() }
      const cols = createSelectionColumn(mockT, { selectRowAriaLabel: '选择此行' })
      const vnode = (cols[0].cell as ColumnDef<unknown>['cell'])(makeCellCtx(row))
      expect(vnode.props).toMatchObject({ 'aria-label': '选择此行' })
    })

    it('checkboxClass is passed to header and cell', () => {
      const table = { getIsAllPageRowsSelected: () => false, getIsSomePageRowsSelected: () => false, toggleAllPageRowsSelected: vi.fn() }
      const row = { getIsSelected: () => false, toggleSelected: vi.fn() }
      const cls = 'border-[var(--silver-300)]'
      const cols = createSelectionColumn(mockT, { checkboxClass: cls })
      const headerVnode = (cols[0].header as ColumnDef<unknown>['header'])(makeHeaderCtx(table))
      const cellVnode = (cols[0].cell as ColumnDef<unknown>['cell'])(makeCellCtx(row))
      expect(headerVnode.props).toMatchObject({ class: cls })
      expect(cellVnode.props).toMatchObject({ class: cls })
    })
  })
})
