import { describe, expect, it, vi } from "vitest";
import router from "../index";
import { useAuthStore } from "@/stores/auth";

vi.mock("@/stores/auth", () => ({
  useAuthStore: vi.fn(),
}));

vi.mock("@/shared/auth-core/src", () => ({
  installAuthNavigation: vi.fn(),
}));

describe("Router Landing and Root Redirect", () => {
  it("defines the /landing route", () => {
    const landingRoute = router.getRoutes().find((r) => r.path === "/landing");
    expect(landingRoute).toBeDefined();
    expect(landingRoute?.name).toBe("landing");
  });

  it("redirects / to /landing when unauthenticated", () => {
    vi.mocked(useAuthStore).mockReturnValue({
      isAuthenticated: false,
    } as never);

    const rootRoute = router.options.routes.find((r) => r.path === "/");
    expect(rootRoute).toBeDefined();
    if (typeof rootRoute?.redirect === "function") {
      const target = rootRoute.redirect({} as never);
      expect(target).toBe("/landing");
    } else {
      expect.fail("Root redirect is not a function");
    }
  });

  it("redirects / to /problemset when authenticated", () => {
    vi.mocked(useAuthStore).mockReturnValue({
      isAuthenticated: true,
    } as never);

    const rootRoute = router.options.routes.find((r) => r.path === "/");
    expect(rootRoute).toBeDefined();
    if (typeof rootRoute?.redirect === "function") {
      const target = rootRoute.redirect({} as never);
      expect(target).toBe("/problemset");
    } else {
      expect.fail("Root redirect is not a function");
    }
  });
});
