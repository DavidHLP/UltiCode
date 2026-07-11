import { describe, it, expect, vi, beforeEach } from "vitest";
import { useNotificationFeed } from "../useNotificationFeed";
import {
  fetchNotifications,
  fetchUnreadCount,
  updateNotificationRead,
  markAllNotificationsRead,
  clearNotifications,
  deleteNotification as apiDeleteNotification,
} from "@/api/notification";

vi.mock("@/api/notification", () => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  updateNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  clearNotifications: vi.fn(),
  deleteNotification: vi.fn(),
}));

describe("useNotificationFeed", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("withError path", () => {
    it("captures Error.message and rethrows on markAsRead failure", async () => {
      vi.mocked(updateNotificationRead).mockRejectedValue(
        new Error("boom-update"),
      );
      const feed = useNotificationFeed();

      await expect(feed.markAsRead("n1")).rejects.toThrow("boom-update");
      expect(feed.error.value).toBe("boom-update");
    });

    it("falls back to the supplied message for non-Error throws", async () => {
      vi.mocked(markAllNotificationsRead).mockRejectedValue("nope");
      const feed = useNotificationFeed();

      await expect(feed.markAllRead()).rejects.toBe("nope");
      expect(feed.error.value).toBe("Failed to mark all as read");
    });

    it("uses 'Failed to clear notifications' fallback", async () => {
      vi.mocked(clearNotifications).mockRejectedValue(42);
      const feed = useNotificationFeed();

      await expect(feed.clearAll()).rejects.toBe(42);
      expect(feed.error.value).toBe("Failed to clear notifications");
    });

    it("uses 'Failed to delete notification' fallback", async () => {
      vi.mocked(apiDeleteNotification).mockRejectedValue(
        new Error("delete-fail"),
      );
      const feed = useNotificationFeed();

      await expect(feed.removeNotification("n2")).rejects.toThrow(
        "delete-fail",
      );
      expect(feed.error.value).toBe("delete-fail");
    });

    it("preserves the success path through withError", async () => {
      const updated = {
        id: "n1",
        title: "t",
        body: "b",
        type: "SYSTEM" as const,
        category: "SYSTEM" as const,
        link: null,
        isRead: true,
        readAt: null,
        createdAt: "2026-01-01T00:00:00Z",
      };
      vi.mocked(updateNotificationRead).mockResolvedValue(updated);
      const feed = useNotificationFeed();

      const result = await feed.markAsRead("n1", true);

      expect(result).toEqual(updated);
      expect(feed.error.value).toBeNull();
    });
  });

  describe("loadNotifications", () => {
    it("uses 'Failed to load notifications' fallback and rethrows", async () => {
      vi.mocked(fetchNotifications).mockRejectedValue(
        new Error("net-down"),
      );
      const feed = useNotificationFeed();

      await expect(feed.loadNotifications()).rejects.toThrow("net-down");
      expect(feed.error.value).toBe("net-down");
      expect(feed.loading.value).toBe(false);
    });

    it("uses fallback when rejection is not an Error", async () => {
      vi.mocked(fetchNotifications).mockRejectedValue("plain");
      const feed = useNotificationFeed();

      await expect(feed.loadNotifications()).rejects.toBe("plain");
      expect(feed.error.value).toBe("Failed to load notifications");
      expect(feed.loading.value).toBe(false);
    });
  });

  describe("loadUnreadCount (intentionally NOT routed through withError)", () => {
    it("silently returns 0 on failure and leaves error null", async () => {
      vi.mocked(fetchUnreadCount).mockRejectedValue(new Error("oops"));
      const feed = useNotificationFeed();

      const result = await feed.loadUnreadCount();

      expect(result).toBe(0);
      expect(feed.error.value).toBeNull();
    });
  });
});