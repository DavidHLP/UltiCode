<script setup lang="ts">
import type { Component } from 'vue'
import { ref, watchEffect } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { ChevronRight } from '@lucide/vue'
import { SidebarBranch } from '@ulticode/sidebar-menu'
import {
  SidebarGroup,
  SidebarMenuSub,
  SidebarMenuSubItem,
  SidebarMenuSubButton,
} from '@ulticode/design-system/ui/sidebar'
import {
  Collapsible,
  CollapsibleTrigger,
  CollapsibleContent,
} from '@ulticode/design-system/ui/collapsible'

interface NavItem {
  title: string
  url: string
  icon?: Component
  items?: NavItem[]
}
const props = defineProps<{ items: NavItem[]; title?: string }>()
const route = useRoute()
const openMenus = ref<Record<string, boolean>>({})
function isActive(url: string): boolean {
  return route.path === url || (url !== '/' && route.path.startsWith(url + '/'))
}
function isSubmenuActive(item: NavItem): boolean {
  return item.items?.some((child) => isActive(child.url)) ?? false
}
watchEffect(() => {
  for (const item of props.items) if (isSubmenuActive(item)) openMenus.value[item.title] = true
})
</script>

<template>
  <SidebarGroup v-if="items.length">
    <SidebarBranch
      :title="title || items[0]!.title"
      :icon="items[0]?.icon"
      :contains-active="items.some((item) => isActive(item.url) || isSubmenuActive(item))"
    >
      <template v-for="item in items" :key="item.title">
        <Collapsible
          v-if="item.items?.length"
          :open="Boolean(openMenus[item.title])"
          as-child
          class="group/submenu"
          @update:open="openMenus[item.title] = $event"
        >
          <SidebarMenuSubItem>
            <CollapsibleTrigger as-child>
              <SidebarMenuSubButton as="button" type="button">
                <component :is="item.icon" v-if="item.icon" />
                <span>{{ item.title }}</span>
                <ChevronRight
                  class="ml-auto transition-transform duration-200 group-data-[state=open]/submenu:rotate-90"
                />
              </SidebarMenuSubButton>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <SidebarMenuSub>
                <SidebarMenuSubItem v-for="child in item.items" :key="child.title">
                  <SidebarMenuSubButton :is-active="isActive(child.url)" as-child>
                    <RouterLink :to="child.url"
                      ><component :is="child.icon" v-if="child.icon" /><span>{{
                        child.title
                      }}</span></RouterLink
                    >
                  </SidebarMenuSubButton>
                </SidebarMenuSubItem>
              </SidebarMenuSub>
            </CollapsibleContent>
          </SidebarMenuSubItem>
        </Collapsible>
        <SidebarMenuSubItem v-else>
          <SidebarMenuSubButton :is-active="isActive(item.url)" as-child>
            <RouterLink :to="item.url"
              ><component :is="item.icon" v-if="item.icon" /><span>{{
                item.title
              }}</span></RouterLink
            >
          </SidebarMenuSubButton>
        </SidebarMenuSubItem>
      </template>
    </SidebarBranch>
  </SidebarGroup>
</template>
