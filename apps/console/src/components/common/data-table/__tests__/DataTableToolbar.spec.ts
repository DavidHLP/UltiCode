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
    expect(wrapper.find("button").exists()).toBe(false);
    wrapper.unmount();
  });

  it("renders the filter action as an accessible icon-only button", () => {
    const wrapper = mount(DataTableToolbar, {
      props: {
        modelValue: "",
        filterLabel: "Filter",
        filterIconOnly: true,
      },
      slots: { filters: "Available filters" },
    });

    const filterButton = wrapper.get('button[aria-label="Filter"]');
    expect(filterButton.attributes("title")).toBe("Filter");
    expect(filterButton.text()).toBe("");
    wrapper.unmount();
  });
});
