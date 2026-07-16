import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import i18n from "./i18n";
import "./style.css";
import "./assets/markdown.css";

// Import PWA registration (this registers the service worker)
import "@/pwa-register";
import { initTheme } from '@/shared/theme/src';
import { applyTypographyDensity } from '@/shared/theme/src';

/**
 * Application Bootstrap
 *
 * Critical: Auth initialization happens BEFORE router installation.
 * This eliminates race conditions where the router guard would
 * need to wait for async auth initialization.
 *
 * Flow:
 * 1. Hydrate theme (singleton + media-query listener)
 * 2. Create Vue app
 * 3. Install Pinia (required for stores)
 * 4. Initialize auth context (coordinates auth state)
 * 5. Initialize auth store (async, completes before router)
 * 6. Setup auth-aware navigation handling
 * 7. Install router (now auth status is known)
 * 8. Mount app
 */
async function bootstrap() {
  // Hydrate the shared color theme. The matching FOUC script lives at
  // public/theme-bootstrap.js and runs before this bootstrap, so this
  // call is a no-op for the initial paint but registers the OS-preference
  // listener for live updates.
  initTheme();
  // Console is the comfortable reading surface: long-form problem
  // statements, markdown, forum content, and personal dashboards all
  // benefit from the default density. The shared typography CSS exposes
  // a `data-uc-density="comfortable"` profile (see
  // shared/theme/src/typography.css + docs/SHARED_TYPOGRAPHY_DESIGN.md
  // §7). The helper below is the only place this attribute is written.
  applyTypographyDensity("comfortable");

  const app = createApp(App);
  const pinia = createPinia();

  // Install Pinia first (required for stores to work)
  app.use(pinia);
  app.use(i18n);

  // Initialize auth context BEFORE auth store
  // This sets up global auth error handling
  const { initializeAuthContext, onSessionExpired } = await import(
    "@/contexts/AuthContext"
  );
  initializeAuthContext();

  // Setup session expired redirect to login
  // Use the imported router instance directly — useRouter() relies on inject()
  // which only works during setup, not in async callbacks.
  onSessionExpired(() => {
    setTimeout(() => {
      if (router.currentRoute.value.meta.requiresAuth !== true) {
        router.push("/login");
      }
    }, 100);
  });

  // Wire up: 401 → refresh → replay is now ACTIVE (request.ts passes
  // createRefreshAccessToken(csrfManager) as the third arg to
  // createCsrfAxiosInterceptor). When refresh itself fails (refresh
  // cookie expired, >7d idle), the shared interceptor calls
  // onAuthFailure. We forward through `runSessionExpired`, the same
  // single owner the propagated-401 strategy in `utils/request.ts`
  // delegates to, so concurrent refresh failures and a fan-in of 401s
  // collapse to one clearUser + one navigation push.
  const { setOnAuthFailure } = await import("@/shared/auth-core/src");
  const { runSessionExpired } = await import("@/auth/runSessionExpired");
  setOnAuthFailure(() => {
    runSessionExpired();
  });

  // Initialize auth store AFTER auth context is ready
  const { useAuthStore } = await import("@/stores/auth");
  const authStore = useAuthStore();

  try {
    await authStore.initialize();
  } catch (error) {
    console.error("[Bootstrap] Auth initialization failed:", error);
    // Continue anyway - app will render with unauthenticated state
  }

  // Now install router (auth status is already determined)
  app.use(router);

  // Set initial document language
  document.documentElement.lang = (
    i18n.global.locale as unknown as { value: string }
  ).value;

  app.mount("#app");
}

// Start the application
bootstrap().catch((error) => {
  console.error("[Bootstrap] Fatal error during bootstrap:", error);
  // Fallback: mount app anyway to show error UI
  const app = createApp(App);
  app.use(createPinia());
  app.use(i18n);
  app.use(router);
  app.mount("#app");
});
