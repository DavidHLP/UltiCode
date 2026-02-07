<script setup lang="ts">
import type { Component } from 'vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'
import NavMain from './NavMain.vue'
import NavDocuments from './NavDocuments.vue'
import NavSecondary from './NavSecondary.vue'

export interface NavItem {
  title: string
  url: string
  icon?: Component
}

export interface Document {
  name: string
  url: string
  icon?: Component
}

defineProps<{
  brandName?: string
  brandIcon?: Component
  brandLogo?: string
  brandUrl?: string
  navMainItems?: NavItem[]
  navSecondaryItems?: NavItem[]
  documentItems?: Document[]
  documentGroupLabel?: string
  quickCreateLabel?: string
  quickCreateIcon?: Component
}>()
</script>

<template>
  <Sidebar collapsible="offcanvas">
    <SidebarHeader v-if="brandName">
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton as-child class="data-[slot=sidebar-menu-button]:!p-1.5">
            <a :href="brandUrl || '#'">
              <component v-if="brandIcon" :is="brandIcon" class="!size-5" />
              <span v-else-if="brandLogo" class="text-base font-semibold">{{ brandLogo }}</span>
              <span class="text-base font-semibold">{{ brandName }}</span>
            </a>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent>
      <NavMain
        v-if="navMainItems"
        :items="navMainItems"
        :quick-create-label="quickCreateLabel"
        :quick-create-icon="quickCreateIcon"
      >
        <template #extra-actions>
          <slot name="extra-actions" />
        </template>
      </NavMain>
      <NavDocuments v-if="documentItems" :items="documentItems" :group-label="documentGroupLabel" />
      <NavSecondary v-if="navSecondaryItems" :items="navSecondaryItems" class="mt-auto" />
    </SidebarContent>
    <SidebarFooter>
      <slot name="footer" />
    </SidebarFooter>
  </Sidebar>
</template>
