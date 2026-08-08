import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import DataTableToolbar from "../DataTableToolbar.vue";

describe("DataTableToolbar", () => {
  it("keeps controls stacked until the large breakpoint", () => {
    const wrapper = mount(DataTableToolbar, {
      props: {
        modelValue: "",
      },
    });

    expect(wrapper.classes()).toContain("lg:flex-row");
  });
});
