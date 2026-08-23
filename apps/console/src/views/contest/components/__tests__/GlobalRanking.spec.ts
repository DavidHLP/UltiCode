import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import GlobalRanking from "../GlobalRanking.vue";
import type { GlobalRankingEntry } from "@/types/contest";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
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
  it("renders rank numbers on all three podium blocks", () => {
    const wrapper = mount(GlobalRanking, { props: { rankings } });
    const rankNodes = wrapper.findAll('[data-testid="podium-rank-number"]');

    expect(rankNodes.map((node) => node.text())).toEqual(["2", "1", "3"]);
    expect(rankNodes.every((node) => node.classes().includes("opacity-20"))).toBe(
      true,
    );
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
    expect(realAvatarWrapper.findAll("img").map((node) => node.attributes("src"))).toEqual([
      "/avatars/user-2.png",
      "/avatars/user-1.png",
      "/avatars/user-3.png",
    ]);
  });
});
