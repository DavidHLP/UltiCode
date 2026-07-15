/**
 * Behavioural tests for `useProblemLayout`. The matching source-string
 * suite (`useProblemLayout.contest.spec.ts`) documents the structural
 * contract; this file exercises the actual composable end-to-end with a
 * memory router + pinia + i18n stub for the behaviours that are
 * reliable to assert through observables (headerStore state).
 *
 * URL rewrite and store→URL push behaviours are intentionally covered
 * by the source-string suite: vue-router memory history and watcher
 * ordering make them fragile to assert through the public router API.
 */
import { describe, it, expect, beforeEach } from "vitest";
import { defineComponent, h, nextTick, computed } from "vue";
import { mount, type VueWrapper } from "@vue/test-utils";
import {
  createMemoryHistory,
  createRouter,
  useRoute,
  type Router,
  type RouteRecordRaw,
} from "vue-router";
import { createPinia, setActivePinia } from "pinia";
import { createI18n } from "vue-i18n";
import { useHeaderStore } from "@/stores/headerStore";
import { useProblemLayout } from "../useProblemLayout";

const PROBLEM_LAYOUT_KEYS = {
  problemInfo: "Problem Info",
  problemDescription: "Description",
  solution: "Solutions",
  submissions: "Submissions",
  codeEditor: "Code",
  code: "Code Editor",
  testInfo: "Tests",
  testCases: "Test Cases",
  testResults: "Test Results",
} as const;

const i18n = createI18n({
  legacy: false,
  locale: "en",
  messages: {
    en: {
      problem: { layout: PROBLEM_LAYOUT_KEYS },
    },
  },
});

interface MountHandle {
  router: Router;
  wrapper: VueWrapper;
  headerStore: ReturnType<typeof useHeaderStore>;
}

async function mountLayout(path: string): Promise<MountHandle> {
  setActivePinia(createPinia());

  const TestHost = defineComponent({
    setup() {
      // Mirror ProblemDetailView: derive `contestId` from the route once
      // and thread it into useProblemLayout, so contest mode comes from a
      // single source instead of being re-read inside the composable.
      const route = useRoute();
      const contestId = computed(() => {
        const v = route.query.contestId;
        if (Array.isArray(v)) return v[0] ?? null;
        return typeof v === "string" && v.length > 0 ? v : null;
      });
      // Expose the composable surface onto the public instance so the
      // test can drive `initLayout` exactly the way `ProblemDetailView`
      // does in production (`onMounted(() => initLayout())`).
      const layout = useProblemLayout(contestId);
      return { layout };
    },
    render() {
      return h("div", { "data-testid": "host" });
    },
  });

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: "/problems/:slug/:tab?",
        name: "problem-detail",
        component: TestHost,
      },
    ] as RouteRecordRaw[],
  });

  await router.push(path);
  await router.isReady();

  const wrapper = mount(TestHost, {
    global: { plugins: [router, i18n] },
  });
  await nextTick();

  return { router, wrapper, headerStore: useHeaderStore() };
}

function layoutApi(wrapper: VueWrapper) {
  return (wrapper.vm as unknown as {
    layout: ReturnType<typeof useProblemLayout>;
  }).layout;
}

describe("useProblemLayout — contest context (behavioural)", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it("omits the Solutions header from the problem-info group on a contest problem", async () => {
    const { wrapper, headerStore } = await mountLayout(
      "/problems/two-sum?contestId=contest-1",
    );
    layoutApi(wrapper).initLayout();
    await nextTick();

    const problemInfo = headerStore.headerGroups.find(
      (g) => g.id === "problem-info",
    );
    expect(problemInfo).toBeDefined();
    const ids = problemInfo!.headers.map((h) => h.id);
    expect(ids).toEqual([1, 3]);
    expect(ids).not.toContain(2);
  });

  it("includes the Solutions header on a regular problem", async () => {
    const { wrapper, headerStore } = await mountLayout("/problems/two-sum");
    layoutApi(wrapper).initLayout();
    await nextTick();

    const problemInfo = headerStore.headerGroups.find(
      (g) => g.id === "problem-info",
    );
    expect(problemInfo!.headers.map((h) => h.id)).toEqual([1, 2, 3]);
  });

  it("removes the Solutions header reactively when entering a contest mid-session", async () => {
    const { router, wrapper, headerStore } = await mountLayout(
      "/problems/two-sum",
    );
    layoutApi(wrapper).initLayout();
    await nextTick();

    const before = headerStore.headerGroups.find((g) => g.id === "problem-info");
    expect(before!.headers.map((h) => h.id)).toContain(2);

    // Navigating from a regular problem into a contest problem must rebuild
    // the layout so the Solutions tab disappears (the contest-safety policy
    // reacting to the shared contest-mode signal).
    await router.push("/problems/two-sum?contestId=contest-1");
    await nextTick();
    await nextTick();

    const after = headerStore.headerGroups.find((g) => g.id === "problem-info");
    expect(after!.headers.map((h) => h.id)).toEqual([1, 3]);
    expect(after!.headers.map((h) => h.id)).not.toContain(2);
  });
});
