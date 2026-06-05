<script setup lang="ts">
import type { Component } from 'vue'
import { useRoute, RouterLink } from 'vue-router'

import {
  SidebarGroup,
  SidebarGroupLabel,
  SidebarGroupContent,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'

interface NavItem {
  title: string
  url: string
  icon?: Component
}

defineProps<{
  items: NavItem[]
  title?: string
}>()

const route = useRoute()

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
</script>

<template>
  <SidebarGroup class="py-1">
    <SidebarGroupLabel
      v-if="title && items.length > 0"
      class="px-2 py-1 text-[10px] font-mono font-bold uppercase tracking-wider text-[var(--silver-400)] dark:text-[var(--silver-500)] mt-2 mb-1"
    >
      {{ title }}
    </SidebarGroupLabel>
    <SidebarGroupContent class="flex flex-col gap-1">
      <SidebarMenu>
        <SidebarMenuItem v-for="item in items" :key="item.title">
          <SidebarMenuButton
            :tooltip="String(item.title)"
            :is-active="isActive(item.url)"
            as-child
            class="h-9 transition-all duration-200"
            :class="[
              isActive(item.url)
                ? 'border-l-4 border-[var(--accent-primary)] bg-[var(--accent-primary)]/8 text-[var(--accent-primary)] pl-2 font-semibold'
                : 'border-l-4 border-transparent hover:bg-[var(--silver-200)]/40 hover:text-foreground text-[var(--silver-500)] pl-2'
            ]"
          >
            <RouterLink :to="item.url" class="flex items-center gap-2.5 w-full">
              <component
                :is="item.icon"
                v-if="item.icon"
                class="size-4 shrink-0 transition-colors"
                :class="isActive(item.url) ? 'text-[var(--accent-primary)] stroke-[2.5]' : 'text-[var(--silver-400)] group-hover:text-foreground stroke-[1.8]'"
              />
              <span
                class="truncate text-xs font-medium"
                :class="isActive(item.url) ? 'text-[var(--accent-primary)] font-semibold' : 'text-[var(--silver-500)] group-hover:text-foreground'"
              >
                {{ item.title }}
              </span>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarGroupContent>
  </SidebarGroup>
</template>
