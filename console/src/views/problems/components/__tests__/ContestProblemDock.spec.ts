import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { computed, ref } from "vue";
import { createPinia, setActivePinia } from "pinia";
import ContestProblemDock from "../ContestProblemDock.vue";
import {
  ContestProblemContextKey,
  ToggleNotesKey,
} from "../../problem-context";
import { useAuthStore } from "@/stores/auth";
import type {
  ContestDetail,
  ContestProblemSummary,
  ParticipationStatus,
} from "@/types/contest";
import type { SubmissionRecord } from "@/types/submission";

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  windowAssign: vi.fn(),
  fetchContestProblemSubmissions: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({
    params: { slug: "reverse-linked-list", tab: "description" },
    query: { contestId: "linked-list-special" },
  }),
  useRouter: () => ({ push: mocks.routerPush }),
}));

vi.mock("vue-i18n", () => ({
  createI18n: () => ({
    global: {
      t: (key: string) => key,
    },
  }),
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("@/api/contest", () => ({
  fetchContestProblemSubmissions: mocks.fetchContestProblemSubmissions,
  getAnnouncements: vi.fn(async () => []),
}));

vi.mock("vue-sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
}));

const stubs = {
  Popover: { template: "<div><slot /></div>" },
  PopoverTrigger: { template: "<div><slot /></div>" },
  PopoverContent: {
    template: '<section data-testid="dock-content"><slot /></section>',
  },
  Button: {
    props: ["disabled"],
    template: '<button :disabled="disabled"><slot /></button>',
  },
  ContestStatusBadge: {
    props: ["status"],
    template: '<span data-testid="status-badge">{{ status }}</span>',
  },
  ContestTimer: {
    props: ["targetTime"],
    template: '<span data-testid="contest-timer">{{ targetTime }}</span>',
  },
  ContestAnnouncementBell: {
    template: '<button data-testid="announcement-bell">bell</button>',
  },
};

function makeContest(status = "FINISHED"): ContestDetail {
  return {
    id: "contest-1",
    slug: "linked-list-special",
    title: "链表专题赛",
    status,
    startTime: "2026-06-01T00:00:00.000Z",
    endTime: status === "RUNNING" ? "2099-01-01T00:00:00.000Z" : null,
    duration: 7200,
    contestType: "ICPC",
    participantCount: 1,
    problemCount: 3,
    isPremium: false,
    isPublished: true,
    isVisible: true,
    maxParticipants: 100,
    registeredCount: 1,
    isParticipating: true,
    userRanking: 3,
    isRated: false,
    scoringMode: "SCORE",
    penaltyPerWrong: 300,
    coverImage: "",
    description: "",
    isVirtual: false,
    submissionCount: 2,
    rules: "",
    registrationStart: "",
    registrationEnd: "",
    freezeTime: "",
    actualStartTime: "",
    actualEndTime: "",
    tieBreaker: "NONE",
    scoringRuleId: "",
    createdAt: "",
    updatedAt: "",
    createdById: 1,
    createdByUsername: "admin",
    problemIds: [6, 7, 8],
    tags: [],
    userScore: 80,
  };
}

function makeProblem(
  problemId: number,
  problemIndex: string,
  slug: string,
): ContestProblemSummary {
  return {
    id: `cp-${problemIndex}`,
    contestId: "contest-1",
    problemId,
    problemIndex,
    score: 100,
    penaltyPerWrong: 300,
    title: `Problem ${problemIndex}`,
    slug,
    difficulty: "Easy",
    solvedCount: 0,
    submissionCount: 0,
    acceptanceRate: 0,
  };
}

function makeParticipation(): ParticipationStatus {
  return {
    contestId: "contest-1",
    title: "链表专题赛",
    status: "FINISHED",
    registeredAt: "",
    startedAt: "",
    completedAt: "",
    startTime: "",
    endTime: "",
    ranking: 3,
    score: 80,
    problemsSolved: 1,
    totalProblems: 3,
    hasStarted: true,
    isActive: false,
    isCompleted: true,
    canParticipate: true,
  };
}

function makeSubmission(status: SubmissionRecord["status"]): SubmissionRecord {
  return {
    id: `sub-${status}`,
    problem_id: 6,
    status,
    language: "cpp",
    runtime: 12,
    memory: 1024,
    created_at: "2026-06-01T00:17:42.000Z",
    contest_info: {
      time_from_start: 1062,
      problem_index: "A",
      score: 80,
      is_accepted: status === "Accepted",
    },
  };
}

function mountDock(status = "FINISHED") {
  const contest = ref(makeContest(status));
  const participation = ref(makeParticipation());
  const problems = ref([
    makeProblem(6, "A", "reverse-linked-list"),
    makeProblem(7, "B", "add-two-numbers"),
    makeProblem(8, "C", "merge-k-sorted-lists"),
  ]);
  const contestProblemNav = computed(() => ({
    prev: null,
    current: problems.value[0],
    next: problems.value[1],
  }));
  const refreshParticipation = vi.fn();
  const toggleNotes = vi.fn();
  // The dock delegates pill navigation to the injected context's
  // goToContestProblem (see useContestProblemContext), which in turn
  // calls useRouter().push. The vue-router mock below routes that push
  // to mocks.routerPush, so mirror the composable's contract here
  // rather than omitting it (otherwise the pill click throws and the
  // push is never observed).
  const goToContestProblem = (slug: string | null): void => {
    if (!slug) return;
    mocks.routerPush({
      name: "problem-detail",
      params: { slug },
      query: { contestId: contest.value?.slug ?? "" },
    });
  };

  const wrapper = mount(ContestProblemDock, {
    global: {
      stubs,
      provide: {
        [ContestProblemContextKey as unknown as symbol]: {
          contestId: ref("contest-1"),
          contest,
          participation,
          problems,
          contestProblemNav,
          isInContest: ref(true),
          problemBelongsToContest: ref(true),
          refreshParticipation,
          refreshProblems: vi.fn(),
          goToContestProblem,
        },
        [ToggleNotesKey as unknown as symbol]: toggleNotes,
      },
    },
  });

  return { wrapper, toggleNotes };
}

describe("ContestProblemDock", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    const authStore = useAuthStore();
    authStore.user = {
      id: "u1",
      username: "alice",
      name: "Alice",
      role: "USER",
    } as never;
    mocks.routerPush.mockReset();
    mocks.windowAssign.mockReset();
    mocks.fetchContestProblemSubmissions.mockReset();
    mocks.fetchContestProblemSubmissions.mockResolvedValue([
      makeSubmission("Wrong Answer"),
      makeSubmission("Accepted"),
    ]);
    Object.defineProperty(window, "location", {
      value: { assign: mocks.windowAssign },
      configurable: true,
    });
  });

  it("collects contest status, navigation, announcements, and post-game actions into one toolbar dock", async () => {
    const { wrapper, toggleNotes } = mountDock("FINISHED");
    await flushPromises();

    expect(
      wrapper.find('[data-testid="contest-problem-dock-trigger"]').exists(),
    ).toBe(true);
    expect(wrapper.text()).toContain("链表专题赛");
    expect(wrapper.text()).toContain("contest.detail.shell.score");
    expect(wrapper.text()).toContain("80");
    expect(wrapper.text()).toContain("contest.detail.shell.rank");
    expect(wrapper.text()).toContain("#3");
    expect(wrapper.text()).toContain("contest.detail.shell.solved");
    expect(wrapper.text()).toContain("1");
    expect(wrapper.text()).toContain("/ 3");
    expect(wrapper.find('[data-testid="announcement-bell"]').exists()).toBe(
      true,
    );
    expect(wrapper.find('[data-testid="dock-pill-A"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="dock-pill-C"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="contest-review-retake"]').exists()).toBe(
      true,
    );
    expect(
      wrapper.find('[data-testid="contest-review-notebook"]').exists(),
    ).toBe(true);
    expect(mocks.fetchContestProblemSubmissions).toHaveBeenCalledWith(
      "contest-1",
      6,
    );

    await wrapper.find('[data-testid="dock-pill-C"]').trigger("click");
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "merge-k-sorted-lists" },
      query: { contestId: "linked-list-special" },
    });
    mocks.routerPush.mockClear();

    await wrapper
      .find('[data-testid="contest-review-retake"]')
      .trigger("click");
    expect(mocks.windowAssign).not.toHaveBeenCalled();
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "reverse-linked-list", tab: "description" },
      query: { contestId: "linked-list-special" },
    });

    await wrapper
      .find('[data-testid="contest-review-notebook"]')
      .trigger("click");
    expect(toggleNotes).toHaveBeenCalled();
  });

  it("renders nothing outside contest mode", () => {
    const wrapper = mount(ContestProblemDock, {
      global: {
        stubs,
        provide: {
          [ContestProblemContextKey as unknown as symbol]: {
            contestId: ref(null),
            contest: ref(null),
            participation: ref(null),
            problems: ref([]),
            contestProblemNav: computed(() => ({
              prev: null,
              current: null,
              next: null,
            })),
            isInContest: ref(false),
            problemBelongsToContest: ref(null),
            refreshParticipation: vi.fn(),
            refreshProblems: vi.fn(),
          },
        },
      },
    });

    expect(
      wrapper.find('[data-testid="contest-problem-dock-trigger"]').exists(),
    ).toBe(false);
  });
});
