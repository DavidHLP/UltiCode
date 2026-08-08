/**
 * @ulticode/sidebar-menu — shared sidebar visual contract.
 *
 * Activation visuals come from `.uc-sidebar-*` CSS + [data-active] attributes
 * (see ./styles/sidebar-menu.css), imported once per app from its style.css
 * AFTER design-system/tailwind so utility classes cannot override the
 * activation bar.
 *
 * Importing apps must alias the local shadcn `SidebarMenuItem` etc. to avoid
 * name clashes, e.g.
 *   import { SidebarMenuItem as SharedSidebarMenuItem } from '@/shared/sidebar-menu/src'
 *
 * Consumption status:
 * - SidebarMenuItem / SidebarMenuSubItem / SidebarGroupCollapsible / SidebarParentItem:
 *   consumed by console and/or management.
 * - SidebarNavUser / SidebarIconButton: @beta — exported for future adoption,
 *   NOT yet wired into either app. SidebarIconButton's hover-reveal also
 *   requires a plain `.group` or named `group/*` ancestor (see CSS).
 */
export { default as SidebarMenuItem } from './components/SidebarMenuItem.vue'
export { default as SidebarMenuSubItem } from './components/SidebarMenuSubItem.vue'
export { default as SidebarGroupCollapsible } from './components/SidebarGroupCollapsible.vue'
export { default as SidebarParentItem } from './components/SidebarParentItem.vue'
export { default as SidebarNavUser } from './components/SidebarNavUser.vue'
export { default as SidebarIconButton } from './components/SidebarIconButton.vue'
export { cn, isExactOrStartsWith } from './utils'
export type { SidebarItemActiveFn, SidebarUser } from './utils'
