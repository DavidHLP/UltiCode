import { shallowMount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import CategoriesTab from "../CategoriesTab.vue";

vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));

describe("CategoriesTab", () => {
  it("explains an empty category collection", async () => {
    const wrapper = shallowMount(CategoriesTab, {
      props: { categories: [], allCategories: [] },
    });
    try {
      expect(wrapper.text()).toContain(
        "personal.problemLists.emptyStates.noCategories",
      );
      await wrapper.setProps({
        categories: [
          { id: "category", name: "Practice", sortOrder: 0, lists: [] },
        ],
      });
      expect(wrapper.find('[role="status"]').exists()).toBe(false);
      expect(wrapper.findComponent({ name: "Card" }).exists()).toBe(true);
    } finally {
      wrapper.unmount();
    }
  });
});
