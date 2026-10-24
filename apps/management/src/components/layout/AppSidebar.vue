<script setup lang="ts">
import { AccountSwitcher } from '@ulticode/auth-ui'
import { accountSessions } from '@/api/accountSessions'

import {
  systemPermissionRules,
  problemPermissionRules,
  problemListPermissionRules,
  tagPermissionRules,
  contestPermissionRules,
  solutionPermissionRules,
  forumPermissionRules,
  userPermissionRules,
  commentPermissionRules,
} from '@/composables/domainPermissionMaps'
import { usePermissionMap } from '@/composables/usePermissionMap'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { useSearchPalette } from '@/composables/useSearchPalette'
import {
  Code as IconCode,
  LayoutDashboard as IconDashboard,
  FileText as IconFileDescription,
  CircleHelp as IconHelp,
  ListChecks as IconListDetails,
  Flag as IconReport,
  Search as IconSearch,
  Settings as IconSettings,
  Users as IconUsers,
  History as IconHistory,
  MessageCircle as IconMessageCircle,
  MessagesSquare as IconMessages,
  Trophy as IconTrophy,
  Tags as IconTags,
  Bell as IconBell,
  ChartBar as IconChartBar,
} from '@lucide/vue'

import NavMain from './NavMain.vue'
import NavUser from './NavUser.vue'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuItem,
  SidebarRail,
} from '@ulticode/design-system/ui/sidebar'

const { t } = useI18n()
const authStore = useAuthStore()
const { open: openSearch } = useSearchPalette()
const systemCan = { system: usePermissionMap(systemPermissionRules) }
const problemCan = { problem: usePermissionMap(problemPermissionRules) }
const problemListCan = { problemList: usePermissionMap(problemListPermissionRules) }
const tagCan = { tag: usePermissionMap(tagPermissionRules) }
const contestCan = { contest: usePermissionMap(contestPermissionRules) }
const solutionCan = { solution: usePermissionMap(solutionPermissionRules) }
const forumCan = { forum: usePermissionMap(forumPermissionRules) }
const userCan = { user: usePermissionMap(userPermissionRules) }
const commentCan = { comment: usePermissionMap(commentPermissionRules) }

const user = computed(() => ({
  name: authStore.user?.name || 'Admin',
  username: authStore.user?.username,
  email: authStore.user?.email || 'admin@ulticode.com',
  avatar: authStore.user?.avatar || '',
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
  if (systemCan.system.read.value) {
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
  if (problemCan.problem.read.value) {
    bankSubItems.push({
      title: t('nav.problems'),
      url: '/problems',
      icon: IconListDetails,
    })
  }
  if (problemListCan.problemList.read.value) {
    bankSubItems.push({
      title: t('nav.problemLists'),
      url: '/problem-lists',
      icon: IconListDetails,
    })
  }
  if (tagCan.tag.read.value) {
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
  if (contestCan.contest.read.value) {
    items.push({
      title: t('nav.contests'),
      url: '/contests',
      icon: IconTrophy,
    })
  }

  // 3. Submissions (Single item)
  if (problemCan.problem.read.value) {
    items.push({
      title: t('nav.submissions'),
      url: '/submissions',
      icon: IconCode,
    })
  }

  // 4. Discussions Group
  const discussionSubItems = []
  if (solutionCan.solution.read.value) {
    discussionSubItems.push({
      title: t('nav.solutions'),
      url: '/solutions',
      icon: IconFileDescription,
    })
  }
  if (forumCan.forum.moderatePost.value) {
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
  if (userCan.user.read.value) {
    items.push({
      title: t('nav.users'),
      url: '/users',
      icon: IconUsers,
    })
  }

  // 2. Moderation Group
  const moderationSubItems = []
  if (problemCan.problem.moderate.value) {
    moderationSubItems.push({
      title: t('nav.moderation'),
      url: '/moderation',
      icon: IconReport,
    })
  }
  if (commentCan.comment.moderateForum.value || commentCan.comment.moderateSolution.value) {
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
  if (systemCan.system.read.value) {
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

  if (systemCan.system.update.value) {
    items.push({
      title: t('nav.settings'),
      url: '/settings',
      icon: IconSettings,
    })
  }

  // Only show help and search for users with READ:SYSTEM permission
  // Basic admin users (only Dashboard + User Management) should not see these
  if (systemCan.system.read.value) {
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
  <Sidebar collapsible="icon" class="uc-sidebar-shell">
    <SidebarHeader class="uc-sidebar-header">
      <SidebarMenu>
        <SidebarMenuItem>
          <AccountSwitcher :client="accountSessions" :account-name="user.name" admin-only />
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
    <SidebarContent class="uc-sidebar-content">
      <NavMain :items="overviewItems" :title="t('nav.overview')" />
      <NavMain :items="contentItems" :title="t('nav.content')" />
      <NavMain :items="userSecurityItems" :title="t('nav.usersAndSecurity')" />
    </SidebarContent>
    <SidebarFooter class="p-2">
      <NavUser :user="user" :system-items="navSecondary" />
    </SidebarFooter>
    <SidebarRail />
  </Sidebar>
</template>
