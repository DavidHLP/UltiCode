import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { PERM } from '@/constants/permissions'
import { t } from '@/i18n'
import { installAuthNavigation } from '@/shared/auth-core/src'

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
    // Post-auth redirect: an already-authenticated user landing on /login
    // is sent to `dashboard` by the shared auth-navigation seam via the
    // `authenticatedGuestRouteName` policy field. The route meta is the
    // policy's input — without `guestOnly: true` the seam has no way to
    // know this is a guest-only route.
    meta: { guestOnly: true },
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
        meta: { titleKey: 'nav.users', permission: PERM.USER_READ },
      },

      // ==================== Audit Logs ====================
      {
        path: 'audit',
        name: 'audit',
        component: () => import('@/views/audit/AuditLogsView.vue'),
        meta: { titleKey: 'nav.auditLogs', permission: PERM.SYSTEM_READ },
      },
      {
        path: 'audit/report',
        name: 'audit-report',
        component: () => import('@/views/audit/AuditReportView.vue'),
        meta: { titleKey: 'nav.auditReport', permission: PERM.SYSTEM_READ },
      },

      {
        path: 'help',
        name: 'help',
        component: () => import('@/views/help/HelpView.vue'),
        meta: { titleKey: 'nav.getHelp', permission: PERM.SYSTEM_READ },
      },

      // ==================== Analytics ====================
      {
        path: 'analytics',
        name: 'analytics',
        component: () => import('@/views/analytics/AnalyticsView.vue'),
        meta: { titleKey: 'nav.analytics', permission: PERM.SYSTEM_READ },
      },

      // ==================== Problems ====================
      {
        path: 'problems',
        name: 'problems',
        component: () => import('@/views/problems/ProblemsListView.vue'),
        meta: { titleKey: 'nav.problems', permission: PERM.PROBLEM_READ },
      },
      {
        path: 'problems/create',
        name: 'problem-create',
        component: () => import('@/views/problems/ProblemCreateView.vue'),
        meta: {
          titleKey: 'problems.createTitle',
          permission: PERM.PROBLEM_CREATE,
        },
      },
      // Problem detail view (handles all tabs via route detection)
      {
        path: 'problems/:id/:tab?',
        name: 'problem-detail',
        component: () => import('@/views/problems/ProblemDetailView.vue'),
        meta: {
          titleKey: 'problems.detailTitle',
          permission: PERM.PROBLEM_READ,
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
          permission: PERM.PROBLEM_UPDATE,
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
          permission: PERM.MODERATE_PROBLEM,
        },
      },
      {
        path: 'moderation/dashboard',
        name: 'moderation-dashboard',
        component: () => import('@/views/moderation/ModerationDashboardView.vue'),
        meta: {
          titleKey: 'moderation.stats.title',
          permission: PERM.MODERATE_PROBLEM,
        },
      },
      {
        path: 'moderation/reports',
        name: 'moderation-reports',
        component: () => import('@/views/moderation/ReportsView.vue'),
        meta: {
          titleKey: 'moderation.reports.title',
          permission: PERM.MODERATE_PROBLEM,
        },
      },
      {
        path: 'moderation/appeals',
        name: 'moderation-appeals',
        component: () => import('@/views/moderation/AppealsView.vue'),
        meta: {
          titleKey: 'moderation.appeals.title',
          permission: PERM.MODERATE_PROBLEM,
        },
      },

      // ==================== Problem Lists ====================
      {
        path: 'problem-lists',
        name: 'problem-lists',
        component: () => import('@/views/problem-lists/ProblemListsListView.vue'),
        meta: {
          titleKey: 'nav.problemLists',
          permission: PERM.PROBLEM_LIST_READ,
        },
      },
      {
        path: 'problem-lists/create',
        name: 'problem-list-create',
        component: () => import('@/views/problem-lists/ProblemListDetailView.vue'),
        meta: {
          titleKey: 'problemLists.createTitle',
          permission: PERM.PROBLEM_LIST_CREATE,
        },
      },
      {
        path: 'problem-lists/:id/edit',
        name: 'problem-list-edit',
        component: () => import('@/views/problem-lists/ProblemListDetailView.vue'),
        meta: {
          titleKey: 'problemLists.editTitle',
          permission: PERM.PROBLEM_LIST_UPDATE,
        },
        props: true,
      },

      // ==================== Solutions ====================
      {
        path: 'solutions',
        name: 'solutions',
        component: () => import('@/views/solutions/SolutionsListView.vue'),
        meta: { titleKey: 'nav.solutions', permission: PERM.SOLUTION_READ },
      },
      // Solution detail view (handles all tabs via route detection)
      {
        path: 'solutions/:id/:tab?',
        name: 'solution-detail',
        component: () => import('@/views/solutions/SolutionDetailView.vue'),
        meta: {
          titleKey: 'solutions.detailTitle',
          permission: PERM.SOLUTION_READ,
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
          permission: [PERM.MODERATE_FORUM_COMMENT, PERM.MODERATE_SOLUTION_COMMENT],
        },
      },
      {
        path: 'comments/:type/:id',
        name: 'comment-detail',
        component: () => import('@/views/comments/CommentDetailView.vue'),
        meta: {
          titleKey: 'comments.detail.title',
          permission: [PERM.MODERATE_FORUM_COMMENT, PERM.MODERATE_SOLUTION_COMMENT],
        },
        props: true,
      },

      // ==================== Tags ====================
      {
        path: 'tags',
        name: 'tags',
        component: () => import('@/views/tags/TagsListView.vue'),
        meta: { titleKey: 'nav.tags', permission: PERM.TAG_READ },
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
          permission: PERM.MODERATE_FORUM_POST,
        },
      },
      // Forum post detail view (handles all tabs via route detection)
      {
        path: 'forum/posts/:id/:tab?',
        name: 'forum-post-detail',
        component: () => import('@/views/forum/ForumPostDetailView.vue'),
        meta: {
          titleKey: 'forum.detailTitle',
          permission: PERM.MODERATE_FORUM_POST,
        },
        props: true,
      },

      // ==================== Contests ====================
      {
        path: 'contests',
        name: 'contests',
        component: () => import('@/views/contests/ContestsListView.vue'),
        meta: { titleKey: 'nav.contests', permission: PERM.CONTEST_READ },
      },
      {
        path: 'contests/:id',
        name: 'contest-detail',
        component: () => import('@/views/contests/ContestDetailView.vue'),
        meta: {
          titleKey: 'contests.detailTitle',
          permission: PERM.CONTEST_READ,
        },
        props: true,
      },
      {
        path: 'scoring-rules',
        name: 'scoring-rules',
        component: () => import('@/views/contests/ScoringRulesView.vue'),
        meta: {
          titleKey: 'contests.scoringRules',
          permission: PERM.CONTEST_READ,
        },
      },

      // ==================== Submissions ====================
      {
        path: 'submissions',
        name: 'submissions',
        component: () => import('@/views/submissions/SubmissionsView.vue'),
        meta: { titleKey: 'nav.submissions', permission: PERM.PROBLEM_READ },
      },

      // ==================== Settings ====================
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { titleKey: 'nav.settings', permission: PERM.SYSTEM_UPDATE },
      },

      // ==================== Notifications ====================
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/notifications/NotificationsListView.vue'),
        meta: { titleKey: 'nav.notifications', permission: PERM.SYSTEM_READ },
      },

      // ==================== System ====================
      {
        path: 'monitoring',
        name: 'monitoring',
        component: () => import('@/views/system/MonitoringView.vue'),
        meta: { titleKey: 'nav.monitoring', permission: PERM.SYSTEM_READ },
      },
      {
        path: 'backup',
        name: 'backup',
        component: () => import('@/views/system/BackupView.vue'),
        meta: { titleKey: 'nav.backup', permission: PERM.SYSTEM_UPDATE },
      },
      {
        path: 'email',
        name: 'email',
        component: () => import('@/views/system/EmailView.vue'),
        meta: { titleKey: 'nav.email', permission: PERM.SYSTEM_UPDATE },
      },

      // ==================== Account ====================
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/account/AccountView.vue'),
        meta: { titleKey: 'nav.account' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

// ----------------------------------------------------------------------------
// Shared authentication navigation policy
// ----------------------------------------------------------------------------
//
// The cross-cutting navigation policy (staleness revalidation, cancellation
// ordering, redirect-to-login, post-auth redirects for guest-only routes)
// lives in `shared/auth-core/src/navigation.ts` so console and management
// share exactly one implementation. This file owns the per-app adapters:
//   - the auth-store bridge
//   - the per-app redirect targets (`login`, `dashboard`)
//   - the management-only permission/role checks that run AFTER the shared
//     policy returns `allow`
//
// See architecture-review candidate #1.
// ----------------------------------------------------------------------------

// Default staleness window lives in shared/auth-core (DEFAULT_STALE_SESSION_MS).

function buildManagementAuthAdapter() {
  // The management store is initialized in main.ts before the router mounts.
  // We therefore expose `waitForInitialization` as an unconditional no-op;
  // `status` reflects `isInitialized` so the seam's barrier is skipped.
  return {
    status: () => 'idle' as const,
    isAuthenticated: () => useAuthStore().isAuthenticated,
    waitForInitialization: async () => undefined,
    fetchUser: async () => {
      await useAuthStore().fetchUser()
    },
    ensureUser: async () => {
      // Lazy-load the user record only if the store is empty.
      // Previously this adapter called fetchUser() unconditionally
      // (ensureUser() did not exist on the auth store yet), which
      // caused a redundant /auth/me round-trip on every protected
      // navigation for an already-authenticated user. createAuthStore
      // now exposes ensureUser() to satisfy the seam's lazy-loader
      // contract.
      await useAuthStore().ensureUser()
    },
  }
}

installAuthNavigation({
  router,
  auth: buildManagementAuthAdapter,
  policy: {
    loginRouteName: 'login',
    // Authenticated users landing on /login (e.g. via a stale
    // session-expired redirect) are sent to the dashboard. The
    // guest-only route meta is set on the /login route above so the
    // shared seam can match it.
    authenticatedGuestRouteName: 'dashboard',
  },
})

// ----------------------------------------------------------------------------
// Management-specific permissions / roles check
// ----------------------------------------------------------------------------
// Runs after the shared auth-navigation policy. The shared policy owns
// `requiresAuth` + redirect-to-login; this block owns the management-only
// `meta.permission` + `meta.roles` checks and the post-login redirect.

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.permission && authStore.isAuthenticated) {
    const permissions = Array.isArray(to.meta.permission)
      ? to.meta.permission
      : [to.meta.permission]

    const hasAnyPermission = permissions.some((p: { action: string; resource: string }) =>
      authStore.hasPermission(p.action, p.resource),
    )

    if (!hasAnyPermission) {
      const { toast } = await import('vue-sonner')
      toast.error(t('errors.permission.forbiddenPage'), {
        duration: 4000,
        position: 'top-right',
      })
      return { name: 'dashboard' }
    }
  }

  if (to.meta.roles && authStore.isAuthenticated) {
    const roles = to.meta.roles as string[]
    if (!authStore.hasAnyRole(roles)) {
      const { toast } = await import('vue-sonner')
      toast.error(t('errors.permission.forbiddenRole'), {
        duration: 4000,
        position: 'top-right',
      })
      return { name: 'dashboard' }
    }
  }

  // Post-auth redirect is owned by the shared auth-navigation seam via
  // `authenticatedGuestRouteName: 'dashboard'` and the login route's
  // `meta: { guestOnly: true }` (see installAuthNavigation above).
  return true
})

export default router
