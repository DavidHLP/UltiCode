import { mount, flushPromises } from "@vue/test-utils";
import type { Router } from "vue-router";
import { describe, expect, it, vi } from "vitest";
import ContestProblemList from "../components/ContestProblemList.vue";
import type {
  ContestDetail,
  ContestProblemSummary,
} from "@/types/contest";

// ------------------------------------------------------------------
// Mocks — vue-router and vue-i18n are auto-imported by the SFC; we
// provide the bare minimum so the component can call $router.push
// and resolve i18n keys in the template.
// ------------------------------------------------------------------
const { routerPushMock } = vi.hoisted(() => ({
  routerPushMock: vi.fn(),
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: routerPushMock }),
  RouterLink: { template: "<a><slot /></a>" },
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

// ------------------------------------------------------------------
// Factories — minimal fixtures that satisfy the SFC's runtime shape
// without reproducing the entire ContestDetail interface.
// ------------------------------------------------------------------
interface ContestOverrides {
  status?: string;
  isVirtual?: boolean;
}

function makeContest({
  status = "FINISHED",
  isVirtual = false,
}: ContestOverrides = {}): ContestDetail {
  return {
    id: "contest-1",
    slug: "linked-list-special",
    title: "Linked List Special",
    status,
    // The SFC only reads `contest.status` and `contest.isVirtual` at
    // render time, so every other field is left empty and cast through
    // `as ContestDetail` to keep this fixture readable.
    startTime: "2026-06-18T10:00:00.000Z",
    endTime: "2026-06-18T12:00:00.000Z",
    duration: 120,
    contestType: "ICPC",
    participantCount: 0,
    problemCount: 1,
    isPremium: false,
    isPublished: true,
    isVisible: true,
    maxParticipants: 100,
    registeredCount: 0,
    isParticipating: false,
    userRanking: 0,
    isRated: true,
    scoringMode: "SCORE",
    penaltyPerWrong: 0,
    coverImage: "",
    description: "",
    isVirtual,
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
  } as unknown as ContestDetail;
}

function makeProblem(): ContestProblemSummary {
  return {
    id: "cp-1",
    contestId: "contest-1",
    problemId: 1001,
    problemIndex: "A",
    score: 100,
    penaltyPerWrong: 0,
    title: "Reverse Linked List",
    slug: "reverse-linked-list",
    difficulty: "Easy",
    solvedCount: 0,
    submissionCount: 0,
    acceptanceRate: "0%",
  };
}

// ------------------------------------------------------------------
// Stubs — replace the heavy shadcn-vue table/card/badge primitives
// with passthrough elements so we can assert on the rendered DOM
// without dragging in their internal styling/runtime requirements.
// ------------------------------------------------------------------
const stubs = {
  Card: { template: "<div><slot /></div>" },
  CardHeader: { template: "<div><slot /></div>" },
  CardTitle: { template: "<div><slot /></div>" },
  CardContent: { template: "<div><slot /></div>" },
  Table: { template: "<table><slot /></table>" },
  TableHeader: { template: "<thead><slot /></thead>" },
  TableBody: { template: "<tbody><slot /></tbody>" },
  TableRow: { template: "<tr><slot /></tr>" },
  TableHead: { template: "<th><slot /></th>" },
  TableCell: { template: "<td><slot /></td>" },
  Button: { template: "<button><slot /></button>" },
  Badge: { template: "<span><slot /></span>" },
  Lock: { template: "<svg />" },
  ChevronRight: { template: "<svg />" },
  Target: { template: "<svg />" },
  Award: { template: "<svg />" },
  Check: { template: "<svg />" },
};

interface MountOverrides {
  isInVirtualSession?: boolean;
}

function mountList(
  contest: ContestDetail,
  overrides: MountOverrides = {},
) {
  return mount(ContestProblemList, {
    props: {
      contest,
      problems: [makeProblem()],
      contestId: "linked-list-special",
      isRegistered: true,
      registering: false,
      getDifficultyColor: () => "text-muted-foreground",
      problemStatuses: { 1001: "todo" },
      isInVirtualSession: overrides.isInVirtualSession ?? false,
    },
    global: {
      stubs,
      // Make $router available inside the template so @click="$router.push(...)"
      // resolves to our mock. Without this, $router is undefined under
      // @vue/test-utils because we never install the vue-router plugin.
      // The component only ever calls `.push(...)` on the router, so a
      // minimal `Pick<Router, "push">` shape is enough — cast through
      // `unknown` because Router has 18+ fields we don't need to fake.
      config: {
        globalProperties: {
          $router: { push: routerPushMock } as unknown as Router,
        },
      },
    },
  });
}

// ------------------------------------------------------------------
// Tests — the bug is purely about the URL query that flows out of
// the problem list. We assert the router push payload directly.
// ------------------------------------------------------------------
describe("ContestProblemList", () => {
  it("forwards ?contestId=... while the contest is RUNNING (anti-cheat active)", async () => {
    routerPushMock.mockClear();
    const wrapper = mountList(makeContest({ status: "RUNNING" }));

    await wrapper.find("tbody tr").trigger("click");
    await flushPromises();

    expect(routerPushMock).toHaveBeenCalledTimes(1);
    expect(routerPushMock).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "reverse-linked-list" },
      query: { contestId: "linked-list-special" },
    });
  });

  it("omits the contestId query once the contest is FINISHED so users can read solutions", async () => {
    routerPushMock.mockClear();
    const wrapper = mountList(makeContest({ status: "FINISHED" }));

    await wrapper.find("tbody tr").trigger("click");
    await flushPromises();

    expect(routerPushMock).toHaveBeenCalledTimes(1);
    const call = routerPushMock.mock.calls[0]?.[0] as
      | { name: string; params: { slug: string }; query?: Record<string, unknown> }
      | undefined;
    expect(call).toMatchObject({
      name: "problem-detail",
      params: { slug: "reverse-linked-list" },
    });
    // The key invariant: no contestId query, so useProblemLayout treats
    // the destination as a normal problem and the Solutions tab is rendered.
    expect(call?.query?.contestId).toBeUndefined();
  });

  it("renders the locked screen and skips navigation while the contest is UPCOMING", async () => {
    routerPushMock.mockClear();
    const wrapper = mountList(makeContest({ status: "UPCOMING" }));

    // The problems table is gated behind v-else on the locked screen,
    // so there is no row to click and the locked block is rendered.
    expect(wrapper.find("tbody").exists()).toBe(false);
    expect(wrapper.text()).toContain("contest.detail.problemsLocked");
    expect(routerPushMock).not.toHaveBeenCalled();
  });

  it("keeps ?contestId=... when a virtual session is active, even though status is FINISHED", async () => {
    // Regression guard for the H1 review finding: virtual contests are
    // per-user replays of contests whose underlying `status` is already
    // FINISHED. Without the `isInVirtualSession` signal the Solutions
    // tab would be exposed mid-replay, defeating the anti-cheat
    // invariant at `useProblemLayout.ts:60-62`.
    //
    // R10.6 / H1: this case uses `isVirtual: true` (a virtual-only
    // contest). The real-world regression was the case below, where
    // `isVirtual: false` (a real, scheduled contest being virtually
    // replayed). The two cases must both keep `?contestId=...`.
    routerPushMock.mockClear();
    const wrapper = mountList(
      makeContest({ status: "FINISHED", isVirtual: true }),
      { isInVirtualSession: true },
    );

    await wrapper.find("tbody tr").trigger("click");
    await flushPromises();

    expect(routerPushMock).toHaveBeenCalledTimes(1);
    expect(routerPushMock).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "reverse-linked-list" },
      query: { contestId: "linked-list-special" },
    });
  });

  it("R10.6 keeps ?contestId=... when virtually replaying a real (isVirtual=false) contest", async () => {
    // Anti-cheat regression: virtual contests are per-user replays of
    // *real* scheduled contests — the DB `contests.is_virtual` flag
    // is false for those, but the user has an active virtual session
    // row in `contest_participants` (is_virtual=1). The previous guard
    // also required `props.contest.isVirtual === true`, which is
    // *false* for any real contest being replayed, so `?contestId=...`
    // was dropped and the Solutions tab became visible mid-replay.
    //
    // The fix delegates contestId-scoping to ContestDetailView (which
    // checks `virtualSession?.contestId === contestId`) and trusts
    // `isInVirtualSession` alone here. This test pins that contract:
    // `isVirtual: false` + `isInVirtualSession: true` MUST still keep
    // `?contestId=...`.
    routerPushMock.mockClear();
    const wrapper = mountList(
      makeContest({ status: "FINISHED", isVirtual: false }),
      { isInVirtualSession: true },
    );

    await wrapper.find("tbody tr").trigger("click");
    await flushPromises();

    expect(routerPushMock).toHaveBeenCalledTimes(1);
    expect(routerPushMock).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "reverse-linked-list" },
      query: { contestId: "linked-list-special" },
    });
  });

  it("drops ?contestId=... when a virtual session ends (isVirtual + isInVirtualSession=false)", async () => {
    // Companion to the regression guard above: once the user finishes
    // the virtual replay they should be free to browse solutions again.
    routerPushMock.mockClear();
    const wrapper = mountList(
      makeContest({ status: "FINISHED", isVirtual: true }),
      { isInVirtualSession: false },
    );

    await wrapper.find("tbody tr").trigger("click");
    await flushPromises();

    expect(routerPushMock).toHaveBeenCalledTimes(1);
    const call = routerPushMock.mock.calls[0]?.[0] as
      | { query?: Record<string, unknown> }
      | undefined;
    expect(call?.query?.contestId).toBeUndefined();
  });
});