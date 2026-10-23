import { shallowMount, flushPromises } from "@vue/test-utils";
import { beforeEach, expect, it, vi } from "vitest";
import UserProfileView from "../UserProfileView.vue";
import StatsCard from "@/components/dashboard/StatsCard.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import {
  fetchUserProfile,
  fetchProfileByUsername,
  fetchUserStats,
  fetchUserSkills,
} from "@/api/user";

vi.mock("vue-router", async (original) => ({
  ...(await original<typeof import("vue-router")>()),
  useRoute: () => ({ params: { id: "user-1" } }),
}));
vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("@/api/user", () => ({
  fetchUserProfile: vi.fn(),
  fetchProfileByUsername: vi.fn(),
  fetchUserStats: vi.fn(),
  fetchUserSkills: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchUserProfile).mockResolvedValue({
    id: "user-1",
    username: "existing-user",
    name: "Existing user",
    submissionCount: 0,
    globalRank: null,
  } as Awaited<ReturnType<typeof fetchUserProfile>>);
  vi.mocked(fetchProfileByUsername).mockResolvedValue({
    id: "user-1",
    username: "existing-user",
    name: "Existing user",
    submissionCount: 4,
    globalRank: 64,
    acceptanceRate: 75,
  } as Awaited<ReturnType<typeof fetchProfileByUsername>>);
  vi.mocked(fetchUserStats).mockResolvedValue({
    totalSolved: 1,
    streak: 0,
    heatmap: [],
    stats: {
      Easy: { count: 1, total: 2 },
      Medium: { count: 0, total: 2 },
      Hard: { count: 0, total: 2 },
    },
  });
  vi.mocked(fetchUserSkills).mockResolvedValue({ skills: [], totalSolved: 1 });
});

it("uses canonical profile statistics rather than basic-user defaults or solved/submission ratio", async () => {
  const wrapper = shallowMount(UserProfileView);
  try {
    await flushPromises();
    expect(fetchProfileByUsername).toHaveBeenCalledWith("existing-user");
    const cards = wrapper.findAllComponents(StatsCard);
    const card = (title: string) =>
      cards.find((item) => item.props("title") === title)!;
    expect(card("personal.stats.submissions").props("value")).toBe(4);
    expect(card("profile.stats.globalRank").props("value")).toBe(64);
    expect(card("profile.stats.acceptanceRate").props("value")).toBe("75%");
  } finally {
    wrapper.unmount();
  }
});

it.each(["failure", "different-user"])(
  "does not show zero-success statistics on canonical %s",
  async (mode) => {
    if (mode === "failure") {
      vi.mocked(fetchProfileByUsername).mockRejectedValue(
        new Error("Profile unavailable"),
      );
    } else {
      vi.mocked(fetchProfileByUsername).mockResolvedValue({
        id: "other-user",
        username: "existing-user",
      } as Awaited<ReturnType<typeof fetchProfileByUsername>>);
    }
    const wrapper = shallowMount(UserProfileView);
    try {
      await flushPromises();
      expect(wrapper.findAllComponents(StatsCard)).toHaveLength(0);
      expect(wrapper.text()).toContain(
        mode === "failure"
          ? "Profile unavailable"
          : "personal.social.loadError",
      );
    } finally {
      wrapper.unmount();
    }
  },
);

it("retries the complete profile read after failure without rendering stale or zero statistics", async () => {
  vi.mocked(fetchProfileByUsername).mockRejectedValueOnce(
    new Error("Network Error"),
  );
  const wrapper = shallowMount(UserProfileView);
  try {
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("personal.social.loadError");
    expect(wrapper.findAllComponents(StatsCard)).toHaveLength(0);
    wrapper.getComponent(RetryButton).vm.$emit("retry");
    await flushPromises();
    expect(fetchUserProfile).toHaveBeenCalledTimes(2);
    expect(fetchUserStats).toHaveBeenCalledTimes(2);
    expect(fetchUserSkills).toHaveBeenCalledTimes(2);
    expect(fetchProfileByUsername).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.findComponent(RetryButton).exists()).toBe(false);
    expect(wrapper.findAllComponents(StatsCard)).toHaveLength(4);
  } finally {
    wrapper.unmount();
  }
});
