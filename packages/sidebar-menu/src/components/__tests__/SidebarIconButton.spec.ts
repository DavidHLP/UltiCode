import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import SidebarIconButton from "../SidebarIconButton.vue";

describe("SidebarIconButton", () => {
  it("renders an official button without hiding keyboard actions", () => {
    const wrapper = mount(SidebarIconButton, { props: { label: "edit" } });
    expect(wrapper.attributes("data-slot")).toBe("button");
    expect(wrapper.classes()).not.toContain("opacity-0");
  });

  it("sets the aria-label", () => {
    const wrapper = mount(SidebarIconButton, { props: { label: "delete" } });
    expect(wrapper.attributes("aria-label")).toBe("delete");
  });

  it("emits click on activation", async () => {
    const wrapper = mount(SidebarIconButton, { props: { label: "x" } });
    await wrapper.trigger("click");
    expect(wrapper.emitted("click")).toBeTruthy();
  });

  it("emits click with the MouseEvent payload", async () => {
    const wrapper = mount(SidebarIconButton, { props: { label: "x" } });
    await wrapper.trigger("click");
    const events = wrapper.emitted("click");
    expect(events).toBeTruthy();
    expect(events![0][0]).toBeInstanceOf(MouseEvent);
  });

  it("renders the default slot (icon)", () => {
    const wrapper = mount(SidebarIconButton, {
      props: { label: "x" },
      slots: { default: "<svg />" },
    });
    expect(wrapper.find("svg").exists()).toBe(true);
  });
});
