import { flushPromises, mount, shallowMount } from "@vue/test-utils";
import { ref } from "vue";
import { expect, it, vi } from "vitest";
import ForumThreadView from "./ForumThreadView.vue";

const scrollToHeading = vi.hoisted(() => vi.fn());

vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key, locale: ref("en-US") }),
}));
vi.mock("@/composables/useForumThread", () => ({
  useForumThread: () => ({
    thread: ref({
      id: "thread-1",
      title: "Thread",
      createdAt: "2026-01-01T00:00:00Z",
      body: "## First section\n\nBody",
      comments: [],
      stats: { comments: 0 },
    }),
    isLoading: ref(false),
    isOwner: () => false,
    countThreadComments: () => 18,
  }),
}));
vi.mock("@/composables/useContentNavigation", () => ({
  useContentNavigation: () => ({
    wrapperRef: ref(null),
    activeHeadingId: ref(""),
    isWideLayout: ref(false),
    showMobileTOC: ref(false),
    scrollToHeading,
  }),
}));

it("uses the loaded comment count for both the action bar and section heading", () => {
  const view = shallowMount(ForumThreadView, {
    global: {
      renderStubDefaultSlot: true,
      stubs: { RouterLink: true, ThreadContent: false },
    },
  });
  try {
    expect(
      view.findComponent({ name: "ThreadContent" }).props("commentCount"),
    ).toBe(18);
    expect(
      view.findComponent({ name: "PostActions" }).props("config").comments
        .count,
    ).toBe("18");
    expect(view.find("#comments-section").text()).toContain("(18)");
    expect(view.text()).toContain("forum.actions.report");
    expect(view.findComponent({ name: "CommentThread" }).classes()).toContain(
      "px-4",
    );
  } finally {
    view.unmount();
  }
});

it("portals the narrow layout outline outside the clipped card and closes after navigation", async () => {
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  const view = mount(ForumThreadView, {
    attachTo: document.body,
    global: {
      stubs: {
        RouterLink: { template: "<a><slot /></a>" },
        ThreadContent: true,
        CommentThread: true,
        ReportDialog: true,
      },
    },
  });
  try {
    const trigger = view
      .findAll("button")
      .find((b) => b.text() === "forum.sidebar.toc")!;
    await trigger.trigger("click");
    await flushPromises();
    const outline = document.querySelector('[data-slot="popover-content"]');
    expect(outline).not.toBeNull();
    expect(view.element.contains(outline)).toBe(false);
    const focus = vi.spyOn(trigger.element, "focus");
    const heading = Array.from(outline!.querySelectorAll("button")).find(
      (button) => button.textContent?.trim() === "First section",
    )!;
    heading.click();
    await flushPromises();
    expect(scrollToHeading).toHaveBeenCalledWith("first-section");
    expect(document.querySelector('[data-slot="popover-content"]')).toBeNull();
    expect(focus).toHaveBeenCalledWith({ preventScroll: true });
    await trigger.trigger("click");
    await flushPromises();
    const outside = view
      .findAll("button")
      .find((b) => b.text() === "forum.actions.report")!;
    outside.element.focus();
    await flushPromises();
    expect(document.querySelector('[data-slot="popover-content"]')).toBeNull();
    expect(document.activeElement).toBe(outside.element);
  } finally {
    view.unmount();
    vi.unstubAllGlobals();
  }
});
