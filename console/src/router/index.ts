import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";
import { useAuthStore } from "@/stores/auth";

const forumRoutes: RouteRecordRaw = {
  path: "/forum",
  component: () => import("@/features/sider/AppLayout.vue"),
  children: [
    {
      path: "",
      name: "forum-home",
      component: () => import("@/views/forum/ForumFeedView.vue"),
    },
    {
      path: "popular",
      name: "forum-popular",
      component: () => import("@/views/forum/ForumFeedView.vue"),
      props: { filter: "hot" },
    },
    {
      path: "explore",
      name: "forum-explore",
      component: () => import("@/views/forum/ForumFeedView.vue"),
      props: { filter: "explore" },
    },
    {
      path: "all",
      name: "forum-all",
      component: () => import("@/views/forum/ForumFeedView.vue"),
      props: { filter: "new" },
    },
    {
      path: "c/:category",
      name: "forum-category",
      component: () => import("@/views/forum/ForumFeedView.vue"),
    },
    {
      path: "detailed/:postId",
      name: "forum-thread",
      component: () => import("@/views/forum/ForumThreadView.vue"),
    },
    {
      path: "guidelines",
      name: "forum-guidelines",
      component: () => import("@/views/forum/ForumGuidelinesView.vue"),
    },
    {
      path: "feedback",
      name: "forum-feedback",
      component: () => import("@/views/forum/ForumFeedbackView.vue"),
    },
  ],
};

const contestRoutes: RouteRecordRaw = {
  path: "/contest",
  component: () => import("@/features/sider/AppLayout.vue"),
  children: [
    {
      path: "",
      name: "contest-home",
      component: () => import("@/views/contest/ContestHomeView.vue"),
    },
    {
      path: "browse",
      name: "contest-browse",
      component: () => import("@/views/contest/ContestBrowseView.vue"),
    },
    {
      path: "list",
      name: "contest-list",
      redirect: (to) => ({ name: "contest-home", query: to.query }),
    },
    {
      path: "browse/past",
      name: "contest-browse-past",
      component: () => import("@/views/contest/ContestBrowseView.vue"),
      props: { initialTab: "finished" },
    },
    {
      path: "my",
      name: "contest-my",
      component: () => import("@/views/contest/ContestMyView.vue"),
      meta: { requiresAuth: true },
    },
    {
      path: "rankings",
      name: "contest-rankings",
      component: () => import("@/views/contest/ContestRankingsView.vue"),
    },
    {
      path: "past",
      redirect: (to) => ({ name: "contest-browse-past", query: to.query }),
    },
    {
      path: "global-ranking",
      redirect: (to) => ({ name: "contest-rankings", query: to.query }),
    },
    {
      path: "local-ranking",
      redirect: (to) => ({ name: "contest-rankings", query: to.query }),
    },
    {
      path: ":slug",
      name: "contest-detail",
      component: () => import("@/views/contest/detailed/ContestDetailView.vue"),
    },
  ],
};

const problemSetRoute: RouteRecordRaw = {
  path: "/problemset",
  component: () => import("@/features/sider/AppLayout.vue"),
  children: [
    {
      path: "",
      name: "problemset",
      component: () => import("@/views/problem-set/ProblemSetView.vue"),
    },
    {
      path: "list/:id",
      name: "problem-list-detail",
      component: () => import("@/views/problem-list/ProblemListView.vue"),
    },
    {
      path: ":category",
      name: "problemset-category",
      component: () => import("@/views/problem-set/ProblemSetView.vue"),
    },
  ],
};

const problemDetailRoute: RouteRecordRaw = {
  path: "/problems/:slug/:tab?",
  name: "problem-detail",
  component: () => import("@/views/problems/ProblemDetailView.vue"),
};

const solutionCreateRoute: RouteRecordRaw = {
  path: "/problem/:id(\\d+)/solution/create",
  name: "solution-create",
  component: () =>
    import("@/views/post-editor/solutions/SolutionsEditView.vue"),
};

const solutionCreateFromSubmissionRoute: RouteRecordRaw = {
  path: "/post-editor/solution/create",
  name: "solution-create-from-submission",
  component: () =>
    import("@/views/post-editor/solutions/SolutionsEditView.vue"),
};

const solutionEditRoute: RouteRecordRaw = {
  path: "/solutions/:id/edit",
  name: "solution-edit",
  component: () =>
    import("@/views/post-editor/solutions/SolutionsEditView.vue"),
};

const submissionDetailRoute: RouteRecordRaw = {
  path: "/submissions/:id",
  name: "submission-detail",
  component: () => import("@/views/submissions/SubmissionsDetailView.vue"),
  meta: { requiresAuth: true },
};

const forumCreateRoute: RouteRecordRaw = {
  path: "/forum/create",
  name: "forum-create",
  component: () => import("@/views/forum/ForumEditorView.vue"),
};

const forumEditRoute: RouteRecordRaw = {
  path: "/forum/edit/:postId",
  name: "forum-edit",
  component: () => import("@/views/forum/ForumEditorView.vue"),
};

const personalRoutes: RouteRecordRaw = {
  path: "/personal",
  component: () => import("@/features/sider/AppLayout.vue"),
  meta: { requiresAuth: true },
  children: [
    {
      path: "",
      name: "personal-profile",
      component: () => import("@/views/personal/PersonalView.vue"),
    },
    {
      path: "account",
      component: () => import("@/views/personal/AccountView.vue"),
      children: [
        {
          path: "",
          redirect: { name: "personal-account-general" },
        },
        {
          path: "general",
          name: "personal-account-general",
          component: () => import("@/views/personal/AccountGeneralView.vue"),
        },
        {
          path: "security",
          name: "personal-account-security",
          component: () => import("@/views/personal/AccountSecurityView.vue"),
        },
        {
          path: "notifications",
          name: "personal-account-notifications",
          component: () =>
            import("@/views/personal/AccountNotificationsView.vue"),
        },
      ],
    },
    {
      path: "submissions",
      name: "personal-submissions",
      component: () => import("@/views/personal/SubmissionsView.vue"),
    },
    {
      path: "solutions",
      name: "personal-solutions",
      component: () => import("@/views/personal/SolutionsView.vue"),
    },
    {
      path: "problem-lists",
      name: "personal-problem-lists",
      component: () => import("@/views/personal/ProblemListsView.vue"),
    },
    {
      path: "bookmarks",
      name: "personal-bookmarks",
      component: () => import("@/views/personal/BookmarksView.vue"),
    },
    {
      path: "forum-posts",
      name: "personal-forum-posts",
      component: () => import("@/views/personal/ForumPostsView.vue"),
    },
    {
      path: "notifications",
      name: "personal-notifications",
      component: () => import("@/views/personal/NotificationsView.vue"),
    },
    {
      path: "achievements",
      name: "personal-achievements",
      component: () =>
        import("@/views/achievements/AchievementGalleryView.vue"),
    },
    {
      path: "dashboard",
      name: "personal-dashboard",
      component: () => import("@/views/dashboard/PersonalDashboardView.vue"),
    },
    {
      path: "subscription",
      name: "personal-subscription",
      component: () => import("@/views/personal/SubscriptionView.vue"),
    },
  ],
};

// Mark create routes as requiring auth
solutionCreateRoute.meta = { requiresAuth: true };
solutionCreateFromSubmissionRoute.meta = { requiresAuth: true };
solutionEditRoute.meta = { requiresAuth: true };
forumCreateRoute.meta = { requiresAuth: true };
forumEditRoute.meta = { requiresAuth: true };

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/",
      name: "landing",
      component: () => import("../views/LandingView.vue"),
    },
    forumCreateRoute,
    forumEditRoute,
    submissionDetailRoute,
    forumRoutes,
    contestRoutes,
    problemSetRoute,
    problemDetailRoute,
    solutionCreateRoute,
    solutionCreateFromSubmissionRoute,
    solutionEditRoute,
    {
      path: "/login",
      name: "login",
      component: () => import("../views/auth/LoginView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/register",
      name: "register",
      component: () => import("../views/auth/RegisterView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/forgot-password",
      name: "forgot-password",
      component: () => import("../views/auth/ForgotPasswordView.vue"),
      meta: { guestOnly: true },
    },
    {
      path: "/reset-password",
      name: "reset-password",
      component: () => import("../views/auth/ResetPasswordView.vue"),
      meta: { guestOnly: true },
    },
    personalRoutes,
    // Public user profile route
    {
      path: "/users/:id",
      name: "user-profile",
      component: () => import("@/views/users/UserProfileView.vue"),
      meta: { requiresAuth: true },
    },
    // Public profile route by username
    {
      path: "/profile/:username",
      name: "public-profile",
      component: () => import("@/views/profile/ProfileView.vue"),
      meta: { requiresAuth: true },
    },
  ],
});

/**
 * Navigation guard for authentication
 *
 * LAZY LOADING design: User information is fetched on-demand when
 * accessing authenticated routes, NOT during app bootstrap.
 *
 * Logic:
 * 1. Wait for auth initialization if still in progress (prevents premature redirect to login)
 * 2. Check if route requires authentication
 * 3. If required, call ensureUser() to fetch user info on-demand
 * 4. Re-validate stale sessions (token may have expired while user was idle)
 * 5. Redirect to login if not authenticated after fetch
 * 6. Redirect to home if already authenticated and accessing login/register
 * 7. Guard against navigation abort (newer navigation superseded this one)
 */

// Navigation abort detection: each new navigation increments the counter,
// allowing async guards to bail out when superseded.
let pendingNavigationId = 0;

// Token freshness: track last successful auth validation to detect stale sessions.
// Sessions older than this threshold are re-validated against /auth/me before
// allowing access to protected routes.
let lastValidatedAt = 0;
const STALE_SESSION_MS = 5 * 60 * 1000; // 5 minutes

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  const navId = ++pendingNavigationId;
  const isStale = () => navId !== pendingNavigationId;

  // Development-only logging
  if (import.meta.env.DEV) {
    console.debug("[Router] beforeEach", {
      to: to.path,
      requiresAuth: to.matched.some((r) => r.meta.requiresAuth === true),
      authStatus: authStore.status,
      isAuthenticated: authStore.isAuthenticated,
      hasUser: !!authStore.user,
    });
  }

  // If auth is still initializing, wait for it to complete before making navigation decisions.
  // This prevents premature redirect to login when user navigates before initialize() completes.
  if (authStore.status === "loading" && authStore.initializationPromise) {
    await authStore.initializationPromise;
    if (isStale()) return;
  }

  const requiresAuth = to.matched.some(
    (record) => record.meta.requiresAuth === true,
  );

  // If authentication required, fetch user info on-demand
  if (requiresAuth) {
    // Re-validate stale sessions — token may have expired while user was idle.
    // ensureUser() returns cached user without API call, so we must force
    // a backend check when the session is potentially expired.
    const isSessionExpired =
      authStore.isAuthenticated &&
      lastValidatedAt > 0 &&
      Date.now() - lastValidatedAt > STALE_SESSION_MS;

    if (isSessionExpired) {
      await authStore.fetchUser();
      if (isStale()) return;
    }

    // ensureUser() loads user from backend if not present; no-op if already loaded
    if (!authStore.isAuthenticated) {
      await authStore.ensureUser();
      if (isStale()) return;
    }

    // After fetch, check if authenticated
    if (!authStore.isAuthenticated) {
      return {
        name: "login",
        query: { redirect: to.fullPath },
      };
    }

    lastValidatedAt = Date.now();
  }

  // If already authenticated and trying to access a guest-only route, redirect home
  const isGuestOnly = to.matched.some(
    (record) => record.meta.guestOnly === true,
  );
  if (authStore.isAuthenticated && isGuestOnly) {
    return { name: "forum-home" };
  }

  // If authenticated user tries to access landing page, redirect to app console
  if (authStore.isAuthenticated && to.name === "landing") {
    return { name: "forum-home" };
  }

  return true;
});

export default router;
