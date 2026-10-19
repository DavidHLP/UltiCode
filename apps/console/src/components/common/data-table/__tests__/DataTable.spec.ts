import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import DataTable from "../DataTable.vue";

describe("DataTable row navigation", () => {
  it.each([false, true])(
    "keeps custom empty content outside wide table columns (virtualized=%s)",
    (virtualized) => {
      const wrapper = mount(DataTable, {
        props: {
          data: [],
          columns: [{ key: "title", header: "Title" }],
          virtualized,
        },
        slots: { empty: "<h3>No matching submissions</h3>" },
      });
      try {
        expect(wrapper.get("h3").element.closest("table")).toBeNull();
      } finally {
        wrapper.unmount();
      }
    },
  );

  it.each([false, true])(
    "allows empty-state text to wrap (virtualized=%s)",
    (virtualized) => {
      const wrapper = mount(DataTable, {
        props: {
          data: [],
          columns: [{ key: "title", header: "Title" }],
          emptyDescription: "Try adjusting your filters or search query.",
          virtualized,
        },
      });
      try {
        expect(wrapper.find("table").exists()).toBe(false);
        expect(wrapper.text()).toContain("No results found");
        expect(wrapper.text()).toContain(
          "Try adjusting your filters or search query.",
        );
        expect(wrapper.classes()).toContain("[overflow-wrap:anywhere]");
      } finally {
        wrapper.unmount();
      }
    },
  );

  it("keeps the table overflow container keyboard focusable", () => {
    const wrapper = mount(DataTable, {
      props: {
        data: [{ id: 1, title: "Problem" }],
        columns: [{ key: "title", header: "Title" }],
      },
    });
    try {
      const container = wrapper.get('[data-slot="table-container"]');
      expect(container.attributes("tabindex")).toBe("0");
      expect(container.classes()).toContain("focus-visible:ring-2");

      const scrollContainer = container.element as HTMLDivElement;
      scrollContainer.scrollLeft = 100;
      const rightKey = new KeyboardEvent("keydown", {
        key: "ArrowRight",
        bubbles: true,
        cancelable: true,
      });
      scrollContainer.dispatchEvent(rightKey);
      expect(rightKey.defaultPrevented).toBe(true);
      expect(scrollContainer.scrollLeft).toBe(140);

      const rowKey = new KeyboardEvent("keydown", {
        key: "ArrowRight",
        bubbles: true,
        cancelable: true,
      });
      wrapper.get("tbody tr").element.dispatchEvent(rowKey);
      expect(rowKey.defaultPrevented).toBe(false);
      expect(scrollContainer.scrollLeft).toBe(140);

      const leftKey = new KeyboardEvent("keydown", {
        key: "ArrowLeft",
        bubbles: true,
        cancelable: true,
      });
      scrollContainer.dispatchEvent(leftKey);
      expect(scrollContainer.scrollLeft).toBe(100);
    } finally {
      wrapper.unmount();
    }
  });

  it("keeps rows keyboard reachable and does not navigate for child key events", async () => {
    const item = { id: 1, title: "Problem" };
    const wrapper = mount(DataTable, {
      props: { data: [item], columns: [{ key: "title", header: "Title" }] },
      slots: { "cell-title": '<button type="button">Child action</button>' },
    });
    const row = wrapper.get("tbody tr");
    expect(row.attributes("tabindex")).toBe("0");
    await row.get("button").trigger("keydown", { key: "Enter" });
    expect(wrapper.emitted("row-click")).toBeUndefined();
    await row.trigger("keydown", { key: "Enter" });
    expect(wrapper.emitted("row-click")).toEqual([[item]]);
  });
});
