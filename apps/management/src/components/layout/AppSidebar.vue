<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useSearchPalette } from '@/composables/useSearchPalette'
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
const { open: openSearch } = useSearchPalette()

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

  // 1. Problem Bank Group
  const bankSubItems = []
  if (authStore.hasPermission('READ', 'PROBLEM')) {
    bankSubItems.push({
      title: t('nav.problems'),
      url: '/problems',
      icon: IconListDetails,
    })
  }
  if (authStore.hasPermission('READ', 'PROBLEM_LIST')) {
    bankSubItems.push({
      title: t('nav.problemLists'),
      url: '/problem-lists',
      icon: IconListDetails,
    })
  }
  if (authStore.hasPermission('READ', 'TAG')) {
    bankSubItems.push({
      title: t('nav.tags'),
      url: '/tags',
      icon: IconTags,
    })
  }

  if (bankSubItems.length > 0) {
    items.push({
      title: t('nav.problemBank'),
      url: '#',
      icon: IconListDetails,
      items: bankSubItems,
    })
  }

  // 2. Contests (Single item)
  if (authStore.hasPermission('READ', 'CONTEST')) {
    items.push({
      title: t('nav.contests'),
      url: '/contests',
      icon: IconTrophy,
    })
  }

  // 3. Submissions (Single item)
  if (authStore.hasPermission('READ', 'PROBLEM')) {
    items.push({
      title: t('nav.submissions'),
      url: '/submissions',
      icon: IconCode,
    })
  }

  // 4. Discussions Group
  const discussionSubItems = []
  if (authStore.hasPermission('READ', 'SOLUTION')) {
    discussionSubItems.push({
      title: t('nav.solutions'),
      url: '/solutions',
      icon: IconFileDescription,
    })
  }
  if (authStore.hasPermission('MODERATE', 'FORUM_POST')) {
    discussionSubItems.push({
      title: t('nav.forum'),
      url: '/forum/posts',
      icon: IconMessages,
    })
  }

  if (discussionSubItems.length > 0) {
    items.push({
      title: t('nav.discussion'),
      url: '#',
      icon: IconMessages,
      items: discussionSubItems,
    })
  }

  return items
})

const userSecurityItems = computed(() => {
  const items = []

  // 1. Users (Single item) — gated by READ:USER to match the /users route
  //    guard (router meta.permission = PERM.USER_READ). Previously pushed
  //    unconditionally, so a user without USER_READ saw the menu but was
  //    rejected by the route guard with a "no permission" toast.
  if (authStore.hasPermission('READ', 'USER')) {
    items.push({
      title: t('nav.users'),
      url: '/users',
      icon: IconUsers,
    })
  }

  // 2. Moderation Group
  const moderationSubItems = []
  if (authStore.hasPermission('MODERATE', 'PROBLEM')) {
    moderationSubItems.push({
      title: t('nav.moderation'),
      url: '/moderation',
      icon: IconReport,
    })
  }
  if (
    authStore.hasPermission('MODERATE', 'FORUM_COMMENT') ||
    authStore.hasPermission('MODERATE', 'SOLUTION_COMMENT')
  ) {
    moderationSubItems.push({
      title: t('nav.comments'),
      url: '/comments',
      icon: IconMessageCircle,
    })
  }

  if (moderationSubItems.length > 0) {
    items.push({
      title: t('nav.moderationGroup'),
      url: '#',
      icon: IconReport,
      items: moderationSubItems,
    })
  }

  // 3. Notifications (Single item)
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
      url: '/help',
      icon: IconHelp,
    })

    items.push({
      title: t('nav.search'),
      url: '/search',
      icon: IconSearch,
      onClick: openSearch,
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
    <SidebarFooter class="border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)] p-2">
      <NavUser :user="user" />
    </SidebarFooter>
  </Sidebar>
</template>
