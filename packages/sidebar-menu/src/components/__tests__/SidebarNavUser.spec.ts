import { mount, flushPromises } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import SidebarNavUser from "../SidebarNavUser.vue";

describe("SidebarNavUser", () => {
  it("renders name, email and role", () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: "Alice", email: "a@b.c", role: "ADMIN" } },
    });
    expect(wrapper.text()).toContain("Alice");
    expect(wrapper.text()).toContain("a@b.c");
    expect(wrapper.text()).toContain("ADMIN");
  });

  it("shows initials fallback when no avatar", () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: "Bob" } },
    });
    expect(wrapper.text()).toContain("B");
    expect(wrapper.find("img").exists()).toBe(false);
  });

  it("renders avatar img when provided", () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: "C", avatar: "https://x/y.png" } },
    });
    expect(wrapper.find("img").attributes("src")).toBe("https://x/y.png");
  });

  it("falls back to initials when the avatar fails to load (@error)", async () => {
    const image = document.createElement("img");
    const imageConstructor = vi
      .spyOn(window, "Image")
      .mockImplementation(function () {
        return image;
      });
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: "Dan", avatar: "https://broken/x.png" } },
    });
    await flushPromises();
    image.dispatchEvent(new Event("load"));
    await flushPromises();
    expect(wrapper.find("img").exists()).toBe(true);
    image.dispatchEvent(new Event("error"));
    await flushPromises();
    expect(wrapper.get("img").isVisible()).toBe(false);
    expect(wrapper.get('[data-slot="avatar-fallback"]').text()).toBe("D");
    wrapper.unmount();
    imageConstructor.mockRestore();
  });

  it("handles an empty name without crashing", () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: "" } },
    });
    expect(wrapper.find("img").exists()).toBe(false);
    // name.charAt(0) is '' → initials span renders empty; no throw.
    expect(wrapper.find("div.min-w-0").exists()).toBe(true);
  });

  it("renders #menu slot", () => {
    const wrapper = mount(SidebarNavUser, {
      props: { user: { name: "A" } },
      slots: { menu: "<button>menu</button>" },
    });
    expect(wrapper.text()).toContain("menu");
  });
});
