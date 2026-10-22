import { flushPromises, mount, shallowMount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import CommentForm from "../CommentForm.vue";
import CommentNode from "../CommentNode.vue";
import CommentThread from "../CommentThread.vue";

vi.mock("vue-i18n", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: () => null }),
}));

const stubs = {
  MarkdownView: true,
  ReportDialog: true,
  CommentRail: true,
  CommentConnector: true,
  CommentNode: true,
};

describe("comment layout", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        disconnect() {}
      },
    );
  });
  afterEach(() => vi.unstubAllGlobals());
  it("keeps the entry label wrapping and opens a cancellable form", async () => {
    const wrapper = mount(CommentThread, { props: { comments: [] } });
    expect(wrapper.classes()).not.toContain("px-4");
    const entry = wrapper.get("button");
    expect(entry.classes()).toContain("whitespace-normal");
    expect(entry.classes()).toContain("h-auto");
    await entry.trigger("click");
    expect(wrapper.find("textarea").exists()).toBe(true);
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "common.actions.cancel")!
      .trigger("click");
    expect(wrapper.find("textarea").exists()).toBe(false);
    expect(wrapper.emitted("submit")).toBeUndefined();
  });

  it.each(["forum", "solution"] as const)(
    "restores focus to the %s entry when cancelling without submitting",
    async (commentType) => {
      const wrapper = mount(CommentThread, {
        props: { comments: [], commentType },
        attachTo: document.body,
      });
      try {
        await wrapper.get("button").trigger("click");
        await flushPromises();
        expect(document.activeElement).toBe(wrapper.get("textarea").element);
        const cancel = wrapper
          .findAll("button")
          .find((button) => button.text() === "common.actions.cancel")!;
        cancel.element.focus();
        await cancel.trigger("click");
        expect(document.activeElement).toBe(wrapper.get("button").element);
        expect(wrapper.find("textarea").exists()).toBe(false);
        expect(wrapper.emitted("submit")).toBeUndefined();
      } finally {
        wrapper.unmount();
      }
    },
  );

  it("wraps form actions while preserving parent ID and trimmed submission guard", async () => {
    const wrapper = mount(CommentForm, {
      props: { parentId: "parent", onCancel: vi.fn() },
    });
    expect(wrapper.get("form > div > div").classes()).toContain("flex-wrap");
    await wrapper.get("textarea").setValue("  ");
    await wrapper.get("form").trigger("submit");
    expect(wrapper.emitted("submit")).toBeUndefined();
    await wrapper.get("textarea").setValue("reply");
    await wrapper.get("form").trigger("submit");
    expect(wrapper.emitted("submit")).toEqual([["reply", "parent"]]);
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe(
      "",
    );
  });

  it("restores focus to the reply action after cancelling a reply", async () => {
    const wrapper = mount(CommentNode, {
      props: {
        comment: {
          id: "root",
          author: "root",
          content: "body",
          time: "now",
        },
      },
      attachTo: document.body,
      global: {
        stubs: {
          MarkdownView: true,
          ReportDialog: true,
          CommentRail: true,
          CommentConnector: true,
        },
      },
    });
    try {
      const reply = wrapper.get('button[aria-label="common.actions.reply"]');
      await reply.trigger("click");
      await flushPromises();
      expect(document.activeElement).toBe(wrapper.get("textarea").element);
      const cancel = wrapper
        .findAll("button")
        .find((button) => button.text() === "common.actions.cancel")!;
      cancel.element.focus();
      await cancel.trigger("click");
      await flushPromises();
      expect(wrapper.find("textarea").exists()).toBe(false);
      expect(document.activeElement).toBe(reply.element);
    } finally {
      wrapper.unmount();
    }
  });

  it("lets replies span the whole narrow node instead of recursively consuming the text column", async () => {
    const child = {
      id: "child",
      author: "child",
      content: "reply",
      time: "now",
    };
    const wrapper = shallowMount(CommentNode, {
      props: {
        comment: {
          id: "root",
          author: "root",
          content: "body",
          time: "now",
          children: [child],
        },
      },
      global: { stubs },
    });
    const children = wrapper.get(".comment-node > .col-span-2");
    expect(children.classes()).toContain("@min-[24rem]/comment:col-start-2");
    expect(wrapper.get(".min-h-8").classes()).toContain("flex-wrap");
    const reply = wrapper.findAllComponents({ name: "ActionItem" })[0];
    await reply.vm.$emit("click");
    const form = wrapper.getComponent(CommentForm);
    await form.vm.$emit("submit", "nested reply");
    expect(wrapper.emitted("reply")).toEqual([["root", "nested reply"]]);
    await wrapper.get("span.font-bold").trigger("click");
    expect(wrapper.find(".comment-node > .col-span-2").exists()).toBe(false);
  });
});
