import {
  computed,
  defineComponent,
  h,
  onMounted,
  onUnmounted,
  type Component,
} from "vue";
import { useRoute } from "vue-router";

import LayoutHeaderLeft from "../headers/LayoutHeaderLeft.vue";
import LayoutHeaderCenter from "../headers/LayoutHeaderCenter.vue";
import LayoutHeaderControls from "../headers/LayoutHeaderControls.vue";
import LayoutTree from "@/features/layout/tree/LayoutTree.vue";
import { useI18n } from "vue-i18n";
import { useBreakpoints } from "@/composables/useBreakpoints";
import MobileProblemLayout from "../components/MobileProblemLayout.vue";
import DescriptionView from "@/views/problems/description/DescriptionView.vue";
import ProblemSolutionsView from "@/views/problems/solutions/ProblemSolutionsView.vue";
import SubmissionsView from "@/views/problems/submissions/SubmissionsView.vue";
import CodeView from "../code/CodeView.vue";
import TestCaseView from "../test/TestCaseView.vue";
import TestResultsView from "../test/TestResultsView.vue";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet";
import ProblemListDrawer from "@/components/problem/ProblemListDrawer.vue";
import ProblemNotesDrawer from "@/components/problem/ProblemNotesDrawer.vue";
import { problemHooks } from "@/hooks/problem-hooks";

import { useProblemLayout } from "./useProblemLayout";
import { useProblemPanels } from "./useProblemPanels";
import { useContestProblemContext } from "./useContestProblemContext";
import { useProblemDetail } from "../useProblemDetail";
import { useProblemContext } from "../useProblemContext";
import { PanelComponentMapKey } from "@/features/layout/panels/panel-context";
import {
  ContestProblemContextKey,
  ProblemContextKey,
  ToggleNotesKey,
  ToggleSidePanelKey,
} from "../problem-context";

/**
 * Deep Problem solving session module (architecture-review candidate #3,
 * top recommendation).
 *
 * <p>Before the deepening, {@code ProblemDetailView} owned six connector
 * modules inline (connected description / solutions / submissions / code
 * / test-case / test-results view components), threaded two provide-inject
 * keys ({@code ToggleSidePanelKey}, {@code ToggleNotesKey}), exposed
 * {@code PanelComponentMapKey}, and bootstrapped the layout policy.
 * Correctness depended on setup order: {@code ProblemContextKey} MUST be
 * provided before {@code ContestProblemContextKey} because the contest
 * composable injects the problem ref, and the panel map MUST be provided
 * before {@code useProblemLayout} initialises. Source-string tests
 * existed to keep the order pinned.
 *
 * <p>After the deepening, this composable owns navigation interpretation,
 * problem load, contest context, layout, panels, and the panel component
 * map. The view adapter ({@code ProblemDetailView}) calls
 * {@code useProblemSession} and renders exactly what the session exposes;
 * setup order is internal to the session.
 *
 * <p>Returns the same field set the view previously assembled inline, so
 * the adapter only has to wire template bindings.
 *
 * @author ulticode
 */
export function useProblemSession() {
  const { t } = useI18n();
  const route = useRoute();
  const { isMobile } = useBreakpoints();

  // --- Route → slug/contestId (single source of truth) -------------------
  const slug = computed(() => {
    const slugParam = route.params.slug;
    const resolved = Array.isArray(slugParam) ? slugParam[0] : slugParam;
    return resolved ?? null;
  });
  const contestId = computed(() => {
    const contestParam = route.query.contestId;
    if (Array.isArray(contestParam)) {
      return contestParam[0] ?? null;
    }
    return typeof contestParam === "string" && contestParam.length > 0
      ? contestParam
      : null;
  });

  // --- Core data + panels ------------------------------------------------
  // ProblemContextKey must be provided before any composable that
  // injects it (useContestProblemContext depends on it for the
  // `problem` ref used in contestProblemNav / problemBelongsToContest).
  // Setup order is encapsulated here; the view no longer has to know.
  const { problem, runResult } = useProblemDetail(slug);
  const { isSidePanelOpen, isNotesOpen, toggleSidePanel, toggleNotes } =
    useProblemPanels();
  const contestCtx = useContestProblemContext(problem);

  const problemContext = { problem, runResult, contestId };
  const contestContextValue = contestCtx;

  // --- Layout policy -----------------------------------------------------
  const { currentLayout, layoutConfig, handleLayoutChange, initLayout } =
    useProblemLayout(contestId);

  // --- Connector components (moved out of the view) ----------------------
  const ConnectedDescriptionView = defineComponent({
    setup() {
      const { problem } = useProblemContext();
      return () =>
        problem.value
          ? h(
              "div",
              { class: "px-1 py-2" },
              h(DescriptionView, { problem: problem.value }),
            )
          : h(
              "div",
              { class: "flex items-center justify-center h-full" },
              t("common.status.loading"),
            );
    },
  });

  const ConnectedSolutionsView = defineComponent({
    setup() {
      const { problem } = useProblemContext();
      return () =>
        problem.value
          ? h(
              "div",
              { class: "px-1 py-2" },
              h(ProblemSolutionsView, {
                problemId: problem.value.id,
                followUp: problem.value.followUp ?? "",
              }),
            )
          : h(
              "div",
              { class: "flex items-center justify-center h-full" },
              t("common.status.loading"),
            );
    },
  });

  const ConnectedSubmissionsView = defineComponent({
    setup() {
      const { problem, contestId } = useProblemContext();
      return () =>
        problem.value
          ? h(
              "div",
              { class: "px-1 py-2" },
              h(SubmissionsView, {
                problemId: problem.value.id,
                contestId: contestId.value ?? undefined,
              }),
            )
          : h(
              "div",
              { class: "flex items-center justify-center h-full" },
              t("common.status.loading"),
            );
    },
  });

  const ConnectedCodeView = defineComponent({
    setup() {
      const { problem } = useProblemContext();
      return () =>
        problem.value && problem.value.languages.length
          ? h(CodeView, {
              key: problem.value.id,
              languages: problem.value.languages,
              starterNotes: problem.value.starterNotes ?? [],
              problemKey: problem.value.slug,
            })
          : h(
              "div",
              { class: "flex items-center justify-center h-full" },
              t("common.status.loading"),
            );
    },
  });

  const ConnectedTestCaseView = defineComponent({
    setup() {
      const { problem } = useProblemContext();
      return () =>
        problem.value
          ? h(
              "div",
              { class: "px-1 py-2" },
              h(TestCaseView, { testCases: problem.value.testCases ?? [] }),
            )
          : h(
              "div",
              { class: "flex items-center justify-center h-full" },
              t("common.status.loading"),
            );
    },
  });

  const ConnectedTestResultsView = defineComponent({
    setup() {
      const { runResult } = useProblemContext();
      return () =>
        h(
          "div",
          { class: "px-1 py-2" },
          h(TestResultsView, { runResult: runResult.value }),
        );
    },
  });

  // Header id → connected component map. Constant for the session;
  // a future feature flag could swap the map, but the seam is internal.
  const panelComponentMap: Record<number, Component> = {
    1: ConnectedDescriptionView,
    2: ConnectedSolutionsView,
    3: ConnectedSubmissionsView,
    4: ConnectedCodeView,
    5: ConnectedTestCaseView,
    6: ConnectedTestResultsView,
  };

  // --- Lifecycle ---------------------------------------------------------
  onMounted(() => {
    void problemHooks.emit("problem:view:mount", { slug: slug.value });
  });
  onUnmounted(() => {
    void problemHooks.emit("problem:view:unmount", { slug: slug.value });
  });
  onMounted(() => {
    initLayout();
  });

  // Surface the provide() / mount hooks so the adapter can register
  // them inside its <script setup> in the correct order. Vue's provide()
  // must run during a component's setup() phase; we expose a callback
  // the adapter invokes from its setup.
  function installProviders(
    provideFn: (key: symbol, value: unknown) => void,
  ): void {
    provideFn(ToggleSidePanelKey, toggleSidePanel);
    provideFn(ToggleNotesKey, toggleNotes);
    provideFn(ProblemContextKey, problemContext);
    provideFn(ContestProblemContextKey, contestContextValue);
    provideFn(PanelComponentMapKey, panelComponentMap);
  }

  return {
    installProviders,
    isMobile,
    isSidePanelOpen,
    isNotesOpen,
    problem,
    layoutConfig,
    currentLayout,
    handleLayoutChange,
    LayoutHeaderLeft,
    LayoutHeaderCenter,
    LayoutHeaderControls,
    LayoutTree,
    MobileProblemLayout,
    Sheet,
    SheetContent,
    SheetHeader,
    SheetTitle,
    SheetDescription,
    ProblemListDrawer,
    ProblemNotesDrawer,
  };
}