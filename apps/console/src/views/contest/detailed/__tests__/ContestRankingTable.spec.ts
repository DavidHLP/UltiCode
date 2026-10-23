import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import type { ContestRankingEntry } from "@/types/contest";
import ContestRankingTable from "../components/ContestRankingTable.vue";

vi.mock("vue-i18n", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));

function row(rank: number): ContestRankingEntry {
  return {
    rank,
    userId: String(rank),
    username: `user-${rank}`,
    avatar: null,
    score: 0,
    penalty: 0,
    problemsSolved: 0,
    finishTime: null,
    isCurrentUser: false,
    progress: null,
    percentile: null,
    isParticipating: true,
    country: "CN",
    maxRating: 0,
    ratingTitle: "NEWBIE",
    maxRatingTitle: "NEWBIE",
    contestsAttended: 1,
    badge: null,
  };
}

function table() {
  return mount(ContestRankingTable, {
    props: {
      rankings: [row(1)],
      getCountryFlag: () => "CN",
      page: 1,
      totalPages: 2,
      loading: false,
      error: false,
      paginated: true,
    },
  });
}

describe("ContestRankingTable", () => {
  it("uses page controls and keeps every supplied row", async () => {
    const wrapper = table();
    expect(
      wrapper
        .get('[aria-label="common.pagination.previousPage"]')
        .attributes("disabled"),
    ).toBeDefined();
    await wrapper
      .get('[aria-label="common.pagination.nextPage"]')
      .trigger("click");
    expect(wrapper.emitted("pageChange")).toEqual([[2]]);
    await wrapper.setProps({
      page: 2,
      rankings: Array.from({ length: 21 }, (_, i) => row(i + 21)),
    });
    expect(wrapper.findAll("tbody tr")).toHaveLength(21);
    expect(
      wrapper
        .get('[aria-label="common.pagination.nextPage"]')
        .attributes("disabled"),
    ).toBeDefined();
    await wrapper.setProps({ loading: true });
    expect(
      wrapper
        .get('[aria-label="common.pagination.previousPage"]')
        .attributes("disabled"),
    ).toBeDefined();
  });

  it("distinguishes loading, empty and recoverable error states", async () => {
    const wrapper = table();
    await wrapper.setProps({ rankings: [], loading: true });
    expect(wrapper.text()).toContain("contest.loading.rankings");
    expect(wrapper.text()).not.toContain("contest.empty.rankings");
    await wrapper.setProps({ loading: false });
    expect(wrapper.text()).toContain("contest.empty.rankings");
    await wrapper.setProps({ error: true });
    expect(wrapper.get("[role=alert]").text()).toContain(
      "contest.error.rankingsLoadFailed",
    );
    await wrapper.get("[role=alert] button").trigger("click");
    expect(wrapper.emitted("retry")).toHaveLength(1);
    await wrapper.setProps({ paginated: false, rankings: [row(1)] });
    expect(
      wrapper.find('[aria-label="common.pagination.nextPage"]').exists(),
    ).toBe(false);
  });
});
