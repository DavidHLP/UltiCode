<script setup lang="ts">
import type { Component } from 'vue'
import { ref, watchEffect } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { ChevronRight as IconChevronRight } from '@lucide/vue'

import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubItem,
  useSidebar,
} from '@ulticode/design-system/ui/sidebar'
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent,
} from '@ulticode/design-system/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuGroup,
} from '@ulticode/design-system/ui/dropdown-menu'
import { DropdownMenuArrow } from 'reka-ui'
import { SidebarMenuSubItem as SharedSidebarMenuSubItem } from '@ulticode/sidebar-menu'

interface NavItem {
  title: string
  url: string
  icon?: Component
  items?: NavItem[]
}

const props = defineProps<{
  items: NavItem[]
  title?: string
}>()

const route = useRoute()
const { state } = useSidebar()
const openMenus = ref<Record<string, boolean>>({})

function isActive(url: string): boolean {
  const currentPath = route.path
  if (currentPath === url) return true
  if (url === '/') return currentPath === '/'
  return currentPath.startsWith(url + '/')
}

function isSubmenuActive(item: NavItem): boolean {
  if (!item.items) return false
  return item.items.some((subItem) => isActive(subItem.url))
}

// Shared activation class for every nav row (4px accent bar). Extracted so the
// three render branches (collapsed popover / expanded collapsible / plain item)
// never drift apart. NOTE: management uses a font-mono / text-xs terminal style
// that differs from shared/sidebar-menu's text-sm contract, so rows stay on the
// shadcn SidebarMenuButton here; only the duplicated class string is unified.
function itemRowClass(active: boolean): string {
  return active
    ? 'border-l-4 border-primary bg-primary/8 text-foreground pl-2 font-semibold'
    : 'border-l-4 border-transparent hover:bg-border/40 hover:text-foreground text-muted-foreground pl-2'
}

watchEffect(() => {
  props.items.forEach((item) => {
    if (item.items && isSubmenuActive(item)) {
      openMenus.value[item.title] = true
    }
  })
})
</script>

<template>
  <SidebarGroup class="py-1">
    <SidebarGroupLabel v-if="title && items.length > 0" class="px-2 py-1 mt-2 mb-1">
      {{ title }}
    </SidebarGroupLabel>
    <SidebarGroupContent class="flex flex-col gap-1">
      <SidebarMenu>
        <template v-for="item in items" :key="item.title">
          <!-- Collapsed submenu popover (DropdownMenu) -->
          <DropdownMenu v-if="state === 'collapsed' && item.items && item.items.length > 0">
            <DropdownMenuTrigger as-child>
              <SidebarMenuItem>
                <SidebarMenuButton
                  :tooltip="String(item.title)"
                  :aria-label="item.title"
                  class="h-9 w-full"
                  :class="itemRowClass(isSubmenuActive(item))"
                >
                  <component
                    :is="item.icon"
                    v-if="item.icon"
                    class="size-4 shrink-0 transition-colors"
                    :class="
                      isSubmenuActive(item)
                        ? 'text-inherit stroke-[2.5]'
                        : 'text-muted-foreground stroke-[1.8]'
                    "
                  />
                </SidebarMenuButton>
              </SidebarMenuItem>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              side="right"
              align="start"
              class="w-48 p-1.5 space-y-0.5 z-50 animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2"
            >
              <DropdownMenuGroup
                ><DropdownMenuArrow
                  class="bg-card fill-card z-50 size-2.5 translate-y-[calc(-50%_-_2px)] rotate-45"
                />
                <DropdownMenuLabel class="px-2.5 py-1.5">
                  {{ item.title }}
                </DropdownMenuLabel>
                <DropdownMenuSeparator class="mx-1" />
                <DropdownMenuItem
                  v-for="subItem in item.items"
                  :key="subItem.title"
                  as-child
                  class="p-0"
                >
                  <SharedSidebarMenuSubItem
                    :is-active="isActive(subItem.url)"
                    :to="subItem.url"
                    class="flex items-center gap-2 px-2.5 py-2 w-full text-xs font-mono rounded-lg border-l-4"
                  >
                    <component
                      :is="subItem.icon"
                      v-if="subItem.icon"
                      class="size-3.5 shrink-0"
                      :class="isActive(subItem.url) ? 'text-inherit' : 'text-muted-foreground'"
                    />
                    <span>{{ subItem.title }}</span>
                  </SharedSidebarMenuSubItem>
                </DropdownMenuItem></DropdownMenuGroup
              >
            </DropdownMenuContent>
          </DropdownMenu>

          <!-- Render collapsible submenu if items exists and expanded -->
          <Collapsible
            v-else-if="item.items && item.items.length > 0"
            v-model:open="openMenus[item.title]"
            as-child
            class="group/collapsible"
          >
            <SidebarMenuItem>
              <CollapsibleTrigger as-child>
                <SidebarMenuButton
                  :tooltip="String(item.title)"
                  :aria-label="item.title"
                  class="h-9 w-full"
                  :class="itemRowClass(isSubmenuActive(item))"
                >
                  <component
                    :is="item.icon"
                    v-if="item.icon"
                    class="size-4 shrink-0 transition-colors"
                    :class="
                      isSubmenuActive(item)
                        ? 'text-inherit stroke-[2.5]'
                        : 'text-muted-foreground group-hover/collapsible:text-foreground stroke-[1.8]'
                    "
                  />
                  <span
                    class="truncate text-xs font-medium group-data-[collapsible=icon]:hidden"
                    :class="
                      isSubmenuActive(item)
                        ? 'text-inherit font-semibold'
                        : 'text-muted-foreground group-hover/collapsible:text-foreground'
                    "
                  >
                    {{ item.title }}
                  </span>
                  <IconChevronRight
                    class="ml-auto size-3.5 shrink-0 transition-transform duration-200 text-muted-foreground group-hover/collapsible:text-foreground group-data-[collapsible=icon]:hidden"
                    :class="[
                      openMenus[item.title] ? 'rotate-90' : '',
                      isSubmenuActive(item) ? 'text-inherit' : '',
                    ]"
                  />
                </SidebarMenuButton>
              </CollapsibleTrigger>

              <CollapsibleContent>
                <SidebarMenuSub class="mt-0.5">
                  <SidebarMenuSubItem v-for="subItem in item.items" :key="subItem.title">
                    <SharedSidebarMenuSubItem
                      :is-active="isActive(subItem.url)"
                      :to="subItem.url"
                      class="flex items-center gap-2 w-full pl-3 rounded-md"
                    >
                      <component
                        :is="subItem.icon"
                        v-if="subItem.icon"
                        class="size-3.5 shrink-0 transition-colors"
                        :class="
                          isActive(subItem.url)
                            ? 'text-inherit stroke-[2.5]'
                            : 'text-muted-foreground stroke-[1.8]'
                        "
                      />
                      <span class="truncate text-xs">{{ subItem.title }}</span>
                    </SharedSidebarMenuSubItem>
                  </SidebarMenuSubItem>
                </SidebarMenuSub>
              </CollapsibleContent>
            </SidebarMenuItem>
          </Collapsible>

          <!-- Else, render standard menu item -->
          <SidebarMenuItem v-else>
            <SidebarMenuButton
              :tooltip="String(item.title)"
              :aria-label="item.title"
              :is-active="isActive(item.url)"
              as-child
              class="h-9"
              :class="itemRowClass(isActive(item.url))"
            >
              <RouterLink :to="item.url" class="flex items-center gap-2.5 w-full">
                <component
                  :is="item.icon"
                  v-if="item.icon"
                  class="size-4 shrink-0 transition-colors"
                  :class="
                    isActive(item.url)
                      ? 'text-inherit stroke-[2.5]'
                      : 'text-muted-foreground group-hover:text-foreground stroke-[1.8]'
                  "
                />
                <span
                  class="truncate text-xs font-medium group-data-[collapsible=icon]:hidden"
                  :class="
                    isActive(item.url)
                      ? 'text-inherit font-semibold'
                      : 'text-muted-foreground group-hover:text-foreground'
                  "
                >
                  {{ item.title }}
                </span>
              </RouterLink>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </template>
      </SidebarMenu>
    </SidebarGroupContent>
  </SidebarGroup>
</template>
