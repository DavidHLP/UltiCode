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
import { defineComponent, h, nextTick } from "vue";
import { mount, type VueWrapper } from "@vue/test-utils";
import {
  createMemoryHistory,
  createRouter,
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
      // Expose the composable surface onto the public instance so the
      // test can drive `initLayout` exactly the way `ProblemDetailView`
      // does in production (`onMounted(() => initLayout())`).
      const layout = useProblemLayout();
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
});
