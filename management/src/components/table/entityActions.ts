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

function renderNode(node: EntityActionNode, options?: EntityActionsMenuOptions) {
  if (isSeparator(node)) {
    if (node.hidden) return null
    return h(DropdownMenuSeparator, { class: options?.separatorClass })
  }
  if (isSubmenu(node)) {
    const items = node.items.filter((i) => !i.hidden)
    if (items.length === 0) return null
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
            { default: () => items.map((i) => renderItem(i, options?.itemClass)) },
          ),
        ],
      },
    )
  }
  return renderItem(node, options?.itemClass)
}

/**
 * Build the standard admin row-action dropdown from a declarative node list.
 * Hidden items (permission/flag-gated) are dropped; empty sub-menus collapse.
 */
export function createEntityActionsMenu(
  nodes: EntityActionNode[],
  options?: EntityActionsMenuOptions,
) {
  const rendered = nodes.map((n) => renderNode(n, options)).filter((node) => node !== null)
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
