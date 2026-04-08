import { createApp } from "vue";
import { createPinia } from "pinia";

import App from "./App.vue";
import router from "./router";
import i18n from "./i18n";
import "./style.css";
import "./assets/markdown.css";
import VueDnDKitPlugin from "@vue-dnd-kit/core";

// Import PWA registration (this registers the service worker)
import "@/pwa-register";

/**
 * Application Bootstrap
 *
 * Critical: Auth initialization happens BEFORE router installation.
 * This eliminates race conditions where the router guard would
 * need to wait for async auth initialization.
 *
 * Flow:
 * 1. Create Vue app
 * 2. Install Pinia (required for stores)
 * 3. Initialize auth context (coordinates auth state)
 * 4. Initialize auth store (async, completes before router)
 * 5. Setup auth-aware navigation handling
 * 6. Install router (now auth status is known)
 * 7. Mount app
 */
async function bootstrap() {
  const app = createApp(App);
  const pinia = createPinia();

  // Install Pinia first (required for stores to work)
  app.use(pinia);
  app.use(i18n);
  app.use(VueDnDKitPlugin);

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
