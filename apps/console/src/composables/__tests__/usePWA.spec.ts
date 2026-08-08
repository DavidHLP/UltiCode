import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Note: virtual:pwa-register is mocked globally in vitest.config.ts

describe("usePWA", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Reset modules to clear cached imports and state
    vi.resetModules();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("should provide offline-ready state", async () => {
    const { usePWA } = await import("../usePWA");
    const { isOfflineReady } = usePWA();

    expect(isOfflineReady.value).toBe(false);
  });

  it("should provide need-refresh state", async () => {
    const { usePWA } = await import("../usePWA");
    const { needRefresh } = usePWA();

    expect(needRefresh.value).toBe(false);
  });

  it("should provide update service worker function", async () => {
    const { usePWA } = await import("../usePWA");
    const { updateServiceWorker } = usePWA();

    expect(typeof updateServiceWorker).toBe("function");
  });

  it("should provide close function", async () => {
    const { usePWA } = await import("../usePWA");
    const { close } = usePWA();

    expect(typeof close).toBe("function");
  });

  it("should call setUpdateCallback on initialization", async () => {
    const pwaRegister = await import("@/pwa-register");
    const spy = vi.spyOn(pwaRegister, "setUpdateCallback");
    const { usePWA } = await import("../usePWA");

    usePWA();

    expect(spy).toHaveBeenCalled();
  });

  it("should set needRefresh to false when close is called", async () => {
    const { usePWA } = await import("../usePWA");
    const { needRefresh, close } = usePWA();

    // Simulate an update being available
    needRefresh.value = true;

    close();

    expect(needRefresh.value).toBe(false);
  });

  it("should share state across multiple instances", async () => {
    const { usePWA } = await import("../usePWA");

    const instance1 = usePWA();
    const instance2 = usePWA();

    // Modify state through instance 1
    instance1.needRefresh.value = true;

    // Instance 2 should see the same state
    expect(instance2.needRefresh.value).toBe(true);

    // Close through instance 2
    instance2.close();

    // Instance 1 should see the updated state
    expect(instance1.needRefresh.value).toBe(false);
  });
});
