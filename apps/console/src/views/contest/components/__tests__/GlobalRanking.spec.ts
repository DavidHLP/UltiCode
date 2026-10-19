import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import GlobalRanking from "../GlobalRanking.vue";
import type { GlobalRankingEntry } from "@/types/contest";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string, fallback?: unknown) =>
      typeof fallback === "string" ? fallback : key,
  }),
}));

const rankings = [1, 2, 3].map((rank) => ({
  rank,
  userId: `user-${rank}`,
  username: `user-${rank}`,
  name: `User ${rank}`,
  avatar: null,
  country: null,
  rating: 1000 + rank,
  maxRating: 1000 + rank,
  ratingTitle: "PUPIL",
  maxRatingTitle: "PUPIL",
  contestsAttended: rank,
  badge: null,
})) as GlobalRankingEntry[];

describe("GlobalRanking", () => {
  it("renders appended rows and exposes pagination only while more results exist", async () => {
    const entries = Array.from({ length: 12 }, (_, index) => ({
      ...rankings[0]!,
      rank: index + 1,
      username: `user-${index + 1}`,
      name: `User ${index + 1}`,
    }));
    const wrapper = mount(GlobalRanking, {
      props: { rankings: entries, hasMore: true },
    });
    try {
      expect(wrapper.text()).toContain("User 12");
      await wrapper.get("button").trigger("click");
      expect(wrapper.emitted("loadMore")).toHaveLength(1);
      await wrapper.setProps({ loadingMore: true });
      expect(wrapper.get("button").attributes("disabled")).toBeDefined();
      await wrapper.setProps({ loadingMore: false, loadMoreError: true });
      expect(wrapper.get('[role="alert"]').text()).toContain(
        "contest.error.rankingsLoadFailed",
      );
      expect(wrapper.text()).toContain("User 12");
      await wrapper.setProps({ hasMore: false, loadMoreError: false });
      expect(wrapper.find("button").exists()).toBe(false);
    } finally {
      wrapper.unmount();
    }
  });

  it("shows a localized empty state without a blank podium or load-more action", () => {
    const wrapper = mount(GlobalRanking, { props: { rankings: [] } });
    try {
      expect(wrapper.text()).toContain("contest.ranking.noRankings");
      expect(wrapper.find('[data-testid="podium-rank-number"]').exists()).toBe(
        false,
      );
      expect(wrapper.find("button").exists()).toBe(false);
    } finally {
      wrapper.unmount();
    }
  });

  it("keeps a failed request distinct from empty results and hides stale rows", async () => {
    const wrapper = mount(GlobalRanking, { props: { rankings, error: true } });
    try {
      expect(wrapper.get('[role="alert"]').text()).toContain(
        "contest.error.rankingsLoadFailed",
      );
      expect(wrapper.text()).not.toContain("contest.ranking.noRankings");
      expect(wrapper.find('[data-testid="podium-rank-number"]').exists()).toBe(
        false,
      );
      await wrapper.get("button").trigger("click");
      expect(wrapper.emitted("retry")).toHaveLength(1);
    } finally {
      wrapper.unmount();
    }
  });

  it("labels the ranking scope, contest count and rating explicitly", async () => {
    const wrapper = mount(GlobalRanking, { props: { rankings } });
    expect(wrapper.text()).toContain("contest.rankings.global");
    expect(wrapper.text()).toContain("contest.ranking.contests");
    expect(wrapper.text()).toContain("contest.rating.title");
    await wrapper.setProps({ scope: "local" });
    expect(wrapper.text()).toContain("contest.rankings.local");
  });

  it("renders rank numbers on all three podium blocks", () => {
    const wrapper = mount(GlobalRanking, { props: { rankings } });
    const rankNodes = wrapper.findAll('[data-testid="podium-rank-number"]');

    expect(rankNodes.map((node) => node.text())).toEqual(["2", "1", "3"]);
    expect(
      rankNodes.every((node) => node.classes().includes("opacity-20")),
    ).toBe(true);
  });

  it("uses the real profile avatar when supplied", () => {
    const withAvatars = rankings.map((ranking) => ({
      ...ranking,
      avatar: `/avatars/${ranking.username}.png`,
    }));
    const wrapper = mount(GlobalRanking, { props: { rankings } });
    const realAvatarWrapper = mount(GlobalRanking, {
      props: { rankings: withAvatars },
    });

    expect(wrapper.findAll("img")).toHaveLength(0);
    expect(wrapper.text()).toContain("U1");
    expect(
      realAvatarWrapper.findAll("img").map((node) => node.attributes("src")),
    ).toEqual([
      "/avatars/user-2.png",
      "/avatars/user-1.png",
      "/avatars/user-3.png",
    ]);
  });

  it("handles Unicode astral-plane initials safely", () => {
    const astralRankings = [
      {
        ...rankings[0],
        name: "🚀 Rocket",
        avatar: null,
      },
    ];
    const wrapper = mount(GlobalRanking, {
      props: { rankings: astralRankings },
    });
    expect(wrapper.text()).toContain("🚀R");
  });
});
