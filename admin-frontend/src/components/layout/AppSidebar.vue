<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/admin/auth'
import {
  IconCamera,
  IconDashboard,
  IconDatabase,
  IconFileAi,
  IconFileDescription,
  IconHelp,
  IconInnerShadowTop,
  IconListDetails,
  IconReport,
  IconSearch,
  IconSettings,
  IconUsers,
  IconHistory,
  IconMessageCircle,
  IconTrophy,
} from '@tabler/icons-vue'

import NavDocuments from './NavDocuments.vue'
import NavMain from './NavMain.vue'
import NavSecondary from './NavSecondary.vue'
import NavUser from './NavUser.vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/components/ui/sidebar'

const authStore = useAuthStore()

const user = computed(() => ({
  name: authStore.user?.name || 'Admin',
  email: authStore.user?.email || 'admin@ulticode.com',
  avatar: authStore.user?.avatar || '/avatars/default.jpg',
}))

const navMain = computed(() => {
  const items = [
    {
      title: 'Dashboard',
      url: '/',
      icon: IconDashboard,
    },
    {
      title: 'Users',
      url: '/users',
      icon: IconUsers,
    },
  ]

  // Add Problems if user has permission
  if (authStore.hasPermission('READ', 'PROBLEM')) {
    items.push({
      title: 'Problems',
      url: '/problems',
      icon: IconListDetails,
    })
  }

  // Add Solutions if user has permission
  if (authStore.hasPermission('READ', 'SOLUTION')) {
    items.push({
      title: 'Solutions',
      url: '/solutions',
      icon: IconFileDescription,
    })
  }

  // Add Contests if user has permission
  if (authStore.hasPermission('READ', 'CONTEST')) {
    items.push({
      title: 'Contests',
      url: '/contests',
      icon: IconTrophy,
    })
  }

  // Add Comments if user has permission
  if (
    authStore.hasPermission('MODERATE', 'FORUM_COMMENT') ||
    authStore.hasPermission('MODERATE', 'SOLUTION_COMMENT')
  ) {
    items.push({
      title: 'Comments',
      url: '/comments',
      icon: IconMessageCircle,
    })
  }

  // Add Audit Logs only if user has permission
  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: 'Audit Logs',
      url: '/audit',
      icon: IconHistory,
    })
  }

  return items
})

const data = {
  navClouds: [
    {
      title: 'Capture',
      icon: IconCamera,
      isActive: true,
      url: '#',
      items: [
        {
          title: 'Active Proposals',
          url: '#',
        },
        {
          title: 'Archived',
          url: '#',
        },
      ],
    },
    {
      title: 'Proposal',
      icon: IconFileDescription,
      url: '#',
      items: [
        {
          title: 'Active Proposals',
          url: '#',
        },
        {
          title: 'Archived',
          url: '#',
        },
      ],
    },
    {
      title: 'Prompts',
      icon: IconFileAi,
      url: '#',
      items: [
        {
          title: 'Active Proposals',
          url: '#',
        },
        {
          title: 'Archived',
          url: '#',
        },
      ],
    },
  ],
  navSecondary: [
    {
      title: 'Settings',
      url: '#',
      icon: IconSettings,
    },
    {
      title: 'Get Help',
      url: '#',
      icon: IconHelp,
    },
    {
      title: 'Search',
      url: '#',
      icon: IconSearch,
    },
  ],
  documents: [
    {
      name: 'Data Library',
      url: '#',
      icon: IconDatabase,
    },
    {
      name: 'Reports',
      url: '#',
      icon: IconReport,
    },
    {
      name: 'Word Assistant',
      url: '#',
      icon: IconFileDescription,
    },
  ],
}
</script>

<template>
  <Sidebar collapsible="offcanvas">
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton as-child class="data-[slot=sidebar-menu-button]:!p-1.5">
            <a href="#">
              <IconInnerShadowTop class="!size-5" />
              <span class="text-base font-semibold">Acme Inc.</span>
            </a>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent>
      <NavMain :items="navMain" />
      <NavDocuments :items="data.documents" />
      <NavSecondary :items="data.navSecondary" class="mt-auto" />
    </SidebarContent>
    <SidebarFooter>
      <NavUser :user="user" />
    </SidebarFooter>
  </Sidebar>
</template>
