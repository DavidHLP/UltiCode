import { describe, expect, it, vi } from "vitest";
import { computed, ref, nextTick } from "vue";
import { shallowMount } from "@vue/test-utils";
import ProblemListsView from "../ProblemListsView.vue";
import { useProblemLists } from "../composables/useProblemLists";

vi.mock("../composables/useProblemLists", () => ({ useProblemLists: vi.fn() }));
vi.mock("vue-router", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));

describe("personal featured lists search", () => {
  it("matches names and descriptions, exposes no matches, and restores the full list", async () => {
    const searchQuery = ref("");
    const featuredLists = [
      {
        id: "array",
        name: "Array Essentials",
        description: "Hash tables",
        problemCount: 4,
      },
      {
        id: "sql",
        name: "SQL Basics",
        description: "Database queries",
        problemCount: 2,
      },
      { id: "graph", name: "Graph", problemCount: 3 },
    ];
    const state = {
      loading: ref(false),
      loadError: ref(false),
      currentUserId: "test-user",
      searchQuery,
      data: ref({
        ownLists: [],
        savedLists: [],
        categories: [],
        featuredLists,
      }),
      sortedOwnLists: computed(() => []),
      sortedSavedLists: computed(() => []),
      totalSavedCount: computed(() => 0),
      sortedCategories: computed(() => []),
      loadData: vi.fn(),
      handleCreateList: vi.fn(),
      handleDeleteList: vi.fn(),
      handleUnsaveList: vi.fn(),
      handleSaveList: vi.fn(),
      handleMoveToCategory: vi.fn(),
      handleCreateCategory: vi.fn(),
      handleEditCategory: vi.fn(),
      handleDeleteCategory: vi.fn(),
    };
    vi.mocked(useProblemLists).mockReturnValue(state);
    const wrapper = shallowMount(ProblemListsView, {
      global: {
        renderStubDefaultSlot: true,
        stubs: { RouterLink: { template: "<a><slot /></a>" } },
      },
    });
    const titles = () => wrapper.findAll("h3").map((heading) => heading.text());
    expect(titles()).toEqual(["Array Essentials", "SQL Basics", "Graph"]);
    searchQuery.value = " ARRAY ";
    await nextTick();
    expect(titles()).toEqual(["Array Essentials"]);
    searchQuery.value = "DATABASE";
    await nextTick();
    expect(titles()).toEqual(["SQL Basics"]);
    searchQuery.value = "no match";
    await nextTick();
    expect(titles()).toEqual([]);
    expect(wrapper.text()).toContain("common.search.noResults");
    searchQuery.value = "";
    await nextTick();
    expect(titles()).toHaveLength(3);
    expect(state.data.value.featuredLists).toHaveLength(3);
    expect(state.handleSaveList).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
