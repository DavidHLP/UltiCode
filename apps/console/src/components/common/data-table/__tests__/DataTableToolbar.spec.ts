import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import DataTableToolbar from "../DataTableToolbar.vue";

describe("DataTableToolbar", () => {
  it("lets search grow and controls wrap to the available width", () => {
    const wrapper = mount(DataTableToolbar, {
      props: {
        modelValue: "",
      },
    });

    expect(wrapper.classes()).toContain("flex-wrap");
    expect(wrapper.get(".relative").classes()).toContain("flex-1");
    expect(wrapper.get(".relative").classes()).toContain("basis-64");
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
