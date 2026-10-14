import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import DataTable from "../DataTable.vue";

describe("DataTable row navigation", () => {
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
