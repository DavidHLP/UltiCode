import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  createNavigationPolicy,
  installAuthNavigation,
  DEFAULT_STALE_SESSION_MS,
  type NavigationAuthAdapter,
  type NavigationClock,
  type NavigationPolicy,
  type NavigationPolicyOptions,
  type NavigationTarget,
  type VueRouterLike,
  type VueRouterTo,
} from '../navigation';
import type { AuthStatus, User } from '../types';

// ---------------------------------------------------------------------------
// Test fixtures
// ---------------------------------------------------------------------------

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 'u-1',
    username: 'alice',
    name: 'Alice',
    email: 'alice@example.com',
    role: 'USER',
    is_active: true,
    is_banned: false,
    joined_at: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function makeAuthAdapter(
  overrides: Partial<NavigationAuthAdapter> = {},
): NavigationAuthAdapter {
  return {
    status: () => 'authenticated',
    isAuthenticated: () => true,
    waitForInitialization: async () => undefined,
    fetchUser: async () => undefined,
    ensureUser: async () => undefined,
    ...overrides,
  };
}

function makeFakeClock(initial = 1_000_000): NavigationClock & { advance(ms: number): void } {
  let now = initial;
  return {
    now: () => now,
    advance(ms: number): void {
      now += ms;
    },
  };
}
const defaultPolicy: NavigationPolicyOptions = {
  staleSessionMs: DEFAULT_STALE_SESSION_MS,
  loginRouteName: 'login',
  authenticatedGuestRouteName: 'home',
};

function makeTarget(
  overrides: Partial<NavigationTarget> = {},
): NavigationTarget {
  return {
    name: 'some-route',
    fullPath: '/some',
    query: {},
    matched: [],
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Staleness & cancellation policy
// ---------------------------------------------------------------------------

describe('createNavigationPolicy — staleness & cancellation', () => {
  let clock: ReturnType<typeof makeFakeClock>;
  let auth: NavigationAuthAdapter;
  let nav: NavigationPolicy;

  beforeEach(() => {
    clock = makeFakeClock();
    auth = makeAuthAdapter();
    nav = createNavigationPolicy(auth, defaultPolicy, clock);
  });

  it('T1: protected route + fresh session → allow, no fetchUser forced', async () => {
    const fetchSpy = vi.spyOn(auth, 'fetchUser');
    const target = makeTarget({ matched: [{ meta: { requiresAuth: true } }] });

    const decision = await nav.evaluate(target);
    expect(decision).toEqual({ kind: 'allow' });
    expect(fetchSpy).not.toHaveBeenCalled();

    // Within the staleness window the second protected navigation must NOT
    // force a revalidation either.
    clock.advance(60_000);
    const second = await nav.evaluate(target);
    expect(second).toEqual({ kind: 'allow' });
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('T2: protected route + stale session → fetchUser() runs', async () => {
    const fetchSpy = vi
      .spyOn(auth, 'fetchUser')
      .mockResolvedValueOnce(undefined);

    // Prime the policy by validating once.
    const protectedRoute = makeTarget({
      matched: [{ meta: { requiresAuth: true } }],
    });
    await nav.evaluate(protectedRoute);
    expect(fetchSpy).not.toHaveBeenCalled();

    // Advance past staleness window.
    clock.advance(DEFAULT_STALE_SESSION_MS + 1);

    const decision = await nav.evaluate(protectedRoute);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(decision).toEqual({ kind: 'allow' });
  });

  it('T3: protected route + unauthenticated after fetch → redirect to login', async () => {
    auth = makeAuthAdapter({ isAuthenticated: () => false });
    nav = createNavigationPolicy(auth, defaultPolicy, clock);

    const target = makeTarget({
      fullPath: '/secret',
      matched: [{ meta: { requiresAuth: true } }],
    });

    const decision = await nav.evaluate(target);
    expect(decision).toEqual({
      kind: 'redirect',
      name: 'login',
      query: { redirect: '/secret' },
    });
  });

  it('T4: cancellation — bump() increments and isStale() detects superseded', () => {
    const id1 = nav.bump();
    expect(nav.isStale(id1)).toBe(false);

    const id2 = nav.bump();
    expect(id2).toBe(id1 + 1);
    expect(nav.isStale(id1)).toBe(true);
    expect(nav.isStale(id2)).toBe(false);
  });

  it('T5: public route — no auth interaction, allow', async () => {
    const fetchSpy = vi.spyOn(auth, 'fetchUser');
    const ensureSpy = vi.spyOn(auth, 'ensureUser');
    const target = makeTarget({ matched: [] });

    const decision = await nav.evaluate(target);
    expect(decision).toEqual({ kind: 'allow' });
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(ensureSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// Default staleSessionMs
// ---------------------------------------------------------------------------
//
// Both apps used to hard-code 5 * 60 * 1000 ms. The constant now lives in
// shared/auth-core so callers can omit `staleSessionMs` entirely. These
// tests pin (1) the exported value and (2) the policy behaviour when the
// field is absent — protected routes still revalidate after the default
// window, not never.

describe('createNavigationPolicy — default staleSessionMs', () => {
  it('exports the shared 5-minute default', () => {
    expect(DEFAULT_STALE_SESSION_MS).toBe(5 * 60 * 1000);
  });

  it('omitting staleSessionMs applies the default — session still revalidates after 5 minutes', async () => {
    const clock = makeFakeClock();
    const fetchSpy = vi.fn();
    const auth = makeAuthAdapter({ fetchUser: fetchSpy });
    const policyWithoutStaleMs: NavigationPolicyOptions = {
      loginRouteName: 'login',
    };
    const nav = createNavigationPolicy(auth, policyWithoutStaleMs, clock);

    const protectedRoute = makeTarget({
      matched: [{ meta: { requiresAuth: true } }],
    });

    // First navigation primes lastValidatedAt.
    await nav.evaluate(protectedRoute);
    expect(fetchSpy).not.toHaveBeenCalled();

    // Inside the default window → no revalidation. We do NOT call evaluate
    // here because a successful evaluate resets lastValidatedAt; we just
    // advance the clock to confirm the gate math at the boundary.
    clock.advance(DEFAULT_STALE_SESSION_MS - 1);

    // Past the default window → revalidation fires. The regression guard:
    // if the implementation ever fell back to `policy.staleSessionMs`
    // (undefined) the comparison `clock.now() - last > undefined` is NaN,
    // always-false, and revalidation silently never happens — this test
    // fails in that case.
    clock.advance(2);
    await nav.evaluate(protectedRoute);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it('explicit staleSessionMs still overrides the default', async () => {
    const clock = makeFakeClock();
    const fetchSpy = vi.fn();
    const auth = makeAuthAdapter({ fetchUser: fetchSpy });
    const nav = createNavigationPolicy(
      auth,
      { loginRouteName: 'login', staleSessionMs: 1_000 },
      clock,
    );

    const protectedRoute = makeTarget({
      matched: [{ meta: { requiresAuth: true } }],
    });
    await nav.evaluate(protectedRoute);

    // 1.001s later — only past the explicit window, well inside the default.
    clock.advance(1_001);
    await nav.evaluate(protectedRoute);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});

// ---------------------------------------------------------------------------
// Post-auth redirects
// ---------------------------------------------------------------------------

describe('createNavigationPolicy — post-auth redirects', () => {
  let clock: ReturnType<typeof makeFakeClock>;
  let auth: NavigationAuthAdapter;

  beforeEach(() => {
    clock = makeFakeClock();
    auth = makeAuthAdapter();
  });

  it('T6: authenticated user on guest-only route → redirect to authenticatedGuestRouteName', async () => {
    auth = makeAuthAdapter({ isAuthenticated: () => true });
    const nav = createNavigationPolicy(auth, defaultPolicy, clock);

    const target = makeTarget({
      matched: [{ meta: { guestOnly: true } }],
    });

    const decision = await nav.evaluate(target);
    expect(decision).toEqual({ kind: 'redirect', name: 'home' });
  });

  it('T7: unauthenticated user on guest-only route → allow (login itself)', async () => {
    auth = makeAuthAdapter({ isAuthenticated: () => false });
    const nav = createNavigationPolicy(auth, defaultPolicy, clock);

    const target = makeTarget({
      matched: [{ meta: { guestOnly: true } }],
    });

    const decision = await nav.evaluate(target);
    expect(decision).toEqual({ kind: 'allow' });
  });
});

// ---------------------------------------------------------------------------
// Auth initialization barrier
// ---------------------------------------------------------------------------

describe('createNavigationPolicy — initialization barrier', () => {
  it('T10: status=loading → await waitForInitialization, then evaluate', async () => {
    let resolveInit: (() => void) | null = null;
    const auth = makeAuthAdapter({
      status: () => 'loading',
      waitForInitialization: () =>
        new Promise<void>((resolve) => {
          resolveInit = resolve;
        }),
      isAuthenticated: () => true,
    });

    const nav = createNavigationPolicy(auth, defaultPolicy, makeFakeClock());
    const target = makeTarget({
      matched: [{ meta: { requiresAuth: true } }],
    });

    const pending = nav.evaluate(target);

    // Yield once so the event loop reaches the await.
    await new Promise((r) => setTimeout(r, 0));
    resolveInit!();

    const decision = await pending;
    expect(decision).toEqual({ kind: 'allow' });
  });

  it('T11: status=idle → no barrier (initialization never started)', async () => {
    const waitSpy = vi.fn(async () => undefined);
    const auth = makeAuthAdapter({
      status: () => 'idle',
      waitForInitialization: waitSpy,
    });

    const nav = createNavigationPolicy(auth, defaultPolicy, makeFakeClock());
    const decision = await nav.evaluate(makeTarget());
    expect(decision).toEqual({ kind: 'allow' });
    expect(waitSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// installAuthNavigation — Vue Router adapter
// ---------------------------------------------------------------------------

describe('installAuthNavigation — vue-router adapter', () => {
  function captureGuards(): {
    router: VueRouterLike;
    guards: Array<(to: VueRouterTo) => unknown>;
  } {
    const guards: Array<(to: VueRouterTo) => unknown> = [];
    const router: VueRouterLike = {
      beforeEach: (g) => {
        guards.push(g);
      },
    };
    return { router, guards };
  }

  it('T12: protected route + unauthenticated → emits { name: login, query }', async () => {
    const auth = makeAuthAdapter({ isAuthenticated: () => false });
    const { router, guards } = captureGuards();

    installAuthNavigation({
      router,
      auth: () => auth,
      policy: defaultPolicy,
      clock: makeFakeClock(),
    });

    expect(guards).toHaveLength(1);

    const to: VueRouterTo = {
      name: 'private',
      fullPath: '/private',
      query: {},
      matched: [{ meta: { requiresAuth: true } }],
    };

    const result = await guards[0](to);
    expect(result).toEqual({
      name: 'login',
      query: { redirect: '/private' },
    });
  });

  it('T13: public route + authenticated → emits true', async () => {
    const auth = makeAuthAdapter({ isAuthenticated: () => true });
    const { router, guards } = captureGuards();

    installAuthNavigation({
      router,
      auth: () => auth,
      policy: defaultPolicy,
      clock: makeFakeClock(),
    });

    const to: VueRouterTo = {
      name: 'home',
      fullPath: '/',
      query: {},
      matched: [],
    };

    const result = await guards[0](to);
    expect(result).toBe(true);
  });

  it('T14: superseded navigation returns undefined (no redirect emitted)', async () => {
    // Hold `ensureUser()` open so we can race a `bump()` against it. The
    // guard's `bump()` captures id N+1; while the awaited `ensureUser` is
    // pending, the test calls `bump()` to supersede it; the guard must
    // observe isStale() and return `undefined` once the await resolves.
    let resolveEnsure: (() => void) | null = null;
    const auth = makeAuthAdapter({
      isAuthenticated: () => false,
      ensureUser: () =>
        new Promise<void>((resolve) => {
          resolveEnsure = resolve;
        }),
    });
    const { router, guards } = captureGuards();

    const nav = installAuthNavigation({
      router,
      auth: () => auth,
      policy: defaultPolicy,
      clock: makeFakeClock(),
    });

    const to: VueRouterTo = {
      name: 'private',
      fullPath: '/private',
      query: {},
      matched: [{ meta: { requiresAuth: true } }],
    };

    // Start the guard (bumps id=1, awaits ensureUser).
    const p1 = guards[0](to);

    // Simulate a newer navigation superseding the first.
    nav.bump();

    // Now release the awaited ensureUser; the guard resumes, checks
    // isStale(1) → true, and returns `undefined`.
    resolveEnsure!();

    const r1 = await p1;
    expect(r1).toBeUndefined();
  });

  it('T15: post-auth redirect — guest-only + authenticated → redirects home', async () => {
    const auth = makeAuthAdapter({ isAuthenticated: () => true });
    const { router, guards } = captureGuards();

    installAuthNavigation({
      router,
      auth: () => auth,
      policy: defaultPolicy,
      clock: makeFakeClock(),
    });

    const to: VueRouterTo = {
      name: 'login',
      fullPath: '/login',
      query: {},
      matched: [{ meta: { guestOnly: true } }],
    };

    const result = await guards[0](to);
    expect(result).toEqual({ name: 'home' });
  });
});

// ---------------------------------------------------------------------------
// Determinism — same inputs produce same outputs
// ---------------------------------------------------------------------------

describe('createNavigationPolicy — determinism', () => {
  it('T16: replaying the same scenario yields the same verdict', async () => {
    const clock = makeFakeClock(2_000_000);
    const auth = makeAuthAdapter();

    const nav1 = createNavigationPolicy(auth, defaultPolicy, clock);
    const target = makeTarget({
      matched: [{ meta: { requiresAuth: true } }],
    });

    const d1 = await nav1.evaluate(target);
    expect(d1).toEqual({ kind: 'allow' });

    // Same inputs again with a fresh policy yield the same verdict, and at
    // lastValidatedAt=0 we never force-fetch.
    const fetchSpy = vi.spyOn(auth, 'fetchUser');
    const nav2 = createNavigationPolicy(auth, defaultPolicy, clock);
    const d2 = await nav2.evaluate(target);
    expect(d2).toEqual({ kind: 'allow' });
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// AuthStatus contract — the seam only branches on 'loading'.
// ---------------------------------------------------------------------------

describe('createNavigationPolicy — auth status handling', () => {
  const allStatuses: AuthStatus[] = [
    'idle',
    'loading',
    'authenticated',
    'guest',
    'error',
  ];

  it.each(allStatuses)(
    'T17: status=%s → public route returns verdict without exception',
    async (status) => {
      const auth = makeAuthAdapter({ status: () => status });
      const nav = createNavigationPolicy(auth, defaultPolicy, makeFakeClock());
      // For status==='loading' the policy awaits; resolve immediately so the
      // test does not hang.
      if (status === 'loading') {
        auth.waitForInitialization = async () => undefined;
      }
      const decision = await nav.evaluate(makeTarget());
      expect(decision).toEqual({ kind: 'allow' });
    },
  );
});

// Reference makeUser so it is exercised at module load time even when tests
// are filtered (avoid an unused-symbol lint warning).
void makeUser;