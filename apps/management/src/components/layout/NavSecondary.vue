<script setup lang="ts">
import type { Component } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Settings } from '@lucide/vue'
import { SidebarBranch } from '@ulticode/sidebar-menu'
import {
  SidebarGroup,
  SidebarMenuSubItem,
  SidebarMenuSubButton,
} from '@ulticode/design-system/ui/sidebar'
interface NavItem {
  title: string
  url: string
  icon?: Component
  onClick?: (event: MouseEvent) => void
}
defineProps<{ items: NavItem[] }>()
const route = useRoute()
const { t } = useI18n()
const isActive = (url: string) => route.path === url
</script>

<template>
  <SidebarGroup v-if="items.length">
    <SidebarBranch
      :title="t('nav.system')"
      :icon="Settings"
      :active="items.some((item) => isActive(item.url))"
    >
      <SidebarMenuSubItem v-for="item in items" :key="item.title">
        <SidebarMenuSubButton :is-active="isActive(item.url)" as-child>
          <button v-if="item.onClick" type="button" @click="item.onClick($event)">
            <component :is="item.icon" v-if="item.icon" /><span>{{ item.title }}</span>
          </button>
          <RouterLink v-else-if="item.url.startsWith('/')" :to="item.url"
            ><component :is="item.icon" v-if="item.icon" /><span>{{ item.title }}</span></RouterLink
          >
          <a v-else :href="item.url"
            ><component :is="item.icon" v-if="item.icon" /><span>{{ item.title }}</span></a
          >
        </SidebarMenuSubButton>
      </SidebarMenuSubItem>
    </SidebarBranch>
  </SidebarGroup>
</template>
