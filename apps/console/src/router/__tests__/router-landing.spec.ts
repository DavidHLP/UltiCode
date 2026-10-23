import { describe, expect, it, vi } from "vitest";
import router from "../index";
import { useAuthStore } from "@/stores/auth";

vi.mock("@/stores/auth", () => ({
  useAuthStore: vi.fn(),
}));

vi.mock("@ulticode/auth-core", () => ({
  installAuthNavigation: vi.fn(),
}));

describe("Router Landing and Root Redirect", () => {
  it.each(["global", "local"])(
    "preserves the %s ranking entry scope",
    (scope) => {
      const entry = router
        .getRoutes()
        .find((route) => route.path === `/contest/${scope}-ranking`);
      expect(typeof entry?.redirect).toBe("function");
      if (typeof entry?.redirect === "function") {
        expect(
          entry.redirect({ query: { page: "2", scope: "other" } } as never),
        ).toEqual({
          name: "contest-rankings",
          query: { page: "2", scope },
        });
      }
    },
  );

  it("starts a different page at the top", () => {
    expect(router.options.scrollBehavior?.(
      router.resolve("/forum/popular"),
      router.resolve("/forum/guidelines"),
      null,
    )).toEqual({ left: 0, top: 0 });
  });

  it("restores a history entry's saved position", () => {
    const saved = { left: 0, top: 604 };
    expect(router.options.scrollBehavior?.(
      router.resolve("/forum/guidelines"),
      router.resolve("/forum/popular"),
      saved,
    )).toBe(saved);
  });

  it("preserves scrolling for filters on the same page", () => {
    expect(router.options.scrollBehavior?.(
      router.resolve("/forum/popular?sort=new"),
      router.resolve("/forum/popular"),
      null,
    )).toBe(false);
  });

  it("uses the target's scroll margin for hash navigation", () => {
    const target = document.createElement("h2");
    target.id = "scroll-target";
    target.style.scrollMarginTop = "56px";
    document.body.appendChild(target);
    try {
      expect(router.options.scrollBehavior?.(
        router.resolve("/forum/guidelines#scroll-target"),
        router.resolve("/forum/guidelines"),
        null,
      )).toEqual({ el: target, top: 56 });
    } finally {
      target.remove();
    }
  });

  it("hands the legacy /landing route to the standalone app", () => {
    const landingRoute = router.getRoutes().find((r) => r.path === "/landing");
    expect(landingRoute).toBeDefined();
    expect(landingRoute?.name).toBe("landing");
    expect(landingRoute?.redirect).toBeUndefined();
    expect(landingRoute?.beforeEnter).toBeUndefined();
    expect(landingRoute?.components?.default).toBeDefined();
  });

  it("protects the Agent conversation workspace", () => {
    const workspaceRoute = router
      .getRoutes()
      .find((route) => route.name === "agent-workspace");
    expect(workspaceRoute?.path).toBe("/agent");
    expect(workspaceRoute?.meta.requiresAuth).toBe(true);
  });

  it("redirects / to standalone Landing when unauthenticated", () => {
    vi.mocked(useAuthStore).mockReturnValue({
      isAuthenticated: false,
    } as never);

    const rootRoute = router.options.routes.find((r) => r.path === "/");
    expect(rootRoute).toBeDefined();
    if (typeof rootRoute?.redirect === "function") {
      const target = rootRoute.redirect({} as never);
      expect(target).toBe("/landing/");
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
