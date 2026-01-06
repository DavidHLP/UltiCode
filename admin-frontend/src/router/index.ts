import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/admin/auth'

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
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/users/UsersListView.vue'),
          meta: { permission: { action: 'READ', resource: 'USER' } },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/views/audit/AuditLogsView.vue'),
          meta: { permission: { action: 'READ', resource: 'SYSTEM' } },
        },
        {
          path: 'problems',
          name: 'problems',
          component: () => import('@/views/problems/ProblemsListView.vue'),
          meta: { permission: { action: 'READ', resource: 'PROBLEM' } },
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
          meta: { permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/code',
          name: 'problem-view-code',
          component: () => import('@/views/problems/ProblemDetailView.vue'),
          meta: { permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/cases',
          name: 'problem-view-cases',
          component: () => import('@/views/problems/ProblemDetailView.vue'),
          meta: { permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        // Problem edit views (split into 3 views)
        {
          path: 'problems/create',
          name: 'problem-create',
          component: () => import('@/views/problems/ProblemCreateView.vue'),
          meta: { permission: { action: 'CREATE', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/edit',
          redirect: (to) => ({ name: 'problem-edit-description', params: { id: to.params.id } }),
        },
        {
          path: 'problems/:id/edit/description',
          name: 'problem-edit-description',
          component: () => import('@/views/problems/edit/EditDescriptionView.vue'),
          meta: { permission: { action: 'UPDATE', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/edit/code',
          name: 'problem-edit-code',
          component: () => import('@/views/problems/edit/EditCodeView.vue'),
          meta: { permission: { action: 'UPDATE', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/edit/cases',
          name: 'problem-edit-cases',
          component: () => import('@/views/problems/edit/EditCasesView.vue'),
          meta: { permission: { action: 'UPDATE', resource: 'PROBLEM' } },
        },
        // Legacy route name for backward compatibility (aliases to redirect)
        {
          path: 'problems/:id/edit/legacy',
          name: 'problem-edit',
          redirect: (to) => ({ name: 'problem-edit-description', params: { id: to.params.id } }),
        },
      ],
    },
  ],
})

// Navigation guard for authentication and permissions
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()

  // Initialize auth store if not already done
  if (!authStore.isAuthenticated && localStorage.getItem('admin_token')) {
    authStore.initialize()
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
    const { action, resource } = to.meta.permission as { action: string; resource: string }
    if (!authStore.hasPermission(action, resource)) {
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
