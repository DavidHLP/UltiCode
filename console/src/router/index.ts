import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { installAuthNavigation } from "@/shared/auth-core/src";

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
 * The cross-cutting policy (initialization barrier, staleness revalidation,
 * cancellation ordering, redirect-to-login) lives in
 * `shared/auth-core/src/navigation.ts` so that console and management share
 * exactly one implementation. This file keeps only the per-app adapters:
 *   - the auth-store bridge that exposes `status`/`waitForInitialization`/
 *     `fetchUser`/`ensureUser`/`isAuthenticated`
 *   - the per-app redirect targets (`login`, `forum-home` for guest-only
 *     and landing hits).
 *
 * See architecture-review candidate #1.
 */

const STALE_SESSION_MS = 5 * 60 * 1000; // 5 minutes

function buildConsoleAuthAdapter() {
  const authStore = useAuthStore();
  return {
    status: () => authStore.status,
    isAuthenticated: () => authStore.isAuthenticated,
    waitForInitialization: async () => {
      if (authStore.initializationPromise) {
        await authStore.initializationPromise;
      }
    },
    fetchUser: async () => {
      await authStore.fetchUser();
    },
    ensureUser: async () => {
      await authStore.ensureUser();
    },
  };
}

// Install the shared navigation guard. We capture the returned policy so
// tests (and the existing `pm2-logs`-style tooling) can introspect state
// without reaching into router internals.
const _ = installAuthNavigation({
  router,
  auth: buildConsoleAuthAdapter,
  policy: {
    staleSessionMs: STALE_SESSION_MS,
    loginRouteName: "login",
    authenticatedGuestRouteName: "forum-home",
    // Authenticated users may still view the landing page (route `landing`),
    // so we intentionally do NOT set `authenticatedLandingRouteName` here.
    // An unset value makes the shared guard allow `/` instead of bouncing
    // logged-in users to `forum-home`.
  },
});

export default router;
