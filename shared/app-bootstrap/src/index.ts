import type { App, Component, Plugin } from "vue";
import { createApp } from "vue";

/**
 * Application bootstrap — the deep module that owns the cross-application
 * startup ordering invariant.
 *
 * Both the Console and Management composition roots used to repeat one
 * load-bearing rule: hydrate theme, install Pinia + i18n, wire the auth-failure
 * handler, initialize the auth store, and only then install the router (so
 * guards never wait on async auth init), then mount. The rule was duplicated
 * across two ~80-line entry files with no shared owner and no test, so a
 * future edit could silently reorder it in one app.
 *
 * This module owns that sequence. Each application is a thin adapter that
 * supplies its own policy — density, the pre-auth step (Console's auth context,
 * Management's locale detection), the auth initializer, the single
 * auth-failure owner, and an optional pre-mount hook — and this function pins
 * the order they run in.
 */

/** Typography density the app applies during theme hydration. */
export type TypographyDensity = "comfortable" | "compact";

/** Context handed to the optional adapter hooks. */
export interface BootstrapContext {
  /** The Vue application instance once it has been created. */
  app: App;
}

/**
 * Options supplied by each application adapter. Every collaborator that is not
 * part of the shared ordering rule is passed in, so this module depends only on
 * Vue and owns no app-specific policy.
 */
export interface AppBootstrapOptions {
  /** Density profile the app writes via the shared theme helper. */
  density: TypographyDensity;
  /** Hydrates the shared color theme (registers the OS-preference listener). */
  initTheme: () => void;
  /** Writes the density attribute via the shared theme helper. */
  applyTypographyDensity: (density: TypographyDensity) => void;
  /** Root component passed to `createApp`. */
  rootComponent: Component;
  /**
   * Plugins installed before auth initialization, in order. Pinia MUST be first
   * (the auth store depends on it) — the app is responsible for that ordering
   * within the array because Pinia/i18n are app-owned.
   */
  plugins: Plugin[];
  /** App-specific step run after plugins, before the auth-failure handler. */
  preAuthInit?: (ctx: BootstrapContext) => Promise<void> | void;
  /** Registers the single auth-failure owner (shared auth-core `setOnAuthFailure`). */
  registerAuthFailureHandler: (handler: () => void | Promise<void>) => void;
  /** Single auth-failure owner; invoked when refresh fails or a 401 fans in. */
  onAuthFailure: () => void | Promise<void>;
  /** Initializes the auth store; completes before the router is installed. */
  initializeAuth: () => Promise<void>;
  /**
   * The router plugin, installed AFTER auth initialization so route guards see
   * a settled auth state. This is the core invariant the module owns.
   */
  router: Plugin;
  /** App-specific step run after the router is installed, before mount. */
  preMount?: (ctx: BootstrapContext) => Promise<void> | void;
  /** Selector passed to `app.mount`; defaults to `#app`. */
  mountSelector?: string;
}

/**
 * Run the shared application bootstrap sequence and mount the app.
 *
 * Auth initialization failures are caught and logged: the app continues to
 * mount with an unauthenticated state rather than aborting startup, matching
 * the previous behavior of both entry files. The returned promise rejects only
 * if a non-auth step (theme, plugin install, a hook) throws, leaving the caller
 * responsible for the fallback mount.
 */
export async function bootstrapApp(
  options: AppBootstrapOptions,
): Promise<App> {
  options.initTheme();
  options.applyTypographyDensity(options.density);

  const app = createApp(options.rootComponent);
  for (const plugin of options.plugins) {
    app.use(plugin);
  }

  if (options.preAuthInit) {
    await options.preAuthInit({ app });
  }

  options.registerAuthFailureHandler(options.onAuthFailure);

  try {
    await options.initializeAuth();
  } catch (error) {
    console.error("[Bootstrap] Auth initialization failed:", error);
  }

  app.use(options.router);

  if (options.preMount) {
    await options.preMount({ app });
  }

  app.mount(options.mountSelector ?? "#app");
  return app;
}
