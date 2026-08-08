import { describe, it, expect, beforeEach, vi, afterEach } from "vitest";
import { useNetworkStatus } from "../useNetworkStatus";

describe("useNetworkStatus", () => {
  // Create custom event helper
  const dispatchNetworkEvent = (type: "online" | "offline") => {
    window.dispatchEvent(new Event(type));
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("initial state", () => {
    it("should return online status based on navigator.onLine", () => {
      // navigator.onLine defaults to true in jsdom
      const { isOnline } = useNetworkStatus();
      expect(isOnline.value).toBe(true);
    });
  });

  describe("computed properties", () => {
    it("should have online computed property", () => {
      const { online } = useNetworkStatus();
      expect(online.value).toBe(true);
    });

    it("should have offline computed property", () => {
      const { offline } = useNetworkStatus();
      expect(offline.value).toBe(false);
    });
  });

  describe("event listeners", () => {
    it("should update isOnline when online event fires", () => {
      const { isOnline, startListening } = useNetworkStatus();

      startListening();
      // Simulate going offline first
      isOnline.value = false;

      // Then simulate coming back online
      dispatchNetworkEvent("online");

      expect(isOnline.value).toBe(true);
    });

    it("should update isOnline when offline event fires", () => {
      const { isOnline, startListening } = useNetworkStatus();

      startListening();
      dispatchNetworkEvent("offline");

      expect(isOnline.value).toBe(false);
    });
  });

  describe("subscribe", () => {
    it("should call listener when online status changes", () => {
      const { subscribe, startListening } = useNetworkStatus();
      const listener = vi.fn();

      startListening();
      subscribe(listener);

      dispatchNetworkEvent("offline");

      expect(listener).toHaveBeenCalledWith(false);
    });

    it("should return unsubscribe function", () => {
      const { subscribe, startListening } = useNetworkStatus();
      const listener = vi.fn();

      startListening();
      const unsubscribe = subscribe(listener);

      unsubscribe();

      dispatchNetworkEvent("offline");

      expect(listener).not.toHaveBeenCalled();
    });
  });

  describe("checkConnectivity", () => {
    it("should return current navigator.onLine value", () => {
      const { checkConnectivity } = useNetworkStatus();

      // navigator.onLine defaults to true in jsdom
      expect(checkConnectivity()).toBe(true);
    });
  });
});
