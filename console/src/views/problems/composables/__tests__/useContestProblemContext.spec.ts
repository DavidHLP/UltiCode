import { describe, expect, it, vi, beforeEach } from "vitest";
import { ref, nextTick, type Ref } from "vue";

// `vi.hoisted` runs before module imports, so we cannot use vue
// primitives inside it. The plain values below act as a bridge:
// the `vi.mock` factory (which runs after imports resolve) reads
// them and promotes them to reactive refs.
const testState = vi.hoisted(() => ({
  contestIdValue: null as string | null,
  problemIdValue: 101 as number | null,
  isAuth: true,
  // These are *placeholder* references; the real reactive refs are
  // declared inside the mock factory below. We only use them as
  // bookkeeping holders so the mock factory can find them.
  currentContestRef: null as Ref<{ id: string; slug: string } | null> | null,
  userParticipationRef: null as Ref<Map<string, unknown>> | null,
  contestProblemsRef: null as Ref<Map<string, unknown>> | null,
  problemRef: null as Ref<{ id: number | null } | null> | null,
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ query: { contestId: testState.contestIdValue } }),
}));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: testState.isAuth }),
}));

vi.mock("pinia", () => ({
  // The real `storeToRefs` takes a reactive store and returns refs
  // for each property. Our mock returns the pre-allocated refs the
  // `vi.hoisted` block created, so the composable's
  // `storeToRefs(contestStore)` returns reactive refs whose `.value`
  // mutates trigger Vue's watch.
  storeToRefs: () => ({
    currentContest: testState.currentContestRef as Ref<unknown>,
    userParticipation: testState.userParticipationRef as Ref<unknown>,
    contestProblems: testState.contestProblemsRef as Ref<unknown>,
  }),
}));

vi.mock("@/stores/contestDetail", () => {
  // Create the real refs here so `storeToRefs` (above) can return
  // them. The actions also live here, mutating the same refs the
  // composable reads from.
  const currentContest = ref<{ id: string; slug: string } | null>(null);
  const userParticipation = ref<Map<string, unknown>>(new Map());
  const contestProblems = ref<Map<string, unknown>>(new Map());

  testState.currentContestRef = currentContest as Ref<{ id: string; slug: string } | null>;
  testState.userParticipationRef = userParticipation;
  testState.contestProblemsRef = contestProblems;

  return {
    useContestDetailStore: () => ({
      currentContest,
      userParticipation,
      contestProblems,
      loadContestDetail: vi.fn((id: string) => {
        // Sync (no `async`) so the loader watch can finish in the
        // same microtask burst as the test's `nextTick()` flushes.
        currentContest.value = { id: `db-${id}`, slug: id };
        return Promise.resolve(currentContest.value);
      }),
      loadProblems: vi.fn((id: string) => {
        const list = [{ problemId: 101, problemIndex: "A", slug: "a" }];
        contestProblems.value.set(id, list);
        return Promise.resolve(list);
      }),
      loadParticipationStatus: vi.fn((id: string) => {
        const status = { contestId: id, score: 0 };
        userParticipation.value.set(id, status);
        return Promise.resolve(status);
      }),
    }),
  };
});

import { useContestProblemContext } from "../useContestProblemContext";

describe("useContestProblemContext", () => {
  beforeEach(() => {
    testState.contestIdValue = null;
    testState.problemIdValue = 101;
    testState.isAuth = true;
    // Reset the in-mock refs to a clean state.
    if (testState.currentContestRef) testState.currentContestRef.value = null;
    if (testState.userParticipationRef)
      testState.userParticipationRef.value = new Map();
    if (testState.contestProblemsRef)
      testState.contestProblemsRef.value = new Map();
    if (testState.problemRef) testState.problemRef.value = { id: 101 };
  });

  it("is a no-op when there is no ?contestId= in the route", async () => {
    const ctx = useContestProblemContext(ref({ id: testState.problemIdValue }));
    expect(ctx.isInContest.value).toBe(false);
  });

  it("loads contest data the first time the contestId appears", async () => {
    testState.contestIdValue = "linked-list-special";
    const ctx = useContestProblemContext(ref({ id: testState.problemIdValue }));
    await nextTick();
    await nextTick();
    expect(ctx.isInContest.value).toBe(true);
    expect(
      (testState.contestProblemsRef!.value as Map<string, unknown>).has(
        "linked-list-special",
      ),
    ).toBe(true);
  });

  it("skips loadContestDetail when the store already has the contest", async () => {
    testState.currentContestRef!.value = {
      id: "db-1",
      slug: "linked-list-special",
    };
    testState.contestIdValue = "linked-list-special";

    useContestProblemContext(ref({ id: testState.problemIdValue }));
    await nextTick();
    await nextTick();
    // Slug matches the store's loaded contest, so the composable
    // skips the network call but still re-fetches problems (the
    // map was empty before this test).
    expect(testState.currentContestRef!.value?.slug).toBe("linked-list-special");
  });

  it("flags problemBelongsToContest=true when the current problem matches the list", async () => {
    testState.contestIdValue = "linked-list-special";
    const ctx = useContestProblemContext(ref({ id: testState.problemIdValue }));
    // The loader watch is async (awaits 3 store actions); each
    // `nextTick` flushes one microtask. Five ticks is enough to
    // settle the chain on a deterministic CI runner.
    for (let i = 0; i < 5; i++) await nextTick();
    expect(ctx.problemBelongsToContest.value).toBe(true);
  });

  it("flags problemBelongsToContest=false when the current problem is not in the list", async () => {
    testState.contestIdValue = "linked-list-special";
    const ctx = useContestProblemContext(ref({ id: 999 }));
    for (let i = 0; i < 5; i++) await nextTick();
    expect(ctx.problemBelongsToContest.value).toBe(false);
  });

  it("returns null for problemBelongsToContest while problems are still loading", async () => {
    testState.contestIdValue = "linked-list-special";
    const ctx = useContestProblemContext(ref({ id: testState.problemIdValue }));
    // Synchronously after the first watch run, problems haven't been
    // populated yet.
    expect(ctx.problemBelongsToContest.value).toBe(null);
  });

  it("refreshParticipation re-calls the store action", async () => {
    testState.contestIdValue = "linked-list-special";
    const ctx = useContestProblemContext(ref({ id: testState.problemIdValue }));
    await nextTick();
    await nextTick();
    await ctx.refreshParticipation();
    expect(
      (testState.userParticipationRef!.value as Map<string, unknown>).has(
        "linked-list-special",
      ),
    ).toBe(true);
  });

  it("does not populate participation for anonymous users", async () => {
    testState.isAuth = false;
    testState.contestIdValue = "linked-list-special";
    const ctx = useContestProblemContext(ref({ id: testState.problemIdValue }));
    await nextTick();
    await nextTick();
    expect(ctx.participation.value).toBe(null);
  });
});
