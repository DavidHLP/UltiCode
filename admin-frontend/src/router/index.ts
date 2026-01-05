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
          path: 'users/:id',
          name: 'user-detail',
          component: () => import('@/views/users/UserDetailView.vue'),
          meta: { permission: { action: 'READ', resource: 'USER' } },
        },
        {
          path: 'users/create',
          name: 'user-create',
          component: () => import('@/views/users/UserCreateView.vue'),
          meta: { permission: { action: 'CREATE', resource: 'USER' } },
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
        {
          path: 'problems/:id',
          name: 'problem-detail',
          component: () => import('@/views/problems/ProblemDetailView.vue'),
          meta: { permission: { action: 'READ', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/create',
          name: 'problem-create',
          component: () => import('@/views/problems/ProblemCreateView.vue'),
          meta: { permission: { action: 'CREATE', resource: 'PROBLEM' } },
        },
        {
          path: 'problems/:id/edit',
          name: 'problem-edit',
          component: () => import('@/views/problems/ProblemEditView.vue'),
          meta: { permission: { action: 'UPDATE', resource: 'PROBLEM' } },
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
