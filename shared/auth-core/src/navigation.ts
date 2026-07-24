// ---------------------------------------------------------------------------
// Authenticated navigation policy
// ---------------------------------------------------------------------------
//
// Single seam that concentrates the cross-cutting authenticated-navigation
// policy previously duplicated verbatim in `console/src/router/index.ts` and
// `management/src/router/index.ts`:
//
//   1. Wait for auth initialization before making routing decisions
//      (prevents premature redirect to login when the app mounts faster than
//      the `/auth/me` bootstrap call).
//   2. Re-validate sessions whose last successful check is older than the
//      staleness window (token may have expired while the user was idle).
//   3. Detect navigation aborts — every new navigation bumps a counter; if
//      an async guard awakens to find itself superseded, it bails out.
//   4. Hand the actual redirect target back to the caller via callbacks so
//      each app keeps its own route table and post-redirect conventions.
//
// Both apps keep their own route definitions, meta fields, and per-app
// post-auth redirects. The shared module owns ordering and timing only.
// ---------------------------------------------------------------------------

import type { AuthStatus, User } from './types';

// Apps may use slightly different status enumerations (e.g. console uses
// 'ready' where the canonical `AuthStatus` uses 'authenticated'). The seam
// only branches on the 'loading' value — for everything else the caller
// decides what the auth state means. We therefore accept any string and
// keep the canonical union for documentation only.
export type NavigationStatus = string;

export type NavigationVerdict =
  | true
  | { name: string; query?: Record<string, unknown> };

/**
 * The shape of a Vue Router `to` we actually read. Kept structural (not typed
 * against `RouteLocationNormalized`) so the seam stays Vue-router-free at the
 * type level — apps pass a narrow view of the real route.
 */
export interface NavigationTarget {
  name?: string | symbol | null;
  fullPath: string;
  query: Record<string, unknown>;
  matched: ReadonlyArray<{ meta: Record<string, unknown> }>;
}

/** Auth status snapshot + actions the seam needs. */
export interface NavigationAuthAdapter {
  /** Current auth status. The seam only branches on `'loading'`. Apps can
   *  return their own status string (e.g. `'ready'` instead of
   *  `'authenticated'`); the canonical `AuthStatus` is just a hint. */
  status(): NavigationStatus;
  /** Cached `!!user` — cheaper than reading the whole user record. */
  isAuthenticated(): boolean;
  /** Resolves when the current `startInitialization` finishes, or immediately
   *  if no initialization is in flight. Mirrors `authStore.initializationPromise`. */
  waitForInitialization(): Promise<void>;
  /** Force a backend `/auth/me` round-trip — used for staleness revalidation. */
  fetchUser(): Promise<void>;
  /** Lazy-load user if missing; no-op if already loaded. */
  ensureUser(): Promise<void>;
}

/** Internal seam — both apps use a real `Date.now`, tests inject a fake clock. */
export interface NavigationClock {
  now(): number;
}

const systemClock: NavigationClock = { now: () => Date.now() };

/**
 * Default stale-session window shared by both apps: 5 minutes. Each app
 * previously hard-coded this as a local constant; the value lives here now
 * so callers can omit {@link NavigationPolicyOptions.staleSessionMs} unless
 * they need a genuinely different policy. Exposed for tests that want to
 * assert the default.
 */
export const DEFAULT_STALE_SESSION_MS = 5 * 60 * 1000;

export interface NavigationPolicyOptions {
  /**
   * A session older than this must be revalidated before protected navigation.
   * Optional — defaults to {@link DEFAULT_STALE_SESSION_MS} (5 minutes), the
   * value both apps previously hard-coded. Override only when an app has a
   * genuinely different revalidation policy.
   */
  staleSessionMs?: number;
  /** Route name to redirect to when authentication is required. */
  loginRouteName: string;
  /** Optional: route name for authenticated users who land on a guest-only route. */
  authenticatedGuestRouteName?: string;
}

/** Result of a navigation evaluation. */
export type NavigationDecision =
  | { kind: 'allow' }
  | { kind: 'redirect'; name: string; query?: Record<string, unknown> };

/**
 * Helper: `to.matched.some((r) => r.meta.<key> === true)`. Vue Router's `meta`
 * shape is intentionally loose; we only ever read two booleans here.
 */
function routeMetaHasBoolean(to: NavigationTarget, key: string): boolean {
  return to.matched.some((record) => record.meta?.[key] === true);
}

export interface NavigationPolicy {
  /**
   * Run the navigation policy for a route change. Returns `allow` or a
   * `redirect`. Callers translate that into Vue Router's `boolean | RouteLocationRaw`.
   */
  evaluate(to: NavigationTarget): Promise<NavigationDecision>;
}

/**
 * Build a navigation policy. Each app constructs one with its own auth
 * adapter and per-app redirects.
 */
export function createNavigationPolicy(
  auth: NavigationAuthAdapter,
  policy: NavigationPolicyOptions,
  clock: NavigationClock = systemClock,
): NavigationPolicy {
  // Resolve the stale-session window once, applying the shared default when
  // the caller omits it. Both apps used to hard-code 5 minutes; the default
  // keeps that policy living in one place.
  const staleSessionMs = policy.staleSessionMs ?? DEFAULT_STALE_SESSION_MS;

  // Cancellation token. Every new navigation bumps this; async guards compare
  // their captured id against the current value to bail out when superseded.
  // Kept closure-scoped so each `installAuthNavigation` call gets an
  // independent counter — two routers never see each other's bumps.
  let pendingNavigationId = 0;

  // Last successful validation timestamp. 0 means "never validated yet" — we
  // do NOT force a fetch on the first protected navigation, because if the
  // user just logged in the auth store already has a fresh user record.
  let lastValidatedAt = 0;

  async function evaluate(to: NavigationTarget): Promise<NavigationDecision> {
    // 1. Wait for in-flight auth initialization so we don't redirect to login
    //    before the bootstrap call resolves. We match the literal `'loading'`
    //    so apps using `'ready'`/`'authenticated'`/`'guest'` opt out of the
    //    barrier naturally.
    if (auth.status() === 'loading') {
      await auth.waitForInitialization();
      // The installer discards this decision if a newer navigation supersedes
      // it while initialization is pending.
    }

    const requiresAuth = routeMetaHasBoolean(to, 'requiresAuth');
    if (requiresAuth) {
      const isSessionExpired =
        auth.isAuthenticated() &&
        lastValidatedAt > 0 &&
        clock.now() - lastValidatedAt > staleSessionMs;

      if (isSessionExpired) {
        await auth.fetchUser();
      }

      if (!auth.isAuthenticated()) {
        await auth.ensureUser();
      }

      if (!auth.isAuthenticated()) {
        return {
          kind: 'redirect',
          name: policy.loginRouteName,
          query: { redirect: to.fullPath },
        };
      }

      lastValidatedAt = clock.now();
    }

    // Per-app post-auth redirects. The seam only owns the *contract*: each
    // callback receives the route, returns a redirect name + optional query,
    // or `null` to opt out.
    const isGuestOnly = routeMetaHasBoolean(to, 'guestOnly');
    if (isGuestOnly && policy.authenticatedGuestRouteName && auth.isAuthenticated()) {
      return { kind: 'redirect', name: policy.authenticatedGuestRouteName };
    }

    return { kind: 'allow' };
  }

  return {
    evaluate,
  };
}

// ---------------------------------------------------------------------------
// Vue Router adapter — the only file in this module that imports from
// `vue-router`. Both apps consume this so their `beforeEach` handlers stay
// one-liners.
// ---------------------------------------------------------------------------

export interface VueRouterLike {
  beforeEach(guard: (to: VueRouterTo) => unknown): void;
}

export interface VueRouterTo {
  name?: string | symbol | null;
  fullPath: string;
  query: Record<string, unknown>;
  matched: ReadonlyArray<{ meta: Record<string, unknown> }>;
}

export interface InstallAuthNavigationOptions {
  router: VueRouterLike;
  /**
   * Factory that returns the auth adapter. The factory is invoked lazily
   * on every navigation so that store lookups (e.g. {@code useAuthStore()})
   * happen AFTER Pinia has been installed by main.ts. Passing a
   * pre-constructed adapter here would force the adapter to call
   * {@code useAuthStore()} at router/index.ts module-eval time, which
   * runs before {@code app.use(pinia)} and fails with
   * "getActivePinia() was called but there was no active Pinia".
   */
  auth: () => NavigationAuthAdapter;
  policy: NavigationPolicyOptions;
  clock?: NavigationClock;
}

/**
 * Install the shared navigation guard onto a Vue Router instance.
 *
 * Both apps call this in `router/index.ts` and then layer their own
 * per-app post-auth redirects on top of the verdict.
 *
 * Error handling: the guard wraps {@code nav.evaluate(to)} in a try/catch
 * and emits `undefined` (Vue Router's "do nothing" sentinel) on a rejected
 * promise. This prevents a backend 5xx during revalidation from leaking
 * as an unhandled rejection from a router guard. The supersession
 * (T14) check still runs after the catch so a cancelled navigation does
 * not emit a stale redirect from the still-pending guard.
 */
export function installAuthNavigation(
  options: InstallAuthNavigationOptions,
): void {
  // Resolve the auth adapter lazily on each navigation so that any
  // Pinia store lookup inside the factory runs after `app.use(pinia)`.
  const auth = () => options.auth();
  const nav = createNavigationPolicy(
    // Wrap each method so the factory is invoked on the first call.
    // The auth adapter object itself is captured once per evaluation,
    // so internal closures over `useAuthStore()` see a consistent store.
    wrapAdapter(auth),
    options.policy,
    options.clock,
  );
  let pendingNavigationId = 0;

  options.router.beforeEach(async (to) => {
    pendingNavigationId += 1;
    const navId = pendingNavigationId;

    let decision;
    try {
      decision = await nav.evaluate(to);
    } catch (error) {
      // Swallow rejected evaluation promises so a backend 5xx during
      // staleness revalidation does not surface as an unhandled
      // rejection from a router guard. Vue Router treats the
      // returned `undefined` as "no redirect — let the navigation
      // proceed" so the user lands on the requested page (possibly
      // unauthenticated, which the per-page auth-state handling can
      // recover from). The supersession check below still runs.
      if (navId !== pendingNavigationId) return;
      console.error('[auth-navigation] evaluate() rejected:', error);
      return;
    }
    if (navId !== pendingNavigationId) return;

    if (decision.kind === 'allow') return true;
    return { name: decision.name, query: decision.query };
  });
}

/**
 * Wrap an auth factory so {@link NavigationPolicy} can call it as if it
 * were a regular {@link NavigationAuthAdapter}. The factory is invoked
 * once per {@code evaluate(to)} call.
 */
function wrapAdapter(
  factory: () => NavigationAuthAdapter,
): NavigationAuthAdapter {
  return {
    status: () => factory().status(),
    isAuthenticated: () => factory().isAuthenticated(),
    waitForInitialization: () => factory().waitForInitialization(),
    fetchUser: () => factory().fetchUser(),
    ensureUser: () => factory().ensureUser(),
  };
}

// Re-export the shared User type so apps don't have to chase the secondary
// import when building their adapter.
export type { AuthStatus, User };
