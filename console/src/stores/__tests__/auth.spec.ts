import { describe, it, expect, vi, beforeEach } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useAuthStore } from "@/stores/auth";
import { apiGet, apiPost } from "@/utils/request";
import { csrfManager } from "@/shared/auth-core/src";
import type { User } from "@/types/auth";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

// Partial mock of @/shared/auth-core/src — keep the real re-exports (e.g. checkPermission
// types may be used by the store) while overriding only csrfManager with test doubles.
vi.mock("@/shared/auth-core/src", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/shared/auth-core/src")>();
  return {
    ...actual,
    csrfManager: {
      clearToken: vi.fn(),
      refreshFromResponse: vi.fn(),
      getToken: vi.fn(() => "test-csrf-token"),
      setToken: vi.fn(),
    },
  };
});

const mockUser: User = {
  id: "1",
  username: "testuser",
  name: "Test User",
  email: "test@example.com",
  role: "USER",
  isActive: true,
  joinedAt: "2026-01-01T00:00:00Z",
};

describe("useAuthStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    // Default: no csrf_token cookie in the browser (anonymous visitor).
    // Tests that need "session present" must explicitly set
    // `document.cookie = "csrf_token=..."` before calling store.initialize().
    document.cookie = "csrf_token=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    const store = useAuthStore();
    store.reset();
  });

  describe("login", () => {
    it("transitions idle -> loading -> ready on success", async () => {
      const store = useAuthStore();
      expect(store.status).toBe("idle");

      vi.mocked(apiPost).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf-new",
      });

      const loginPromise = store.login({
        username: "testuser",
        password: "password123",
      });
      expect(store.status).toBe("loading");

      await loginPromise;

      expect(store.status).toBe("ready");
      expect(store.user).toEqual(mockUser);
      expect(store.isAuthenticated).toBe(true);
      expect(store.error).toBeNull();
    });

    it("calls refreshFromResponse with CSRF token from login", async () => {
      const store = useAuthStore();

      vi.mocked(apiPost).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf-new",
      });

      await store.login({ username: "testuser", password: "password123" });

      expect(csrfManager.refreshFromResponse).toHaveBeenCalledWith({
        csrfToken: "csrf-new",
      });
    });

    it("transitions idle -> loading -> error on failure", async () => {
      const store = useAuthStore();

      const loginError = new Error("Invalid credentials");
      vi.mocked(apiPost).mockRejectedValue(loginError);

      await expect(
        store.login({ username: "testuser", password: "wrong" }),
      ).rejects.toThrow("Invalid credentials");

      expect(store.status).toBe("error");
      expect(store.user).toBeNull();
      expect(store.isAuthenticated).toBe(false);
      expect(store.error).toBe(loginError);
    });

    it("sends credentials to /auth/login", async () => {
      const store = useAuthStore();
      const credentials = { username: "testuser", password: "pass123" };

      vi.mocked(apiPost).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf",
      });
      await store.login(credentials);

      expect(apiPost).toHaveBeenCalledWith("/auth/login", credentials);
    });
  });

  describe("initialize", () => {
    it("skips /auth/me when no CSRF cookie exists", async () => {
      // beforeEach already cleared the cookie — anonymous visitor.
      const store = useAuthStore();
      await store.initialize();

      expect(apiGet).not.toHaveBeenCalled();
      expect(store.status).toBe("ready");
      expect(store.user).toBeNull();
    });

    it("calls /auth/me when csrf_token cookie exists and sets user", async () => {
      // Simulate a hard refresh with the sentinel cookie still in the jar
      // (the in-memory csrfManager would be empty here, by design).
      document.cookie = "csrf_token=valid-csrf; path=/";
      vi.mocked(apiGet).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf-refreshed",
      });

      const store = useAuthStore();
      await store.initialize();

      expect(apiGet).toHaveBeenCalledWith("/auth/me", {
        skipErrorHandler: true,
      });
      expect(store.user).toEqual(mockUser);
      expect(store.status).toBe("ready");
      expect(store.isAuthenticated).toBe(true);
    });

    it("still transitions to ready when /auth/me fails", async () => {
      document.cookie = "csrf_token=valid-csrf; path=/";
      vi.mocked(apiGet).mockRejectedValue(new Error("Network error"));

      const store = useAuthStore();
      await store.initialize();

      expect(store.status).toBe("ready");
      expect(store.user).toBeNull();
    });

    it("deduplicates concurrent initialize calls", async () => {
      document.cookie = "csrf_token=valid-csrf; path=/";
      // Delay the API response to ensure concurrent calls overlap
      vi.mocked(apiGet).mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(
              () =>
                resolve({
                  user: mockUser,
                  csrfToken: "csrf",
                }),
              10,
            ),
          ),
      );

      const store = useAuthStore();
      const [result1, result2] = await Promise.all([
        store.initialize(),
        store.initialize(),
      ]);

      // apiGet should only be called once (deduplicated)
      expect(apiGet).toHaveBeenCalledTimes(1);
      expect(result1).toBeUndefined();
      expect(result2).toBeUndefined();
    });

    it("skips initialize if already ready", async () => {
      document.cookie = "csrf_token=valid-csrf; path=/";
      vi.mocked(apiGet).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf",
      });

      const store = useAuthStore();
      await store.initialize();
      expect(apiGet).toHaveBeenCalledTimes(1);

      // Second call should skip
      await store.initialize();
      expect(apiGet).toHaveBeenCalledTimes(1);
    });
  });

  describe("clearUser", () => {
    it("resets user to null and clears permissions", () => {
      const store = useAuthStore();
      // Manually set state
      store.$patch({
        user: mockUser,
        status: "ready" as const,
      });

      store.clearUser();

      expect(store.user).toBeNull();
      expect(store.isAuthenticated).toBe(false);
      expect(store.status).toBe("ready");
      expect(store.error).toBeNull();
    });

    it("calls csrfManager.clearToken", () => {
      const store = useAuthStore();
      store.clearUser();

      expect(csrfManager.clearToken).toHaveBeenCalled();
    });
  });

  describe("reset", () => {
    it("sets status back to idle", () => {
      const store = useAuthStore();
      store.$patch({ status: "ready" });

      store.reset();

      expect(store.status).toBe("idle");
    });

    it("clears user and error", () => {
      const store = useAuthStore();
      store.$patch({
        user: mockUser,
        error: new Error("some error"),
      });

      store.reset();

      expect(store.user).toBeNull();
      expect(store.error).toBeNull();
    });

    it("resets _initializationPromise for test isolation", async () => {
      document.cookie = "csrf_token=valid-csrf; path=/";
      vi.mocked(apiGet).mockResolvedValue({
        user: mockUser,
        csrfToken: "csrf",
      });

      const store = useAuthStore();
      // Initialize once
      await store.initialize();
      expect(store.status).toBe("ready");

      // Reset should allow re-initialization
      store.reset();
      expect(store.status).toBe("idle");

      // Re-initialize should work (not be deduplicated)
      await store.initialize();
      expect(store.status).toBe("ready");
      expect(apiGet).toHaveBeenCalledTimes(2);
    });

    it("calls csrfManager.clearToken", () => {
      const store = useAuthStore();
      store.reset();

      expect(csrfManager.clearToken).toHaveBeenCalled();
    });
  });

  describe("logout", () => {
    it("calls apiPost with /auth/logout and clears user state", async () => {
      const store = useAuthStore();
      store.$patch({ user: mockUser });
      vi.mocked(apiPost).mockResolvedValue(undefined);

      await store.logout();

      expect(apiPost).toHaveBeenCalledWith("/auth/logout");
      expect(store.user).toBeNull();
      expect(store.status).toBe("ready");
    });

    it("clears user even when logout API fails", async () => {
      const store = useAuthStore();
      store.$patch({ user: mockUser });
      vi.mocked(apiPost).mockRejectedValue(new Error("Network error"));

      await store.logout();

      expect(store.user).toBeNull();
      expect(store.status).toBe("ready");
    });
  });

  describe("computed properties", () => {
    it("isAuthenticated returns false when user is null", () => {
      const store = useAuthStore();
      expect(store.isAuthenticated).toBe(false);
    });

    it("isAuthenticated returns true when user is set", () => {
      const store = useAuthStore();
      store.$patch({ user: mockUser });
      expect(store.isAuthenticated).toBe(true);
    });

    it("isInitialized returns true when status is ready", () => {
      const store = useAuthStore();
      store.$patch({ status: "ready" });
      expect(store.isInitialized).toBe(true);
    });

    it("isLoading returns true when status is loading", () => {
      const store = useAuthStore();
      store.$patch({ status: "loading" });
      expect(store.isLoading).toBe(true);
    });

    it("userId returns empty string when user is null", () => {
      const store = useAuthStore();
      expect(store.userId).toBe("");
    });

    it("userId returns user id when user is set", () => {
      const store = useAuthStore();
      store.$patch({ user: mockUser });
      expect(store.userId).toBe("1");
    });
  });
});
