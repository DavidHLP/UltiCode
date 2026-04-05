import { reactive, readonly, ref } from 'vue';

// Re-use AuthStatus from ./types to avoid duplicate definition.
// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

import type { AuthStatus } from './types';
export type { AuthStatus };

export interface AuthUser {
  id: string;
  username: string;
  email?: string;
  roles?: string[];
  permissions?: string[];
  [key: string]: unknown;
}

export interface AuthState {
  status: AuthStatus;
  user: AuthUser | null;
  error: Error | null;
}

interface AuthStateReactive {
  status: AuthStatus;
  user: AuthUser | null;
  error: Error | null;
}

// ---------------------------------------------------------------------------
// State machine
// ---------------------------------------------------------------------------

export function createAuthStateMachine() {
  const state = reactive<AuthStateReactive>({
    status: 'idle',
    user: null,
    error: null,
  });

  /**
   * Promise that resolves when the current initialization finishes.
   * Used to dedupe concurrent calls to startInitialization().
   */
  const initializationPromise = ref<Promise<void> | null>(null);

  // ---- transition helpers -------------------------------------------------

  function startInitialization(): Promise<void> {
    // If an initialization is already in-flight, return the existing promise.
    if (initializationPromise.value) {
      return initializationPromise.value;
    }

    state.status = 'loading';
    state.error = null;

    // Deferred resolver – set by startInitialization, called by the transition
    // functions below to notify all concurrent waiters.
    let resolveInit: (() => void) | null = null;

    const promise = new Promise<void>((resolve) => {
      resolveInit = resolve;
    });

    initializationPromise.value = promise;
    // Store the resolver so completeInitialization / failInitialization can call it.
    (initializationPromise as unknown as { _resolve?: () => void })._resolve = resolveInit!;

    return promise;
  }

  function completeInitialization(user: AuthUser | null): void {
    if (state.status !== 'loading') return;

    if (user) {
      state.user = user;
      state.status = 'authenticated';
    } else {
      state.user = null;
      state.status = 'guest';
    }
    state.error = null;

    // Notify any concurrent callers of startInitialization().
    const resolver = (initializationPromise as unknown as { _resolve?: () => void })._resolve;
    if (resolver) resolver();
    initializationPromise.value = null;
  }

  function failInitialization(error: Error): void {
    if (state.status !== 'loading') return;

    state.status = 'error';
    state.error = error;
    state.user = null;

    const resolver = (initializationPromise as unknown as { _resolve?: () => void })._resolve;
    if (resolver) resolver();
    initializationPromise.value = null;
  }

  /**
   * Update user data without changing the auth status.
   * Useful for refreshing user profile info mid-session.
   */
  function setUser(user: AuthUser | null): void {
    if (state.status !== 'authenticated' && state.status !== 'guest') return;
    state.user = user;
    if (user === null && state.status === 'authenticated') {
      state.status = 'guest';
    }
  }

  function reset(): void {
    state.status = 'idle';
    state.user = null;
    state.error = null;
    initializationPromise.value = null;
  }

  return {
    // Expose a readonly view to consumers.
    state: readonly(state) as Readonly<AuthState>,
    // Transition functions.
    startInitialization,
    completeInitialization,
    failInitialization,
    setUser,
    reset,
    // Allow callers to create their own instance.
    createAuthStateMachine,
  };
}

// ---------------------------------------------------------------------------
// Singleton export (shared across the entire app)
// ---------------------------------------------------------------------------

export const authStateMachine = createAuthStateMachine();
