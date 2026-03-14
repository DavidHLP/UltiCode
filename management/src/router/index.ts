import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/components/layout/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '仪表盘' },
        },
        {
          path: 'analytics',
          name: 'analytics',
          component: () => import('@/views/analytics/AnalyticsView.vue'),
          meta: { title: '数据分析', permission: { action: 'READ', resource: 'SYSTEM' } },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/users/UsersListView.vue'),
          meta: { title: '用户管理', permission: { action: 'READ', resource: 'USER' } },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/views/audit/AuditLogsView.vue'),
          meta: { title: '审计日志', permission: { action: 'READ', resource: 'SYSTEM' } },
        },
        {
          path: 'problems',
          name: 'problems',
          component: () => import('@/views/problems/ProblemsListView.vue'),
          meta: { title: '题目管理', permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        // Problem detail views (with tabs)
        {
          path: 'problems/:id',
          redirect: (to) => ({ name: 'problem-view-description', params: { id: to.params.id } }),
        },
        {
          path: 'problems/:id/description',
          name: 'problem-view-description',
          component: () => import('@/views/problems/ProblemDetailView.vue'),
          meta: { title: '问题详情', permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/code',
          name: 'problem-view-code',
          component: () => import('@/views/problems/ProblemDetailView.vue'),
          meta: { title: '问题详情', permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/cases',
          name: 'problem-view-cases',
          component: () => import('@/views/problems/ProblemDetailView.vue'),
          meta: { title: '问题详情', permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/audit',
          name: 'problem-view-audit',
          component: () => import('@/views/problems/ProblemDetailView.vue'),
          meta: { title: '问题详情', permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        // Problem edit views (split into 3 views)
        {
          path: 'problems/create',
          name: 'problem-create',
          component: () => import('@/views/problems/ProblemCreateView.vue'),
          meta: { title: '创建问题', permission: { action: 'CREATE', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/edit',
          redirect: (to) => ({ name: 'problem-edit-description', params: { id: to.params.id } }),
        },
        {
          path: 'problems/:id/edit/description',
          name: 'problem-edit-description',
          component: () => import('@/views/problems/edit/EditDescriptionView.vue'),
          meta: { title: '编辑问题', permission: { action: 'UPDATE', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/edit/code',
          name: 'problem-edit-code',
          component: () => import('@/views/problems/edit/EditCodeView.vue'),
          meta: { title: '编辑问题', permission: { action: 'UPDATE', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/edit/cases',
          name: 'problem-edit-cases',
          component: () => import('@/views/problems/edit/EditCasesView.vue'),
          meta: { title: '编辑问题', permission: { action: 'UPDATE', resource: 'PROBLEM' } },
        },
        // Legacy route name for backward compatibility (aliases to redirect)
        {
          path: 'problems/:id/edit/legacy',
          name: 'problem-edit',
          redirect: (to) => ({ name: 'problem-edit-description', params: { id: to.params.id } }),
        },
        // Moderation
        {
          path: 'moderation',
          name: 'moderation',
          component: () => import('@/views/moderation/ModerationQueueView.vue'),
          meta: { title: '内容审核', permission: { action: 'MODERATE', resource: 'PROBLEM' } },
        },
        // Problem Lists
        {
          path: 'problem-lists',
          name: 'problem-lists',
          component: () => import('@/views/problem-lists/ProblemListsListView.vue'),
          meta: { title: '题单管理', permission: { action: 'READ', resource: 'PROBLEM_LIST' } },
        },
        {
          path: 'problem-lists/create',
          name: 'problem-list-create',
          component: () => import('@/views/problem-lists/ProblemListDetailView.vue'),
          meta: { title: '创建题单', permission: { action: 'CREATE', resource: 'PROBLEM_LIST' } },
        },
        {
          path: 'problem-lists/:id/edit',
          name: 'problem-list-edit',
          component: () => import('@/views/problem-lists/ProblemListDetailView.vue'),
          meta: { title: '编辑题单', permission: { action: 'UPDATE', resource: 'PROBLEM_LIST' } },
        },
        // Solutions
        {
          path: 'solutions',
          name: 'solutions',
          component: () => import('@/views/solutions/SolutionsListView.vue'),
          meta: { title: '题解管理', permission: { action: 'READ', resource: 'SOLUTION' } },
        },
        {
          path: 'solutions/:id',
          redirect: (to) => ({ name: 'solution-view-description', params: { id: to.params.id } }),
        },
        {
          path: 'solutions/:id/description',
          name: 'solution-view-description',
          component: () => import('@/views/solutions/SolutionDetailView.vue'),
          meta: { title: '题解详情', permission: { action: 'READ', resource: 'SOLUTION' } },
        },
        {
          path: 'solutions/:id/code',
          name: 'solution-view-code',
          component: () => import('@/views/solutions/SolutionDetailView.vue'),
          meta: { title: '题解详情', permission: { action: 'READ', resource: 'SOLUTION' } },
        },
        // Comments
        {
          path: 'comments',
          name: 'comments',
          component: () => import('@/views/comments/CommentsListView.vue'),
          meta: {
            title: '评论管理',
            permission: [
              { action: 'MODERATE', resource: 'FORUM_COMMENT' },
              { action: 'MODERATE', resource: 'SOLUTION_COMMENT' },
            ],
          },
        },
        // Tags
        {
          path: 'tags',
          name: 'tags',
          component: () => import('@/views/tags/TagsListView.vue'),
          meta: { title: '标签管理', permission: { action: 'READ', resource: 'TAG' } },
        },
        // Forum
        {
          path: 'forum',
          redirect: 'forum/posts',
        },
        {
          path: 'forum/posts',
          name: 'forum-posts',
          component: () => import('@/views/forum/ForumPostsListView.vue'),
          meta: { title: '论坛帖子', permission: { action: 'MODERATE', resource: 'FORUM_POST' } },
        },
        // Forum post detail views (with tabs)
        {
          path: 'forum/posts/:id',
          redirect: (to) => ({ name: 'forum-post-detail-overview', params: { id: to.params.id } }),
        },
        {
          path: 'forum/posts/:id/overview',
          name: 'forum-post-detail-overview',
          component: () => import('@/views/forum/ForumPostDetailView.vue'),
          meta: { title: '帖子详情', permission: { action: 'MODERATE', resource: 'FORUM_POST' } },
        },
        {
          path: 'forum/posts/:id/comments',
          name: 'forum-post-detail-comments',
          component: () => import('@/views/forum/ForumPostDetailView.vue'),
          meta: { title: '帖子详情', permission: { action: 'MODERATE', resource: 'FORUM_POST' } },
        },
        {
          path: 'forum/posts/:id/audit',
          name: 'forum-post-detail-audit',
          component: () => import('@/views/forum/ForumPostDetailView.vue'),
          meta: { title: '帖子详情', permission: { action: 'MODERATE', resource: 'FORUM_POST' } },
        },
        // Contests
        {
          path: 'contests',
          name: 'contests',
          component: () => import('@/views/contests/ContestsListView.vue'),
          meta: { title: '比赛管理', permission: { action: 'READ', resource: 'CONTEST' } },
        },
        {
          path: 'contests/:id',
          name: 'contest-detail',
          component: () => import('@/views/contests/ContestDetailView.vue'),
          meta: { title: '比赛详情', permission: { action: 'READ', resource: 'CONTEST' } },
        },
        // Scoring Rules
        {
          path: 'scoring-rules',
          name: 'scoring-rules',
          component: () => import('@/views/contests/ScoringRulesView.vue'),
          meta: { title: '评分规则', permission: { action: 'READ', resource: 'CONTEST' } },
        },
        // Submissions
        {
          path: 'submissions',
          name: 'submissions',
          component: () => import('@/views/submissions/SubmissionsView.vue'),
          meta: { title: '提交记录', permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        // Settings
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/settings/SettingsView.vue'),
          meta: { title: '系统设置', permission: { action: 'UPDATE', resource: 'SYSTEM' } },
        },
        // Notifications
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/notifications/NotificationsListView.vue'),
          meta: { title: '通知管理', permission: { action: 'READ', resource: 'SYSTEM' } },
        },
        // System Monitoring
        {
          path: 'monitoring',
          name: 'monitoring',
          component: () => import('@/views/system/MonitoringView.vue'),
          meta: { title: '系统监控', permission: { action: 'READ', resource: 'SYSTEM' } },
        },
        // Backup & Recovery
        {
          path: 'backup',
          name: 'backup',
          component: () => import('@/views/system/BackupView.vue'),
          meta: { title: '备份恢复', permission: { action: 'UPDATE', resource: 'SYSTEM' } },
        },
        // Email Management
        {
          path: 'email',
          name: 'email',
          component: () => import('@/views/system/EmailView.vue'),
          meta: { title: '邮件管理', permission: { action: 'UPDATE', resource: 'SYSTEM' } },
        },
        // Account
        {
          path: 'account',
          name: 'account',
          component: () => import('@/views/account/AccountView.vue'),
          meta: { title: '账户设置' },
        },
        // Billing
        {
          path: 'billing',
          name: 'billing',
          component: () => import('@/views/billing/BillingView.vue'),
          meta: { title: '计费管理' },
        },
      ],
    },
  ],
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
    // Redirect to login with return url
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

    // Check if user has ANY of the required permissions
    const hasAnyPermission = permissions.some((p: { action: string; resource: string }) =>
      authStore.hasPermission(p.action, p.resource),
    )

    if (!hasAnyPermission) {
      // Redirect to dashboard if insufficient permissions
      return next({ name: 'dashboard' })
    }
  }

  // Check roles if route requires them
  if (to.meta.roles && authStore.isAuthenticated) {
    const roles = to.meta.roles as string[]
    if (!authStore.hasAnyRole(roles)) {
      return next({ name: 'dashboard' })
    }
  }

  // If already authenticated and trying to access login, redirect to dashboard
  if (to.name === 'login' && authStore.isAuthenticated) {
    return next({ name: 'dashboard' })
  }

  next()
})

export default router
