import { mount } from "@vue/test-utils";
import { expect, it, vi } from "vitest";
import {
  ContestStatus,
  ContestType,
  type ContestListItem,
} from "@/types/contest";
import ContestCard from "../ContestCard.vue";

const push = vi.hoisted(() => vi.fn());
vi.mock("vue-router", () => ({ useRouter: () => ({ push }) }));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
vi.mock("@/utils/datetime", () => ({
  formatDateTime: () => "2026-09-03 12:13",
  getDurationMinutes: () => 90,
}));

it("labels registration and participation separately and retains detail navigation", async () => {
  const contest: ContestListItem = {
    id: "past-1",
    slug: "past-1",
    title: "Past contest",
    status: ContestStatus.FINISHED,
    startTime: "2026-09-03T12:13:00Z",
    endTime: null,
    duration: 90,
    contestType: ContestType.ICPC,
    participantCount: 0,
    registeredCount: 2,
    problemCount: 3,
    maxParticipants: 0,
    isPremium: false,
    isPublished: true,
    isVisible: true,
    isParticipating: false,
    userRanking: 0,
    isRated: false,
    scoringMode: "ICPC",
    penaltyPerWrong: 20,
    coverImage: "",
  };
  const wrapper = mount(ContestCard, {
    props: { contest },
    global: { stubs: { ContestStatusBadge: true, ContestTimer: true } },
  });

  expect(wrapper.text()).toContain("contest.detail.registered 2");
  expect(wrapper.text()).toContain("contest.detail.participants 0");
  expect(wrapper.text()).toContain("contest.types.unrated");
  await wrapper.get("button").trigger("click");
  expect(push).toHaveBeenCalledWith({
    name: "contest-detail",
    params: { slug: "past-1" },
  });
  await wrapper.setProps({ contest: { ...contest, isRated: true } });
  expect(wrapper.text()).toContain("contest.types.rated");
  wrapper.unmount();
});
