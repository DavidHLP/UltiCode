import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * Route naming convention:
 * - List views: `{resource}` (e.g., 'problems', 'users')
 * - Create views: `{resource}-create` (e.g., 'problem-create')
 * - Detail views: `{resource}-detail` (e.g., 'problem-detail')
 * - Edit views: `{resource}-edit` (e.g., 'problem-edit')
 *
 * For views with tabs, use query params or path segments:
 * - `/problems/:id?tab=description` or `/problems/:id/description`
 */

const routes: RouteRecordRaw[] = [
  // ==================== Auth ====================
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { requiresAuth: false },
  },

  // ==================== Main App ====================
  {
    path: '/',
    component: () => import('@/components/layout/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      // Dashboard
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { titleKey: 'nav.dashboard' },
      },

      // ==================== Users ====================
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/users/UsersListView.vue'),
        meta: { titleKey: 'nav.users', permission: { action: 'READ', resource: 'USER' } },
      },

      // ==================== Audit Logs ====================
      {
        path: 'audit',
        name: 'audit',
        component: () => import('@/views/audit/AuditLogsView.vue'),
        meta: { titleKey: 'nav.auditLogs', permission: { action: 'READ', resource: 'SYSTEM' } },
      },

      // ==================== Analytics ====================
      {
        path: 'analytics',
        name: 'analytics',
        component: () => import('@/views/analytics/AnalyticsView.vue'),
        meta: { titleKey: 'nav.analytics', permission: { action: 'READ', resource: 'SYSTEM' } },
      },

      // ==================== Problems ====================
      {
        path: 'problems',
        name: 'problems',
        component: () => import('@/views/problems/ProblemsListView.vue'),
        meta: { titleKey: 'nav.problems', permission: { action: 'READ', resource: 'PROBLEM' } },
      },
      {
        path: 'problems/create',
        name: 'problem-create',
        component: () => import('@/views/problems/ProblemCreateView.vue'),
        meta: {
          titleKey: 'problems.createTitle',
          permission: { action: 'CREATE', resource: 'PROBLEM' },
        },
      },
      // Problem detail view (handles all tabs via route detection)
      {
        path: 'problems/:id/:tab?',
        name: 'problem-detail',
        component: () => import('@/views/problems/ProblemDetailView.vue'),
        meta: {
          titleKey: 'problems.detailTitle',
          permission: { action: 'READ', resource: 'PROBLEM' },
        },
        props: true,
      },
      // Problem edit views
      {
        path: 'problems/:id/edit/:tab?',
        name: 'problem-edit',
        component: () => import('@/views/problems/ProblemEditView.vue'),
        meta: {
          titleKey: 'problems.editTitle',
          permission: { action: 'UPDATE', resource: 'PROBLEM' },
        },
        props: true,
      },

      // ==================== Moderation ====================
      {
        path: 'moderation',
        name: 'moderation',
        component: () => import('@/views/moderation/ModerationQueueView.vue'),
        meta: {
          titleKey: 'nav.moderation',
          permission: { action: 'MODERATE', resource: 'PROBLEM' },
        },
      },
      {
        path: 'moderation/dashboard',
        name: 'moderation-dashboard',
        component: () => import('@/views/moderation/ModerationDashboardView.vue'),
        meta: {
          titleKey: 'moderation.stats.title',
          permission: { action: 'MODERATE', resource: 'PROBLEM' },
        },
      },
      {
        path: 'moderation/reports',
        name: 'moderation-reports',
        component: () => import('@/views/moderation/ReportsView.vue'),
        meta: {
          titleKey: 'moderation.reports.title',
          permission: { action: 'MODERATE', resource: 'PROBLEM' },
        },
      },
      {
        path: 'moderation/appeals',
        name: 'moderation-appeals',
        component: () => import('@/views/moderation/AppealsView.vue'),
        meta: {
          titleKey: 'moderation.appeals.title',
          permission: { action: 'MODERATE', resource: 'PROBLEM' },
        },
      },

      // ==================== Problem Lists ====================
      {
        path: 'problem-lists',
        name: 'problem-lists',
        component: () => import('@/views/problem-lists/ProblemListsListView.vue'),
        meta: {
          titleKey: 'nav.problemLists',
          permission: { action: 'READ', resource: 'PROBLEM_LIST' },
        },
      },
      {
        path: 'problem-lists/create',
        name: 'problem-list-create',
        component: () => import('@/views/problem-lists/ProblemListDetailView.vue'),
        meta: {
          titleKey: 'problemLists.createTitle',
          permission: { action: 'CREATE', resource: 'PROBLEM_LIST' },
        },
      },
      {
        path: 'problem-lists/:id/edit',
        name: 'problem-list-edit',
        component: () => import('@/views/problem-lists/ProblemListDetailView.vue'),
        meta: {
          titleKey: 'problemLists.editTitle',
          permission: { action: 'UPDATE', resource: 'PROBLEM_LIST' },
        },
        props: true,
      },

      // ==================== Solutions ====================
      {
        path: 'solutions',
        name: 'solutions',
        component: () => import('@/views/solutions/SolutionsListView.vue'),
        meta: { titleKey: 'nav.solutions', permission: { action: 'READ', resource: 'SOLUTION' } },
      },
      // Solution detail view (handles all tabs via route detection)
      {
        path: 'solutions/:id/:tab?',
        name: 'solution-detail',
        component: () => import('@/views/solutions/SolutionDetailView.vue'),
        meta: {
          titleKey: 'solutions.detailTitle',
          permission: { action: 'READ', resource: 'SOLUTION' },
        },
        props: true,
      },

      // ==================== Comments ====================
      {
        path: 'comments',
        name: 'comments',
        component: () => import('@/views/comments/CommentsListView.vue'),
        meta: {
          titleKey: 'nav.comments',
          permission: [
            { action: 'MODERATE', resource: 'FORUM_COMMENT' },
            { action: 'MODERATE', resource: 'SOLUTION_COMMENT' },
          ],
        },
      },

      // ==================== Tags ====================
      {
        path: 'tags',
        name: 'tags',
        component: () => import('@/views/tags/TagsListView.vue'),
        meta: { titleKey: 'nav.tags', permission: { action: 'READ', resource: 'TAG' } },
      },

      // ==================== Forum ====================
      {
        path: 'forum',
        redirect: 'forum/posts',
      },
      {
        path: 'forum/posts',
        name: 'forum-posts',
        component: () => import('@/views/forum/ForumPostsListView.vue'),
        meta: {
          titleKey: 'forum.postsTitle',
          permission: { action: 'MODERATE', resource: 'FORUM_POST' },
        },
      },
      // Forum post detail view (handles all tabs via route detection)
      {
        path: 'forum/posts/:id/:tab?',
        name: 'forum-post-detail',
        component: () => import('@/views/forum/ForumPostDetailView.vue'),
        meta: {
          titleKey: 'forum.detailTitle',
          permission: { action: 'MODERATE', resource: 'FORUM_POST' },
        },
        props: true,
      },

      // ==================== Contests ====================
      {
        path: 'contests',
        name: 'contests',
        component: () => import('@/views/contests/ContestsListView.vue'),
        meta: { titleKey: 'nav.contests', permission: { action: 'READ', resource: 'CONTEST' } },
      },
      {
        path: 'contests/:id',
        name: 'contest-detail',
        component: () => import('@/views/contests/ContestDetailView.vue'),
        meta: {
          titleKey: 'contests.detailTitle',
          permission: { action: 'READ', resource: 'CONTEST' },
        },
        props: true,
      },
      {
        path: 'scoring-rules',
        name: 'scoring-rules',
        component: () => import('@/views/contests/ScoringRulesView.vue'),
        meta: {
          titleKey: 'contests.scoringRules',
          permission: { action: 'READ', resource: 'CONTEST' },
        },
      },

      // ==================== Submissions ====================
      {
        path: 'submissions',
        name: 'submissions',
        component: () => import('@/views/submissions/SubmissionsView.vue'),
        meta: { titleKey: 'nav.submissions', permission: { action: 'READ', resource: 'PROBLEM' } },
      },

      // ==================== Settings ====================
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { titleKey: 'nav.settings', permission: { action: 'UPDATE', resource: 'SYSTEM' } },
      },

      // ==================== Notifications ====================
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/notifications/NotificationsListView.vue'),
        meta: { titleKey: 'nav.notifications', permission: { action: 'READ', resource: 'SYSTEM' } },
      },

      // ==================== System ====================
      {
        path: 'monitoring',
        name: 'monitoring',
        component: () => import('@/views/system/MonitoringView.vue'),
        meta: { titleKey: 'nav.monitoring', permission: { action: 'READ', resource: 'SYSTEM' } },
      },
      {
        path: 'backup',
        name: 'backup',
        component: () => import('@/views/system/BackupView.vue'),
        meta: { titleKey: 'nav.backup', permission: { action: 'UPDATE', resource: 'SYSTEM' } },
      },
      {
        path: 'email',
        name: 'email',
        component: () => import('@/views/system/EmailView.vue'),
        meta: { titleKey: 'nav.email', permission: { action: 'UPDATE', resource: 'SYSTEM' } },
      },

      // ==================== Account ====================
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/account/AccountView.vue'),
        meta: { titleKey: 'nav.account' },
      },
      {
        path: 'billing',
        name: 'billing',
        component: () => import('@/views/billing/BillingView.vue'),
        meta: { titleKey: 'nav.billing' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

// Navigation guard for authentication and permissions
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // Initialize auth store on first navigation
  if (!authStore.isInitialized) {
    await authStore.initialize()
  }

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth !== false)

  if (requiresAuth && !authStore.isAuthenticated) {
    return next({
      name: 'login',
      query: { redirect: to.fullPath },
    })
  }

  // Check permissions if route requires them
  if (to.meta.permission && authStore.isAuthenticated) {
    const permissions = Array.isArray(to.meta.permission)
      ? to.meta.permission
      : [to.meta.permission]

    const hasAnyPermission = permissions.some((p: { action: string; resource: string }) =>
      authStore.hasPermission(p.action, p.resource),
    )

    if (!hasAnyPermission) {
      // Show toast notification for permission denial
      const { toast } = await import('vue-sonner')
      toast.error('You do not have permission to access this page', {
        duration: 4000,
        position: 'top-right',
      })
      return next({ name: 'dashboard' })
    }
  }

  // Check roles if route requires them
  if (to.meta.roles && authStore.isAuthenticated) {
    const roles = to.meta.roles as string[]
    if (!authStore.hasAnyRole(roles)) {
      // Show toast notification for role denial
      const { toast } = await import('vue-sonner')
      toast.error('You do not have the required role to access this page', {
        duration: 4000,
        position: 'top-right',
      })
      return next({ name: 'dashboard' })
    }
  }

  // Redirect authenticated users away from login
  if (to.name === 'login' && authStore.isAuthenticated) {
    return next({ name: 'dashboard' })
  }

  next()
})

export default router
