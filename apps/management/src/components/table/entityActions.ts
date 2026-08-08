import { h, type Component } from 'vue'
import { IconDotsVertical } from '@tabler/icons-vue'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

/**
 * Admin table row-action menu primitives.
 *
 * Each `columns.ts` used to rebuild the DropdownMenu trigger / content /
 * separator / sub-menu scaffolding by hand. This module owns that scaffolding
 * so a table only declares the domain actions it needs (label, icon, handler,
 * permission/visibility, destructive styling) and the menu mechanics stay in
 * one place — see architecture-review-20260716 candidate 3.
 */

export interface EntityActionItem {
  kind?: 'item'
  label: string
  onSelect: () => void
  icon?: Component
  iconClass?: string
  labelClass?: string
  hidden?: boolean
  disabled?: boolean
  /** Class on the DropdownMenuItem element itself; overrides the menu-level default. */
  itemClass?: string
}

export interface EntityActionSubmenu {
  kind: 'submenu'
  triggerLabel: string
  triggerIcon?: Component
  triggerIconClass?: string
  hidden?: boolean
  items: EntityActionItem[]
}

export interface EntityActionSeparator {
  kind: 'separator'
  hidden?: boolean
}

export type EntityActionNode = EntityActionItem | EntityActionSubmenu | EntityActionSeparator

/**
 * Menu-level styling overrides for tables that use a non-default theme variant
 * (e.g. the terminal-themed tables share hover, border, and font classes).
 * All fields are optional and fall back to the clean shadcn defaults.
 */
export interface EntityActionsMenuOptions {
  triggerClass?: string
  triggerIconClass?: string
  contentClass?: string
  /** Default class applied to every DropdownMenuItem unless the item overrides it. */
  itemClass?: string
  separatorClass?: string
  /** Screen-reader label for the trigger; defaults to 'Open menu'. */
  srLabel?: string
}

function isSeparator(node: EntityActionNode): node is EntityActionSeparator {
  return node.kind === 'separator'
}

function isSubmenu(node: EntityActionNode): node is EntityActionSubmenu {
  return node.kind === 'submenu'
}

function renderItem(item: EntityActionItem, defaultItemClass?: string) {
  return h(
    DropdownMenuItem,
    {
      onClick: item.onSelect,
      disabled: item.disabled ?? false,
      class: item.itemClass ?? defaultItemClass,
    },
    {
      default: () =>
        h('div', { class: `flex items-center gap-2 ${item.labelClass ?? ''}`.trim() }, [
          item.icon ? h(item.icon, { class: item.iconClass ?? 'h-4 w-4' }) : null,
          item.label,
        ]),
    },
  )
}

/**
 * Single owner of the menu collapse policy. Drops permission/flag-hidden
 * items, separators, and submenu triggers, filters hidden submenu children,
 * and collapses any submenu left with no visible children. `renderNode` and
 * `createEntityActionsMenu` consume only the resolved list, so the visibility
 * decision lives in one pure, testable place — see architecture-review
 * candidate 3.
 *
 * Before this existed, a permission-gated action at the top level fell through
 * to `renderItem` and stayed visible and clickable across every migrated
 * `columns.ts`.
 */
export function resolveVisibleMenuNodes(nodes: EntityActionNode[]): EntityActionNode[] {
  const resolved: EntityActionNode[] = []
  for (const node of nodes) {
    if (node.hidden) continue
    if (isSeparator(node)) {
      resolved.push(node)
      continue
    }
    if (isSubmenu(node)) {
      const items = node.items.filter((i) => !i.hidden)
      if (items.length === 0) continue
      resolved.push({ ...node, items })
      continue
    }
    resolved.push(node)
  }
  return resolved
}

function renderNode(node: EntityActionNode, options?: EntityActionsMenuOptions) {
  if (isSeparator(node)) {
    return h(DropdownMenuSeparator, { class: options?.separatorClass })
  }
  if (isSubmenu(node)) {
    return h(
      DropdownMenuSub,
      {},
      {
        default: () => [
          h(
            DropdownMenuSubTrigger,
            { class: 'gap-2' },
            {
              default: () => [
                node.triggerIcon
                  ? h(node.triggerIcon, { class: node.triggerIconClass ?? 'h-4 w-4' })
                  : null,
                node.triggerLabel,
              ],
            },
          ),
          h(
            DropdownMenuSubContent,
            {},
            { default: () => node.items.map((i) => renderItem(i, options?.itemClass)) },
          ),
        ],
      },
    )
  }
  return renderItem(node, options?.itemClass)
}

/**
 * Build the standard admin row-action dropdown from a declarative node list.
 * Visibility is resolved first by {@link resolveVisibleMenuNodes} (hidden
 * permission/flag-gated items dropped, empty sub-menus collapsed); each
 * surviving node is then rendered.
 */
export function createEntityActionsMenu(
  nodes: EntityActionNode[],
  options?: EntityActionsMenuOptions,
) {
  const rendered = resolveVisibleMenuNodes(nodes).map((n) => renderNode(n, options))
  return h(
    DropdownMenu,
    {},
    {
      default: () => [
        h(
          DropdownMenuTrigger,
          { asChild: true },
          {
            default: () =>
              h(
                Button,
                { variant: 'ghost', size: 'icon', class: options?.triggerClass ?? 'h-8 w-8 p-0' },
                {
                  default: () => [
                    h('span', { class: 'sr-only' }, options?.srLabel ?? 'Open menu'),
                    h(IconDotsVertical, { class: options?.triggerIconClass ?? 'h-4 w-4' }),
                  ],
                },
              ),
          },
        ),
        h(
          DropdownMenuContent,
          { align: 'end', class: options?.contentClass },
          { default: () => rendered },
        ),
      ],
    },
  )
}
