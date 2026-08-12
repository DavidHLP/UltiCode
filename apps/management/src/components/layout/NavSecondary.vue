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
  /**
   * Optional click handler. When set, takes precedence over navigation:
   * the link's default behaviour is prevented and the handler is invoked.
   * Useful for actions that don't correspond to a real route, e.g. opening
   * the global command palette from the sidebar's search shortcut.
   */
  onClick?: (event: MouseEvent) => void
}

defineProps<{
  items: NavItem[]
}>()

const route = useRoute()

function isActive(url: string): boolean {
  return route.path === url
}
</script>

<template>
  <SidebarGroup>
    <SidebarGroupContent>
      <SidebarMenu>
        <SidebarMenuItem v-for="item in items" :key="item.title">
          <SidebarMenuButton
            :tooltip="item.title"
            :is-active="isActive(item.url)"
            as-child
            :class="[
              isActive(item.url)
                ? 'border-l-4 border-[var(--primary)] bg-[var(--primary)]/8 text-[var(--foreground)] font-bold pl-2'
                : 'border-l-4 border-transparent',
            ]"
          >
            <a v-if="item.onClick" href="#" role="button" @click.prevent="item.onClick($event)">
              <component :is="item.icon" v-if="item.icon" />
              <span class="group-data-[collapsible=icon]:hidden">{{ item.title }}</span>
            </a>
            <RouterLink v-else-if="item.url.startsWith('/')" :to="item.url">
              <component :is="item.icon" v-if="item.icon" />
              <span class="group-data-[collapsible=icon]:hidden">{{ item.title }}</span>
            </RouterLink>
            <a v-else :href="item.url">
              <component :is="item.icon" v-if="item.icon" />
              <span class="group-data-[collapsible=icon]:hidden">{{ item.title }}</span>
            </a>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarGroupContent>
  </SidebarGroup>
</template>
