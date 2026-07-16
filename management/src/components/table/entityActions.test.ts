import { describe, it, expect } from 'vitest'
import {
  resolveVisibleMenuNodes,
  createEntityActionsMenu,
  type EntityActionNode,
  type EntityActionItem,
  type EntityActionSubmenu,
  type EntityActionSeparator,
} from './entityActions'

const item = (label: string, over: Partial<EntityActionItem> = {}): EntityActionItem => ({
  kind: 'item',
  label,
  onSelect: () => {},
  ...over,
})

const submenu = (
  triggerLabel: string,
  items: EntityActionItem[],
  over: Partial<EntityActionSubmenu> = {},
): EntityActionSubmenu => ({
  kind: 'submenu',
  triggerLabel,
  items,
  ...over,
})

const separator = (over: Partial<EntityActionSeparator> = {}): EntityActionSeparator => ({
  kind: 'separator',
  ...over,
})

describe('resolveVisibleMenuNodes', () => {
  it('keeps visible items untouched', () => {
    const nodes = [item('Edit'), item('Delete')]
    expect(resolveVisibleMenuNodes(nodes)).toEqual(nodes)
  })

  it('drops a hidden top-level item (permission-gated action regression)', () => {
    const nodes: EntityActionNode[] = [item('Edit'), item('Admin only', { hidden: true })]
    expect(resolveVisibleMenuNodes(nodes).map((n) => (n as { label: string }).label)).toEqual([
      'Edit',
    ])
  })

  it('drops hidden separators but keeps visible ones', () => {
    const nodes: EntityActionNode[] = [
      item('Edit'),
      separator({ hidden: true }),
      separator(),
      item('Delete'),
    ]
    const resolved = resolveVisibleMenuNodes(nodes)
    expect(resolved.filter((n) => n.kind === 'separator')).toHaveLength(1)
    expect(resolved.map((n) => n.kind)).toEqual(['item', 'separator', 'item'])
  })

  it('drops a hidden submenu trigger entirely', () => {
    const nodes: EntityActionNode[] = [submenu('More', [item('X')], { hidden: true }), item('Edit')]
    expect(resolveVisibleMenuNodes(nodes).map((n) => n.kind)).toEqual(['item'])
  })

  it('collapses a submenu whose children are all hidden', () => {
    const nodes: EntityActionNode[] = [item('Edit'), submenu('More', [item('a', { hidden: true })])]
    expect(resolveVisibleMenuNodes(nodes).map((n) => n.kind)).toEqual(['item'])
  })

  it('filters hidden submenu children but keeps the submenu when some remain', () => {
    const nodes: EntityActionNode[] = [
      submenu('More', [item('a'), item('b', { hidden: true }), item('c')]),
    ]
    const resolved = resolveVisibleMenuNodes(nodes)
    expect(resolved).toHaveLength(1)
    const sub = resolved[0] as EntityActionSubmenu
    expect(sub.items.map((i) => i.label)).toEqual(['a', 'c'])
  })

  it('preserves order across a mixed node list', () => {
    const nodes: EntityActionNode[] = [
      item('Edit'),
      separator(),
      submenu('More', [item('a', { hidden: true }), item('b')]),
      item('Hidden', { hidden: true }),
      item('Delete'),
    ]
    const resolved = resolveVisibleMenuNodes(nodes)
    expect(resolved.map((n) => n.kind)).toEqual(['item', 'separator', 'submenu', 'item'])
  })

  it('returns a new submenu object so the caller node list is not mutated', () => {
    const sub = submenu('More', [item('a', { hidden: true }), item('b')])
    const resolved = resolveVisibleMenuNodes([sub])
    expect(resolved[0]).not.toBe(sub)
    // original node retains its hidden child
    expect(sub.items).toHaveLength(2)
  })
})

describe('createEntityActionsMenu', () => {
  it('returns a Vue VNode and does not throw for a visible action set', () => {
    const vnode = createEntityActionsMenu([item('Edit'), separator(), item('Delete')])
    expect(vnode).toBeTruthy()
    expect((vnode as { __v_isVNode?: boolean }).__v_isVNode).toBe(true)
  })

  it('renders nothing actionable when every node is hidden (no throw)', () => {
    const vnode = createEntityActionsMenu([item('a', { hidden: true })])
    expect(vnode).toBeTruthy()
    expect((vnode as { __v_isVNode?: boolean }).__v_isVNode).toBe(true)
  })
})
