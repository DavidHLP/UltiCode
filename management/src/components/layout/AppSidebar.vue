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
}))

const navMain = computed(() => {
  const items = [
    {
      title: t('nav.dashboard'),
      url: '/',
      icon: IconDashboard,
    },
    {
      title: t('nav.users'),
      url: '/users',
      icon: IconUsers,
    },
  ]

  // Add Problems if user has permission
  if (authStore.hasPermission('READ', 'PROBLEM')) {
    items.push({
      title: t('nav.problems'),
      url: '/problems',
      icon: IconListDetails,
    })
  }

  // Add Moderation if user has permission
  if (authStore.hasPermission('MODERATE', 'PROBLEM')) {
    items.push({
      title: t('nav.moderation'),
      url: '/moderation',
      icon: IconReport,
    })
  }

  // Add Problem Lists if user has permission
  if (authStore.hasPermission('READ', 'PROBLEM_LIST')) {
    items.push({
      title: t('nav.problemLists'),
      url: '/problem-lists',
      icon: IconListDetails,
    })
  }

  // Add Tags if user has permission
  if (authStore.hasPermission('READ', 'TAG')) {
    items.push({
      title: t('nav.tags'),
      url: '/tags',
      icon: IconTags,
    })
  }

  // Add Solutions if user has permission
  if (authStore.hasPermission('READ', 'SOLUTION')) {
    items.push({
      title: t('nav.solutions'),
      url: '/solutions',
      icon: IconFileDescription,
    })
  }

  // Add Contests if user has permission
  if (authStore.hasPermission('READ', 'CONTEST')) {
    items.push({
      title: t('nav.contests'),
      url: '/contests',
      icon: IconTrophy,
    })
  }

  // Add Submissions if user has permission
  if (authStore.hasPermission('READ', 'PROBLEM')) {
    items.push({
      title: t('nav.submissions'),
      url: '/submissions',
      icon: IconCode,
    })
  }

  // Add Forum if user has permission
  if (authStore.hasPermission('MODERATE', 'FORUM_POST')) {
    items.push({
      title: t('nav.forum'),
      url: '/forum/posts',
      icon: IconMessages,
    })
  }

  // Add Comments if user has permission
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

  // Add Notifications if user has permission
  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: t('nav.notifications'),
      url: '/notifications',
      icon: IconBell,
    })
  }

  // Add Audit Logs only if user has permission
  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: t('nav.auditLogs'),
      url: '/audit',
      icon: IconHistory,
    })
  }

  // Add Analytics if user has permission
  if (authStore.hasPermission('READ', 'SYSTEM')) {
    items.push({
      title: t('nav.analytics'),
      url: '/analytics',
      icon: IconChartBar,
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
              <IconInnerShadowTop class="size-4" />
              <span class="text-base font-semibold">{{ t('nav.brandName') }}</span>
            </button>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent>
      <NavMain :items="navMain" />
      <NavSecondary :items="navSecondary" class="mt-auto" />
    </SidebarContent>
    <SidebarFooter>
      <NavUser :user="user" />
    </SidebarFooter>
  </Sidebar>
</template>
