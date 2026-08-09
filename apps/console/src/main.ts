import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import i18n from "./i18n";
import "./style.css";
import "./assets/markdown.css";

// Import PWA registration (this registers the service worker)
import "@/pwa-register";
import { initTheme, applyTypographyDensity } from "@/shared/theme/src";
import { bootstrapApp } from "@/shared/app-bootstrap/src";
import { setOnAuthFailure } from "@/shared/auth-core/src";

/**
 * Console application bootstrap.
 *
 * The startup ordering invariant (theme → Pinia/i18n → auth-failure handler →
 * auth init → router → mount) is owned by the shared `bootstrapApp` module so
 * Console and Management cannot diverge; this entry file is the Console
 * adapter and supplies only Console-specific policy: the comfortable density,
 * the AuthContext pre-auth wiring, the single auth-failure owner, and the
 * document-language pre-mount hook.
 */
bootstrapApp({
  density: "comfortable",
  initTheme,
  applyTypographyDensity,
  rootComponent: App,
  preAuthPlugins: [createPinia(), i18n],
  async preAuthInit() {
    // Initialize auth context BEFORE auth store; sets up global auth error
    // handling and the session-expired redirect. Use the imported router
    // instance directly — useRouter() relies on inject() which only works
    // during setup, not in async callbacks.
    const { initializeAuthContext, onSessionExpired } = await import(
      "@/contexts/AuthContext"
    );
    initializeAuthContext();
    onSessionExpired(() => {
      const currentRoute = router.currentRoute.value;
      if (currentRoute.name === "login") return;
      const redirect = currentRoute.fullPath;
      setTimeout(() => {
        const routeAfterDelay = router.currentRoute.value;
        if (routeAfterDelay.name === "login") {
          if (!routeAfterDelay.query.redirect) {
            router.replace({
              name: "login",
              query: { redirect },
            });
          }
          return;
        }
        router.push({
          name: "login",
          query: { redirect },
        });
      }, 100);
    });
  },
  registerAuthFailureHandler: setOnAuthFailure,
  async onAuthFailure() {
    // 401 → refresh → replay is active (request.ts wires the CSRF interceptor).
    // When refresh itself fails (>7d idle), the shared interceptor calls this
    // handler; it funnels through runSessionExpired, the same single owner the
    // propagated-401 strategy in utils/request.ts delegates to.
    const { runSessionExpired } = await import("@/auth/runSessionExpired");
    runSessionExpired();
  },
  async initializeAuth() {
    const { useAuthStore } = await import("@/stores/auth");
    await useAuthStore().initialize();
  },
  router,
  preMount() {
    document.documentElement.lang = (
      i18n.global.locale as unknown as { value: string }
    ).value;
  },
}).catch((error) => {
  console.error("[Bootstrap] Fatal error during bootstrap:", error);
  // Fallback: mount app anyway to show error UI
  const app = createApp(App);
  app.use(createPinia());
  app.use(i18n);
  app.use(router);
  app.mount("#app");
});
