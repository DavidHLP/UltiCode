import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import SidebarMenuSubItem from "../SidebarMenuSubItem.vue";

describe("SidebarMenuSubItem", () => {
  it("reflects isActive via data-active on the official menu sub-button", () => {
    const wrapper = mount(SidebarMenuSubItem, { props: { isActive: true } });
    expect(wrapper.attributes("data-active")).toBe("true");
    expect(wrapper.attributes("data-sidebar")).toBe("menu-sub-button");
  });

  it("omits data-active when inactive", () => {
    const wrapper = mount(SidebarMenuSubItem);
    expect(wrapper.attributes("data-active")).toBeUndefined();
  });

  it("renders a router-link (stubbed <a>) when :to is provided", () => {
    // Production path: console/management pass :to → router-link branch.
    const wrapper = mount(SidebarMenuSubItem, {
      props: { isActive: true },
      attrs: { to: "/sub" },
    });
    const link = wrapper.find("a");
    expect(link.exists()).toBe(true);
    expect(link.attributes("to")).toBe("/sub");
    expect(link.attributes("data-active")).toBe("true");
  });

  it("renders a plain <a> with no `to` attr when :to is absent", () => {
    const wrapper = mount(SidebarMenuSubItem);
    expect(wrapper.find("a").exists()).toBe(true);
    expect(wrapper.find("a").attributes("to")).toBeUndefined();
  });

  it("exposes the size via data-size", () => {
    const wrapper = mount(SidebarMenuSubItem, { props: { size: "sm" } });
    expect(wrapper.attributes("data-size")).toBe("sm");
  });

  it("renders the badge when provided", () => {
    const wrapper = mount(SidebarMenuSubItem, {
      props: { badge: "new" },
      slots: { default: "Child" },
    });
    expect(wrapper.text()).toContain("new");
    expect(wrapper.text()).toContain("Child");
  });
});
