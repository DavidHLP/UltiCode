<script setup lang="ts">
import type { Component } from 'vue'
import { ref, watchEffect } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { IconChevronRight } from '@tabler/icons-vue'

import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubItem,
  SidebarMenuSubButton,
  useSidebar,
} from '@/components/ui/sidebar'
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent,
} from '@/components/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu'
import { DropdownMenuArrow } from 'reka-ui'

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

/**
 * Check if the given URL matches the current route.
 * Handles both exact matches and prefix matches for nested routes.
 */
function isActive(url: string): boolean {
  const currentPath = route.path

  // Exact match
  if (currentPath === url) {
    return true
  }

  // Handle root url special case to avoid matching everything
  if (url === '/') {
    return currentPath === '/'
  }

  // Check if current path starts with url + '/' to ensure we don't match /users-extra against /users
  return currentPath.startsWith(url + '/')
}

/**
 * Check if any sub-item in a menu item is currently active.
 */
function isSubmenuActive(item: NavItem): boolean {
  if (!item.items) return false
  return item.items.some((subItem) => isActive(subItem.url))
}

// Automatically open parent submenu on initial load if any child is active
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
    <SidebarGroupLabel
      v-if="title && items.length > 0"
      class="px-2 py-1 text-2xs font-mono font-bold uppercase tracking-wider text-[var(--silver-400)] dark:text-[var(--silver-500)] mt-2 mb-1"
    >
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
                  class="h-9 transition-all duration-200 w-full"
                  :class="[
                    isSubmenuActive(item)
                      ? 'border-l-4 border-[var(--accent-primary)] bg-[var(--accent-primary)]/8 text-[var(--accent-primary)] pl-2 font-semibold'
                      : 'border-l-4 border-transparent hover:bg-[var(--silver-200)]/40 hover:text-foreground text-[var(--silver-500)] pl-2'
                  ]"
                >
                  <component
                    :is="item.icon"
                    v-if="item.icon"
                    class="size-4 shrink-0 transition-colors"
                    :class="
                      isSubmenuActive(item)
                        ? 'text-[var(--accent-primary)] stroke-[2.5]'
                        : 'text-[var(--silver-400)] stroke-[1.8]'
                    "
                  />
                </SidebarMenuButton>
              </SidebarMenuItem>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              variant="terminal"
              side="right"
              align="start"
              class="w-48 p-1.5 space-y-0.5 z-50 animate-in fade-in-0 zoom-in-95 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=closed]:zoom-out-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2"
            >
              <DropdownMenuArrow class="bg-[var(--card)] fill-[var(--card)] z-50 size-2.5 translate-y-[calc(-50%_-_2px)] rotate-45" />
              <DropdownMenuLabel class="px-2.5 py-1.5 text-2xs font-mono font-bold uppercase tracking-wider text-muted-foreground">
                {{ item.title }}
              </DropdownMenuLabel>
              <DropdownMenuSeparator class="bg-border mx-1" />
              <DropdownMenuItem
                v-for="subItem in item.items"
                :key="subItem.title"
                as-child
                class="p-0 rounded-none focus:bg-accent focus:text-accent-foreground"
              >
                <RouterLink
                  :to="subItem.url"
                  class="flex items-center gap-2 px-2.5 py-2 w-full text-xs font-mono text-popover-foreground hover:text-popover-foreground transition-colors"
                  :class="[
                    isActive(subItem.url)
                      ? 'bg-accent/10 text-[var(--accent-primary)] font-semibold border-l-2 border-[var(--accent-primary)] pl-1.5'
                      : ''
                  ]"
                >
                  <component
                    :is="subItem.icon"
                    v-if="subItem.icon"
                    class="size-3.5 shrink-0"
                    :class="isActive(subItem.url) ? 'text-[var(--accent-primary)]' : 'text-muted-foreground'"
                  />
                  <span>{{ subItem.title }}</span>
                </RouterLink>
              </DropdownMenuItem>
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
                  class="h-9 transition-all duration-200 w-full"
                  :class="[
                    isSubmenuActive(item)
                      ? 'border-l-4 border-[var(--accent-primary)] bg-[var(--accent-primary)]/8 text-[var(--accent-primary)] pl-2 font-semibold'
                      : 'border-l-4 border-transparent hover:bg-[var(--silver-200)]/40 hover:text-foreground text-[var(--silver-500)] pl-2'
                  ]"
                >
                  <component
                    :is="item.icon"
                    v-if="item.icon"
                    class="size-4 shrink-0 transition-colors"
                    :class="
                      isSubmenuActive(item)
                        ? 'text-[var(--accent-primary)] stroke-[2.5]'
                        : 'text-[var(--silver-400)] group-hover/collapsible:text-foreground stroke-[1.8]'
                    "
                  />
                  <span
                    class="truncate text-xs font-medium group-data-[collapsible=icon]:hidden"
                    :class="
                      isSubmenuActive(item)
                        ? 'text-[var(--accent-primary)] font-semibold'
                        : 'text-[var(--silver-500)] group-hover/collapsible:text-foreground'
                    "
                  >
                    {{ item.title }}
                  </span>
                  <IconChevronRight
                    class="ml-auto size-3.5 shrink-0 transition-transform duration-200 text-[var(--silver-400)] group-hover/collapsible:text-foreground group-data-[collapsible=icon]:hidden"
                    :class="[
                      openMenus[item.title] ? 'rotate-90' : '',
                      isSubmenuActive(item) ? 'text-[var(--accent-primary)]' : '',
                    ]"
                  />
                </SidebarMenuButton>
              </CollapsibleTrigger>

              <CollapsibleContent
                class="transition-all duration-200 data-[state=closed]:animate-collapsible-up data-[state=open]:animate-collapsible-down overflow-hidden"
              >
                <SidebarMenuSub
                  class="mt-0.5 border-[var(--silver-200)] dark:border-[var(--silver-300)]/50"
                >
                  <SidebarMenuSubItem v-for="subItem in item.items" :key="subItem.title">
                    <SidebarMenuSubButton
                      :is-active="isActive(subItem.url)"
                      as-child
                      class="h-8 transition-all duration-200 pl-3 rounded-md"
                      :class="[
                        isActive(subItem.url)
                          ? 'bg-[var(--accent-primary)]/8 text-[var(--accent-primary)] font-semibold border-l-2 border-[var(--accent-primary)] pl-2.5'
                          : 'hover:bg-[var(--silver-200)]/40 hover:text-foreground text-[var(--silver-500)] border-l border-transparent',
                      ]"
                    >
                      <RouterLink :to="subItem.url" class="flex items-center gap-2 w-full">
                        <component
                          :is="subItem.icon"
                          v-if="subItem.icon"
                          class="size-3.5 shrink-0 transition-colors"
                          :class="
                            isActive(subItem.url)
                              ? 'text-[var(--accent-primary)] stroke-[2.5]'
                              : 'text-[var(--silver-400)] stroke-[1.8]'
                          "
                        />
                        <span class="truncate text-xs">{{ subItem.title }}</span>
                      </RouterLink>
                    </SidebarMenuSubButton>
                  </SidebarMenuSubItem>
                </SidebarMenuSub>
              </CollapsibleContent>
            </SidebarMenuItem>
          </Collapsible>

          <!-- Else, render standard menu item -->
          <SidebarMenuItem v-else>
            <SidebarMenuButton
              :tooltip="String(item.title)"
              :is-active="isActive(item.url)"
              as-child
              class="h-9 transition-all duration-200"
              :class="[
                isActive(item.url)
                  ? 'border-l-4 border-[var(--accent-primary)] bg-[var(--accent-primary)]/8 text-[var(--accent-primary)] pl-2 font-semibold'
                  : 'border-l-4 border-transparent hover:bg-[var(--silver-200)]/40 hover:text-foreground text-[var(--silver-500)] pl-2',
              ]"
            >
              <RouterLink :to="item.url" class="flex items-center gap-2.5 w-full">
                <component
                  :is="item.icon"
                  v-if="item.icon"
                  class="size-4 shrink-0 transition-colors"
                  :class="
                    isActive(item.url)
                      ? 'text-[var(--accent-primary)] stroke-[2.5]'
                      : 'text-[var(--silver-400)] group-hover:text-foreground stroke-[1.8]'
                  "
                />
                <span
                  class="truncate text-xs font-medium group-data-[collapsible=icon]:hidden"
                  :class="
                    isActive(item.url)
                      ? 'text-[var(--accent-primary)] font-semibold'
                      : 'text-[var(--silver-500)] group-hover:text-foreground'
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

