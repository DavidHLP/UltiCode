/**
 * Regression tests for LayoutHeaderLeft under contest mode.
 *
 * In contest mode the header must:
 *  - show "返回比赛" instead of the "题库" / problemset toggle
 *  - hide the random-problem button (it could land outside the contest)
 *  - use the contest's problem list (via ContestProblemContextKey) to
 *    drive prev/next — site-wide adjacency is NOT used
 *  - render the "本题不属于本场比赛" guard when the URL's contestId
 *    points to a contest that does not contain the current problem
 *
 * Outside contest mode the original behaviour is preserved.
 */
import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import { computed, ref } from "vue";
import LayoutHeaderLeft from "../LayoutHeaderLeft.vue";
import {
  ContestProblemContextKey,
  ProblemContextKey,
} from "../../problem-context";
import type { ContestProblemSummary, ContestDetail } from "@/types/contest";
import type { ProblemDetail } from "@/types/problem-detail";

// `fetchRandomProblem` is imported by the SFC. We replace it with a
// no-op so the test doesn't actually hit the network. The presence of
// a click handler in the rendered tree (when contest mode is off) is
// asserted separately by the source-string tests.
vi.mock("@/api/problem", () => ({
  fetchAdjacentProblems: vi.fn(async () => ({ prev: null, next: null })),
  fetchRandomProblem: vi.fn(),
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

const routerPushMock = vi.fn();
vi.mock("vue-router", () => ({
  useRouter: () => ({ push: routerPushMock }),
  RouterLink: { template: "<a><slot /></a>" },
}));

const stubs = {
  HoverCard: { template: "<div><slot /></div>" },
  HoverCardTrigger: { template: "<div><slot /></div>" },
  HoverCardContent: { template: "<div><slot /></div>" },
  Button: { template: "<button><slot /></button>" },
  Separator: { template: "<hr />" },
  Kbd: { template: "<kbd><slot /></kbd>" },
  KbdGroup: { template: "<div><slot /></div>" },
  // The not-in-contest guard component is small enough to render
  // directly; it just shows a banner with a button.
  ContestProblemNotInContest: {
    template: '<div data-testid="not-in-contest-guard">not-in-contest</div>',
  },
};

interface ProblemOverrides {
  id: number;
  slug: string;
}

const makeProblem = ({ id, slug }: ProblemOverrides): ProblemDetail =>
  ({
    id,
    slug,
    title: "Reverse Linked List",
    difficulty: "Easy",
    content: "",
    tags: [],
  } as unknown as ProblemDetail);

const makeContest = (slug: string, id = "contest-1"): ContestDetail =>
  ({
    id,
    slug,
    title: "Linked List Special",
    status: "RUNNING",
    startTime: "",
    endTime: "",
    duration: 0,
    contestType: "ICPC",
    participantCount: 0,
    problemCount: 0,
    isPremium: false,
    isPublished: true,
    isVisible: true,
    maxParticipants: 0,
    registeredCount: 0,
    isParticipating: false,
    userRanking: 0,
    isRated: false,
    scoringMode: "SCORE",
    penaltyPerWrong: 0,
    coverImage: "",
    description: "",
    isVirtual: false,
    submissionCount: 0,
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
    createdById: 0,
    createdByUsername: "",
    problemIds: [],
    tags: [],
    userScore: 0,
  } as unknown as ContestDetail);

const makeProblemSummary = (
  problemId: number,
  problemIndex: string,
  slug: string,
): ContestProblemSummary => ({
  id: `cp-${problemId}`,
  contestId: "contest-1",
  problemId,
  problemIndex,
  score: 100,
  penaltyPerWrong: 0,
  title: `Problem ${problemIndex}`,
  slug,
  difficulty: "Easy",
  solvedCount: 0,
  submissionCount: 0,
  acceptanceRate: 0,
});

interface MountOverrides {
  isInContest?: boolean;
  problemBelongsToContest?: boolean | null;
  contestSlug?: string;
  prevSlug?: string | null;
  nextSlug?: string | null;
}

function mountHeader(
  problemOverrides: ProblemOverrides,
  overrides: MountOverrides = {},
) {
  const {
    isInContest = false,
    problemBelongsToContest = true,
    contestSlug = "linked-list-special",
    prevSlug = null,
    nextSlug = null,
  } = overrides;

  const problemRef = ref<ProblemDetail | null>(makeProblem(problemOverrides));
  const contest = ref<ContestDetail | null>(
    isInContest ? makeContest(contestSlug) : null,
  );
  const participation = ref(null);
  const problems = ref<ContestProblemSummary[]>([]);
  const contestProblemNav = computed(() => ({
    prev: prevSlug ? makeProblemSummary(100, "A", prevSlug) : null,
    next: nextSlug ? makeProblemSummary(102, "C", nextSlug) : null,
    current: makeProblemSummary(101, "B", problemOverrides.slug),
  }));
  const ctxValue = {
    contestId: ref(contestSlug),
    contest,
    participation,
    problems,
    contestProblemNav,
    isInContest: ref(isInContest),
    problemBelongsToContest: ref(problemBelongsToContest),
    refreshParticipation: vi.fn(),
    refreshProblems: vi.fn(),
  };

  return mount(LayoutHeaderLeft, {
    props: {
      // The component has no explicit props; ProblemContextKey is what
      // we inject. The provided problem.value is used as-is.
    },
    global: {
      stubs,
      provide: {
        // The SFC calls useProblemContext() — give it the same
        // ProblemContextKey symbol the real app uses (we import it
        // from the same module), plus the synthetic context value.
        [ProblemContextKey as unknown as symbol]: {
          problem: problemRef,
          runResult: ref(null),
          contestId: ref(contestSlug),
        },
        [ContestProblemContextKey as unknown as symbol]: ctxValue,
      },
      config: {
        globalProperties: {
          // $router is needed because the SFC uses $router.push in
          // some templates. We only implement `.push`; the rest of
          // the Router shape is irrelevant for this test.
          $router: { push: routerPushMock } as unknown as Record<string, unknown>,
        },
      },
      mocks: {
        $route: { params: { tab: undefined }, query: {} },
      },
    },
  });
}

describe("LayoutHeaderLeft — contest mode", () => {
  it("renders 'Back to Contest' link instead of the problemset toggle", () => {
    const wrapper = mountHeader(
      { id: 101, slug: "reverse-linked-list" },
      { isInContest: true },
    );
    // The contest "返回比赛" link is the only top-level RouterLink in
    // contest mode (no problemset toggle). We assert the i18n key is
    // resolved to the actual key string our stub returns.
    expect(wrapper.text()).toContain("contest.detail.backToContest");
    // The non-contest "题库" toggle must NOT render in contest mode.
    expect(wrapper.text()).not.toContain("problem.layout.problemSet");
  });

  it("hides the random-problem button in contest mode", () => {
    const wrapper = mountHeader(
      { id: 101, slug: "reverse-linked-list" },
      { isInContest: true },
    );
    // The random button uses lucide `Shuffle` which our stub renders as
    // an empty <svg />. We assert that the toolbar HTML has no <button>
    // whose data-testid is "random". There is no such attribute in the
    // current SFC, so we instead assert that the random-problem i18n
    // key never shows up.
    expect(wrapper.text()).not.toContain("problem.layout.randomProblem");
  });

  it("shows the random-problem button outside contest mode (regression)", () => {
    const wrapper = mountHeader({ id: 101, slug: "reverse-linked-list" });
    expect(wrapper.text()).toContain("problem.layout.randomProblem");
  });

  it("shows the in-contest guard when the URL's contest does not include the current problem", () => {
    const wrapper = mountHeader(
      { id: 999, slug: "some-other-problem" },
      {
        isInContest: true,
        problemBelongsToContest: false,
        contestSlug: "linked-list-special",
      },
    );
    // The ContestProblemNotInContest stub carries this testid.
    expect(wrapper.find('[data-testid="not-in-contest-guard"]').exists()).toBe(
      true,
    );
  });

  it("does NOT show the in-contest guard when the problem is in the contest", () => {
    const wrapper = mountHeader(
      { id: 101, slug: "reverse-linked-list" },
      { isInContest: true, problemBelongsToContest: true },
    );
    expect(wrapper.find('[data-testid="not-in-contest-guard"]').exists()).toBe(
      false,
    );
  });

  it("does not show the guard outside contest mode", () => {
    const wrapper = mountHeader(
      { id: 101, slug: "reverse-linked-list" },
      { problemBelongsToContest: false },
    );
    expect(wrapper.find('[data-testid="not-in-contest-guard"]').exists()).toBe(
      false,
    );
  });
});
