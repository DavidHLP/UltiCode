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
                ? 'border-l-2 border-[var(--accent-electric)] bg-[var(--accent-electric)]/10 text-[var(--foreground)] font-bold pl-1.5'
                : 'border-l-2 border-transparent'
            ]"
          >
            <RouterLink v-if="item.url.startsWith('/')" :to="item.url">
              <component :is="item.icon" v-if="item.icon" />
              <span>{{ item.title }}</span>
            </RouterLink>
            <a v-else :href="item.url">
              <component :is="item.icon" v-if="item.icon" />
              <span>{{ item.title }}</span>
            </a>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarGroupContent>
  </SidebarGroup>
</template>
