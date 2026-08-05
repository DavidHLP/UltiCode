import type { User } from "@/types/auth";
import { apiGet, apiPost } from "@/utils/request";
import { createSessionAuthStore, csrfManager } from "@/shared/auth-core/src";

/**
 * Detect whether the browser has a `csrf_token` cookie set.
 *
 * The backend writes this cookie as a non-httpOnly sentinel on every
 * successful login / register / refresh. Reading it directly from
 * `document.cookie` lets us detect "there is a session to restore" even
 * after a hard page refresh, when the in-memory `csrfManager` token has
 * been wiped. The `access_token` cookie is httpOnly and inaccessible to
 * JS, so this sentinel cookie is the only client-readable signal of an
 * existing session.
 *
 * Returns false in non-browser environments (SSR, tests without jsdom).
 */
function hasCsrfCookie(): boolean {
  if (typeof document === "undefined") return false;
  return document.cookie
    .split(";")
    .some((c) => c.trim().startsWith("csrf_token="));
}

/**
 * Authentication session composable.
 *
 * Thin console-side transport adapter over the shared
 * {@link createSessionAuthStore} policy. The status machine, dedup init,
 * CSRF-cookie gate, and clear/reset teardown live in `shared/auth-core`; this
 * wrapper only binds the console `request` helper endpoints and the
 * console-specific CSRF-cookie sentinel. The store reads the returned refs and
 * layers its own computed selectors on top.
 */
/**
 * Merge the current user's profile avatar onto the identity user.
 *
 * `avatar` is profile-domain data (App-owned, served by `GET /users/me`)
 * and is intentionally absent from `/auth/me`, which the migration guide
 * (MICROSERVICE_MIGRATION_GUIDE § Compatibility Strategy) keeps
 * identity-only. The auth session only carries identity, but every surface
 * that reads `authStore.user.avatar` expects a URL, so we best-effort fetch
 * it here and merge it onto the identity user.
 *
 * Failures are swallowed: losing the avatar must never lose the identity.
 */
async function withProfileAvatar(user: User): Promise<User> {
  try {
    const profile = await apiGet<{ avatar?: string | null }>("/users/me", {
      skipErrorHandler: true,
    });
    return profile?.avatar ? { ...user, avatar: profile.avatar } : user;
  } catch {
    return user;
  }
}

export function useAuthSession() {
  return createSessionAuthStore<User>({
    fetchCurrentUser: async () => {
      const res = await apiGet<{ user: User; csrfToken?: string }>("/auth/me", {
        skipErrorHandler: true,
      });
      if (res?.user) {
        res.user = await withProfileAvatar(res.user);
      }
      return res;
    },
    login: async (credentials) => {
      const res = await apiPost<{ user: User; csrfToken?: string }>(
        "/auth/login",
        credentials,
      );
      if (res?.user) {
        res.user = await withProfileAvatar(res.user);
      }
      return res;
    },
    register: async (data) => {
      const res = await apiPost<{ user: User; csrfToken?: string }>(
        "/auth/register",
        data,
      );
      if (res?.user) {
        res.user = await withProfileAvatar(res.user);
      }
      return res;
    },
    logout: () => apiPost<void>("/auth/logout"),
    loadPermissions: () =>
      apiGet<string[]>("/auth/permissions", { skipErrorHandler: true }),
    hasSessionCookie: hasCsrfCookie,
    refreshCsrf: (response) => csrfManager.refreshFromResponse(response),
    clearCsrf: () => csrfManager.clearToken(),
  });
}

export type AuthSession = ReturnType<typeof useAuthSession>;
