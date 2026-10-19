import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import SidebarMenuItem from "../SidebarMenuItem.vue";

describe("SidebarMenuItem", () => {
  it('reflects isActive via data-active="true" on the interactive item', () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { isActive: true, as: "a" },
    });
    expect(
      wrapper.get('[data-sidebar="menu-button"]').attributes("data-active"),
    ).toBe("true");
    expect(wrapper.attributes("data-slot")).toBe("sidebar-menu-item");
  });

  it("omits data-active when inactive", () => {
    const wrapper = mount(SidebarMenuItem, { props: { as: "a" } });
    expect(
      wrapper.get('[data-sidebar="menu-button"]').attributes("data-active"),
    ).toBeUndefined();
  });

  it("renders as a router-link (stubbed <a>) by default and forwards :to + data-active", () => {
    // `as` defaults to 'link' → <component :is="'router-link'">; RouterLink is
    // globally stubbed to <a> in __tests__/setup.ts, so the production path
    // (console passes :to) is now covered.
    const wrapper = mount(SidebarMenuItem, {
      props: { isActive: true },
      attrs: { to: "/home" },
    });
    const link = wrapper.find("a");
    expect(link.exists()).toBe(true);
    expect(link.attributes("to")).toBe("/home");
    expect(link.attributes("data-active")).toBe("true");
  });

  it("renders a <button> element when as=button", () => {
    const wrapper = mount(SidebarMenuItem, { props: { as: "button" } });
    expect(wrapper.get('[data-sidebar="menu-button"]').element.tagName).toBe(
      "BUTTON",
    );
  });

  it("renders the badge when provided", () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { badge: 3, as: "a" },
    });
    expect(wrapper.text()).toContain("3");
  });

  it("renders badge=0 (falsy but valid count — not swallowed by a truthy-only guard)", () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { badge: 0, as: "a" },
    });
    expect(wrapper.text()).toContain("0");
  });

  it("omits the badge when undefined", () => {
    const wrapper = mount(SidebarMenuItem, { props: { as: "a" } });
    expect(wrapper.text()).toBe("");
  });

  it("emits toggle when the chevron is clicked (showChevron)", async () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { showChevron: true, as: "a" },
    });
    await wrapper.find('[aria-label="toggle section"]').trigger("click");
    expect(wrapper.emitted("toggle")).toBeTruthy();
  });

  it("renders default slot content", () => {
    const wrapper = mount(SidebarMenuItem, {
      props: { as: "a" },
      slots: { default: "Home" },
    });
    expect(wrapper.text()).toContain("Home");
  });
});
