<script setup lang="ts">
import type { Component } from 'vue'
import { useRoute, RouterLink } from 'vue-router'

import {
  SidebarGroup,
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
  <SidebarGroup>
    <SidebarGroupContent class="flex flex-col gap-2">
      <SidebarMenu>
        <SidebarMenuItem v-for="item in items" :key="item.title">
          <SidebarMenuButton :tooltip="item.title" :is-active="isActive(item.url)" as-child>
            <RouterLink :to="item.url">
              <component :is="item.icon" v-if="item.icon" />
              <span>{{ item.title }}</span>
            </RouterLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarGroupContent>
  </SidebarGroup>
</template>
