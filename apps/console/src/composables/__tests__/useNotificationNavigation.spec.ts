import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import {
  useNotificationNavigation,
  resolveNotificationTarget,
} from "../useNotificationNavigation";
import { updateNotificationRead } from "@/api/notification";
import type { NotificationItem } from "@/types/notification";

vi.mock("@/api/notification", () => ({
  updateNotificationRead: vi.fn(),
}));

const mockRouter = { push: vi.fn() };
vi.mock("vue-router", () => ({
  useRouter: () => mockRouter,
}));

function item(partial: Partial<NotificationItem>): NotificationItem {
  return {
    id: "n-1",
    type: "SYSTEM",
    title: "",
    body: "",
    isRead: false,
    createdAt: "2026-07-17T00:00:00Z",
    link: null,
    ...partial,
  } as NotificationItem;
}

describe("resolveNotificationTarget — safe link classification", () => {
  it("classifies explicit http(s) URLs as external", () => {
    expect(resolveNotificationTarget("https://example.com/path")).toEqual({
      kind: "external",
      href: "https://example.com/path",
    });
    expect(resolveNotificationTarget("http://example.com")).toEqual({
      kind: "external",
      href: "http://example.com",
    });
  });

  it("classifies single-leading-slash app paths as internal", () => {
    expect(resolveNotificationTarget("/personal/notifications")).toEqual({
      kind: "internal",
      to: "/personal/notifications",
    });
    expect(resolveNotificationTarget("/problems/123")).toEqual({
      kind: "internal",
      to: "/problems/123",
    });
  });

  it.each([
    ["javascript:alert(1)"],
    ["javascript://%0aalert(1)"],
    ["data:text/html,<script>alert(1)</script>"],
    ["vbscript:msgbox"],
    ["//evil.example.com"], // protocol-relative
    ["mailto:spam@example.com"],
    ["relative-path"],
    ["example.com"],
    [""],
  ])("drops unsafe or non-route link %s to none", (link) => {
    expect(resolveNotificationTarget(link)).toEqual({ kind: "none" });
  });

  it("treats null and undefined as none", () => {
    expect(resolveNotificationTarget(null)).toEqual({ kind: "none" });
    expect(resolveNotificationTarget(undefined)).toEqual({ kind: "none" });
  });
});

describe("useNotificationNavigation.open", () => {
  let openSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    openSpy = vi.spyOn(window, "open").mockReturnValue(null);
  });

  afterEach(() => {
    openSpy.mockRestore();
  });

  it("marks the notification read before opening an external link", async () => {
    vi.mocked(updateNotificationRead).mockResolvedValue(item({ isRead: true }));
    const { open } = useNotificationNavigation();

    await open(item({ link: "https://example.com", isRead: false }));

    expect(updateNotificationRead).toHaveBeenCalledWith("n-1", true);
    expect(openSpy).toHaveBeenCalledWith(
      "https://example.com",
      "_blank",
      "noopener,noreferrer",
    );
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("pushes an internal app route onto the router", async () => {
    const { open } = useNotificationNavigation();

    await open(item({ link: "/problems/42", isRead: true }));

    expect(mockRouter.push).toHaveBeenCalledWith("/problems/42");
    expect(openSpy).not.toHaveBeenCalled();
    // already read → no markAsRead call
    expect(updateNotificationRead).not.toHaveBeenCalled();
  });

  it("does not navigate and does not call window.open for a javascript: link", async () => {
    const { open } = useNotificationNavigation();

    await open(item({ link: "javascript:alert(document.cookie)" }));

    expect(openSpy).not.toHaveBeenCalled();
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("does not navigate for a protocol-relative link", async () => {
    const { open } = useNotificationNavigation();

    await open(item({ link: "//evil.example.com" }));

    expect(openSpy).not.toHaveBeenCalled();
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("still proceeds when markAsRead fails", async () => {
    vi.mocked(updateNotificationRead).mockRejectedValue(new Error("boom"));
    const { open } = useNotificationNavigation();

    await expect(
      open(item({ link: "https://example.com", isRead: false })),
    ).resolves.toBeUndefined();
    expect(openSpy).toHaveBeenCalledWith(
      "https://example.com",
      "_blank",
      "noopener,noreferrer",
    );
  });
});
