import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
} from "vue-router";

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
      component: () => import("@/views/contest/ContestView.vue"),
    },
    {
      path: "past",
      name: "contest-past",
      component: () => import("@/views/contest/ContestView.vue"),
      props: { tab: "past" },
    },
    {
      path: "my",
      name: "contest-my",
      component: () => import("@/views/contest/ContestView.vue"),
      props: { tab: "my" },
    },
    {
      path: "global-ranking",
      name: "contest-global-ranking",
      component: () => import("@/views/contest/ContestView.vue"), // Reuse or create specific view
      props: { tab: "ranking" },
    },
    {
      path: "local-ranking",
      name: "contest-local-ranking",
      component: () => import("@/views/contest/ContestView.vue"), // Reuse or create specific view
      props: { tab: "ranking" },
    },
    {
      path: ":contestId",
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
      name: "personal-account",
      component: () => import("@/views/personal/AccountView.vue"),
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
    { path: "/", redirect: { name: "forum-home" } },
    forumCreateRoute,
    forumEditRoute,
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
    },
    {
      path: "/register",
      name: "register",
      component: () => import("../views/auth/RegisterView.vue"),
    },
    {
      path: "/forgot-password",
      name: "forgot-password",
      component: () => import("../views/auth/ForgotPasswordView.vue"),
    },
    {
      path: "/reset-password",
      name: "reset-password",
      component: () => import("../views/auth/ResetPasswordView.vue"),
    },
    personalRoutes,
  ],
});

// Navigation guard for authentication
router.beforeEach(async (to, from, next) => {
  const { useAuthStore } = await import("@/stores/auth");
  const authStore = useAuthStore();

  // Initialize auth store on first navigation
  if (!authStore.isInitialized) {
    await authStore.initialize();
  }

  const requiresAuth = to.matched.some(
    (record) => record.meta.requiresAuth === true,
  );

  if (requiresAuth && !authStore.isAuthenticated) {
    // Redirect to login with return url
    return next({
      name: "login",
      query: { redirect: to.fullPath },
    });
  }

  // If already authenticated and trying to access login/register, redirect to home
  if (
    authStore.isAuthenticated &&
    (to.name === "login" || to.name === "register")
  ) {
    return next({ name: "forum-home" });
  }

  next();
});

export default router;
