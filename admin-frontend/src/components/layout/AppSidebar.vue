<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  IconDashboard,
  IconUsers,
  IconFileText,
  IconTrophy,
  IconMessage,
  IconChartBar,
} from '@tabler/icons-vue'
import { useAuthStore } from '@/stores/admin/auth'
import NavUser from '@/template/dashboard/NavUser.vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'

const router = useRouter()
const authStore = useAuthStore()

// Compute navigation items based on user permissions
const navItems = computed(() => {
  const items = [
    {
      title: 'Dashboard',
      url: '/',
      icon: IconDashboard,
      show: true,
    },
  ]

  if (authStore.hasPermission('READ', 'USER')) {
    items.push({
      title: 'Users',
      url: '/users',
      icon: IconUsers,
      show: true,
    })
  }

  if (authStore.hasPermission('READ', 'PROBLEM')) {
    items.push({
      title: 'Problems',
      url: '/problems',
      icon: IconFileText,
      show: true,
    })
  }

  if (authStore.hasPermission('READ', 'CONTEST')) {
    items.push({
      title: 'Contests',
      url: '/contests',
      icon: IconTrophy,
      show: true,
    })
  }

  if (
    authStore.hasPermission('MODERATE', 'SOLUTION') ||
    authStore.hasPermission('MODERATE', 'FORUM_POST')
  ) {
    items.push({
      title: 'Moderation',
      url: '/moderation',
      icon: IconMessage,
      show: true,
    })
  }

  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: 'Audit Logs',
      url: '/audit',
      icon: IconChartBar,
      show: true,
    })
  }

  return items
})

function navigate(url: string) {
  router.push(url)
}
</script>

<template>
  <Sidebar collapsible="offcanvas">
    <SidebarHeader>
      <SidebarMenuButton size="lg" @click="navigate('/')">
        <div
          class="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground"
        >
          <span class="text-xl font-bold">UC</span>
        </div>
        <div class="flex flex-col gap-0.5 leading-none">
          <h1 class="font-semibold">UltiCode Admin</h1>
          <p class="text-xs">Management Panel</p>
        </div>
      </SidebarMenuButton>
    </SidebarHeader>

    <SidebarContent>
      <SidebarMenu>
        <SidebarMenuItem v-for="item in navItems" :key="item.title">
          <SidebarMenuButton @click="navigate(item.url)">
            <component :is="item.icon" />
            <span>{{ item.title }}</span>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarContent>

    <SidebarFooter>
      <NavUser v-if="authStore.user" :user="authStore.user" />
    </SidebarFooter>
  </Sidebar>
</template>
