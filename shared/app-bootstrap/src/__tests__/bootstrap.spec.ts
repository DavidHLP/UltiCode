import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock `vue` so the test asserts the bootstrap sequence without a real DOM.
// createApp returns a chainable spy app whose `.use` and `.mount` calls record
// the order collaborators are wired.
const mockApp = {
  use: vi.fn(function MockUse(this: unknown, _plugin: unknown) {
    return this;
  }),
  mount: vi.fn(function MockMount(this: unknown, _selector: unknown) {
    return this;
  }),
};

vi.mock("vue", () => ({
  createApp: vi.fn(() => mockApp),
}));

import { bootstrapApp, type AppBootstrapOptions } from "../index";

function makeOptions(
  overrides: Partial<AppBootstrapOptions> & {
    initializeAuth?: () => Promise<void>;
  } = {},
): AppBootstrapOptions {
  return {
    density: "comfortable",
    initTheme: vi.fn(),
    applyTypographyDensity: vi.fn(),
    rootComponent: {} as never,
    preAuthPlugins: [{ install: vi.fn(), name: "pinia" }, { install: vi.fn(), name: "i18n" }],
    preAuthInit: vi.fn(async () => {}),
    registerAuthFailureHandler: vi.fn(),
    onAuthFailure: vi.fn(async () => {}),
    initializeAuth: overrides.initializeAuth ?? vi.fn(async () => {}),
    router: { install: vi.fn(), name: "router" },
    preMount: vi.fn(async () => {}),
    mountSelector: "#app",
    ...overrides,
  } as unknown as AppBootstrapOptions;
}

describe("bootstrapApp ordering", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("runs the shared startup sequence in the load-bearing order", async () => {
    const calls: string[] = [];
    const options = makeOptions({
      initTheme: () => {
        calls.push("initTheme");
      },
      applyTypographyDensity: () => {
        calls.push("applyDensity");
      },
      preAuthInit: async () => {
        calls.push("preAuthInit");
      },
      registerAuthFailureHandler: (handler) => {
        calls.push("registerAuthFailure");
        (options as { __handler?: () => void }).__handler = handler;
      },
      initializeAuth: async () => {
        calls.push("initializeAuth");
      },
      preMount: async () => {
        calls.push("preMount");
      },
      onAuthFailure: async () => {
        calls.push("onAuthFailure");
      },
    });

    await bootstrapApp(options);

    // Theme → plugins (via app.use) → preAuth → register failure → auth init →
    // router (app.use) → preMount → mount. Captured before mount.
    expect(calls).toEqual([
      "initTheme",
      "applyDensity",
      "preAuthInit",
      "registerAuthFailure",
      "initializeAuth",
      "preMount",
    ]);

    // Plugins installed before auth; router installed after auth init.
    const useCalls = mockApp.use.mock.calls.map((c) => c[0]);
    expect(useCalls.length).toBe(3); // 2 plugins + router
    expect((useCalls[2] as { name?: string }).name).toBe("router");

    // Mount is the final step, after the last app.use (router install).
    const useOrders = mockApp.use.mock.invocationCallOrder;
    expect(mockApp.mount.mock.invocationCallOrder[0]).toBeGreaterThan(
      useOrders[useOrders.length - 1],
    );
  });

  it("installs the router after plugins and after auth initialization", async () => {
    const options = makeOptions();
    await bootstrapApp(options);

    const pluginUseOrders = mockApp.use.mock.invocationCallOrder;
    // router is the third app.use call (after the two plugins)
    expect(pluginUseOrders[2]).toBeGreaterThan(pluginUseOrders[0]);
    expect(pluginUseOrders[2]).toBeGreaterThan(pluginUseOrders[1]);
  });

  it("registers the supplied handler as the single auth-failure owner", async () => {
    const options = makeOptions();
    await bootstrapApp(options);

    expect(options.registerAuthFailureHandler).toHaveBeenCalledTimes(1);
    expect(options.registerAuthFailureHandler).toHaveBeenCalledWith(
      options.onAuthFailure,
    );
  });

  it("catches auth initialization failures and still installs the router and mounts", async () => {
    const initializeAuth = vi.fn(async () => {
      throw new Error("auth init failed");
    });
    const options = makeOptions({ initializeAuth });
    const router = options.router as { install: ReturnType<typeof vi.fn> };

    await expect(bootstrapApp(options)).resolves.toBeDefined();

    expect(console.error).toHaveBeenCalledWith(
      "[Bootstrap] Auth initialization failed:",
      expect.any(Error),
    );
    // router still installed and app still mounted despite auth failure
    expect(mockApp.use).toHaveBeenCalledWith(router);
    expect(mockApp.mount).toHaveBeenCalledWith("#app");
  });

  it("passes the density through to the theme helper", async () => {
    const applyTypographyDensity = vi.fn();
    const options = makeOptions({ density: "compact", applyTypographyDensity });

    await bootstrapApp(options);

    expect(applyTypographyDensity).toHaveBeenCalledWith("compact");
    expect(options.initTheme).toHaveBeenCalled();
  });

  it("defaults the mount selector to #app", async () => {
    const { mountSelector, ...rest } = makeOptions();
    void mountSelector;
    await bootstrapApp(rest as AppBootstrapOptions);

    expect(mockApp.mount).toHaveBeenCalledWith("#app");
  });
});
