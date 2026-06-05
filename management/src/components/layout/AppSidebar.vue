<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import {
  IconCode,
  IconDashboard,
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
  IconMessages,
  IconTrophy,
  IconTags,
  IconBell,
  IconChartBar,
} from '@tabler/icons-vue'

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
  useSidebar,
} from '@/components/ui/sidebar'

const { toggleSidebar } = useSidebar()

const { t } = useI18n()
const authStore = useAuthStore()

const user = computed(() => ({
  name: authStore.user?.name || 'Admin',
  email: authStore.user?.email || 'admin@ulticode.com',
  avatar: authStore.user?.avatar || '/avatars/default.jpg',
  role: authStore.userRole || 'ADMIN',
}))

const overviewItems = computed(() => {
  const items = [
    {
      title: t('nav.dashboard'),
      url: '/',
      icon: IconDashboard,
    },
  ]
  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: t('nav.analytics'),
      url: '/analytics',
      icon: IconChartBar,
    })
    items.push({
      title: t('nav.auditLogs'),
      url: '/audit',
      icon: IconHistory,
    })
  }
  return items
})

const contentItems = computed(() => {
  const items = []
  if (authStore.hasPermission('READ', 'PROBLEM')) {
    items.push({
      title: t('nav.problems'),
      url: '/problems',
      icon: IconListDetails,
    })
  }
  if (authStore.hasPermission('READ', 'PROBLEM_LIST')) {
    items.push({
      title: t('nav.problemLists'),
      url: '/problem-lists',
      icon: IconListDetails,
    })
  }
  if (authStore.hasPermission('READ', 'TAG')) {
    items.push({
      title: t('nav.tags'),
      url: '/tags',
      icon: IconTags,
    })
  }
  if (authStore.hasPermission('READ', 'SOLUTION')) {
    items.push({
      title: t('nav.solutions'),
      url: '/solutions',
      icon: IconFileDescription,
    })
  }
  if (authStore.hasPermission('READ', 'CONTEST')) {
    items.push({
      title: t('nav.contests'),
      url: '/contests',
      icon: IconTrophy,
    })
  }
  if (authStore.hasPermission('READ', 'PROBLEM')) {
    items.push({
      title: t('nav.submissions'),
      url: '/submissions',
      icon: IconCode,
    })
  }
  if (authStore.hasPermission('MODERATE', 'FORUM_POST')) {
    items.push({
      title: t('nav.forum'),
      url: '/forum/posts',
      icon: IconMessages,
    })
  }
  return items
})

const userSecurityItems = computed(() => {
  const items = [
    {
      title: t('nav.users'),
      url: '/users',
      icon: IconUsers,
    },
  ]
  if (authStore.hasPermission('MODERATE', 'PROBLEM')) {
    items.push({
      title: t('nav.moderation'),
      url: '/moderation',
      icon: IconReport,
    })
  }
  if (
    authStore.hasPermission('MODERATE', 'FORUM_COMMENT') ||
    authStore.hasPermission('MODERATE', 'SOLUTION_COMMENT')
  ) {
    items.push({
      title: t('nav.comments'),
      url: '/comments',
      icon: IconMessageCircle,
    })
  }
  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: t('nav.notifications'),
      url: '/notifications',
      icon: IconBell,
    })
  }
  return items
})

const navSecondary = computed(() => {
  const items = []

  if (authStore.hasPermission('UPDATE', 'SYSTEM')) {
    items.push({
      title: t('nav.settings'),
      url: '/settings',
      icon: IconSettings,
    })
  }

  // Only show help and search for users with READ:SYSTEM permission
  // Basic admin users (only Dashboard + User Management) should not see these
  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: t('nav.getHelp'),
      url: '#',
      icon: IconHelp,
    })

    items.push({
      title: t('nav.search'),
      url: '#',
      icon: IconSearch,
    })
  }

  return items
})
</script>

<template>
  <Sidebar collapsible="icon">
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton
            :tooltip="t('nav.brandName')"
            as-child
            class="data-[slot=sidebar-menu-button]:!p-1.5"
          >
            <button type="button" @click="toggleSidebar">
              <IconInnerShadowTop class="size-4 text-[var(--accent-primary)]" />
              <span class="text-base font-semibold text-foreground">{{ t('nav.brandName') }}</span>
            </button>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent class="gap-0">
      <NavMain :items="overviewItems" :title="t('nav.overview')" />
      <NavMain :items="contentItems" :title="t('nav.content')" />
      <NavMain :items="userSecurityItems" :title="t('nav.usersAndSecurity')" />
      <NavSecondary :items="navSecondary" class="mt-auto" />
    </SidebarContent>
    <SidebarFooter class="border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] p-2">
      <NavUser :user="user" />
    </SidebarFooter>
  </Sidebar>
</template>
