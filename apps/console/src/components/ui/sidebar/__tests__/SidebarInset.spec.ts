import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import SidebarInset from "../SidebarInset.vue";

describe("SidebarInset", () => {
  it("can shrink beside the desktop sidebar", () => {
    const wrapper = mount(SidebarInset);

    expect(wrapper.classes()).toContain("min-w-0");
  });
});
