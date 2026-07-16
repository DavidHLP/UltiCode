<script setup lang="ts">
import {
  computed,
  onMounted,
  onUnmounted,
  provide,
  h,
  defineComponent,
  type Component,
} from "vue";
import { useRoute } from "vue-router";

import LayoutHeaderLeft from "./headers/LayoutHeaderLeft.vue";
import LayoutHeaderCenter from "./headers/LayoutHeaderCenter.vue";
import LayoutHeaderControls from "./headers/LayoutHeaderControls.vue";
import LayoutTree from "@/features/layout/tree/LayoutTree.vue";
import { useProblemLayout } from "./composables/useProblemLayout";
import { useProblemPanels } from "./composables/useProblemPanels";
import { useContestProblemContext } from "./composables/useContestProblemContext";
import { useProblemDetail } from "./useProblemDetail";

import DescriptionView from "@/views/problems/description/DescriptionView.vue";
import ProblemSolutionsView from "@/views/problems/solutions/ProblemSolutionsView.vue";
import SubmissionsView from "@/views/problems/submissions/SubmissionsView.vue";
import CodeView from "./code/CodeView.vue";
import TestCaseView from "./test/TestCaseView.vue";
import TestResultsView from "./test/TestResultsView.vue";
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
import {
  ContestProblemContextKey,
  ProblemContextKey,
  ToggleNotesKey,
  ToggleSidePanelKey,
} from "./problem-context";
import { PanelComponentMapKey } from "@/features/layout/panels/panel-context";
import { useProblemContext } from "./useProblemContext";
import { useI18n } from "vue-i18n";
import { useBreakpoints } from "@/composables/useBreakpoints";
import MobileProblemLayout from "./components/MobileProblemLayout.vue";

const { t } = useI18n();
const { isMobile } = useBreakpoints();
const { isSidePanelOpen, isNotesOpen, toggleSidePanel, toggleNotes } =
  useProblemPanels();

provide(ToggleSidePanelKey, toggleSidePanel);
provide(ToggleNotesKey, toggleNotes);

// --- Data Fetching (must run BEFORE any composable that injects
//     ProblemContextKey, so the provider is registered first) ---
const route = useRoute();
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
const { problem, runResult } = useProblemDetail(slug);

// --- Context Providers ---
// ProblemContextKey must be provided before any composable that
// injects it (useContestProblemContext depends on it for the
// `problem` ref used in contestProblemNav / problemBelongsToContest).
provide(ProblemContextKey, { problem, runResult, contestId });

// Provide the contest context for header / shell / review panel
// consumers. The composable self-loads contest data from the store
// when `route.query.contestId` is set; on a regular problem page
// the inject in LayoutHeaderLeft falls back to a no-op and the
// existing site-wide nav is preserved.
const contestCtx = useContestProblemContext(problem);
provide(ContestProblemContextKey, contestCtx);

onMounted(() => {
  void problemHooks.emit("problem:view:mount", { slug: slug.value });
});

// --- Connector Components ---
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

// Map Header IDs to Components
const panelComponentMap: Record<number, Component> = {
  1: ConnectedDescriptionView,
  2: ConnectedSolutionsView,
  3: ConnectedSubmissionsView,
  4: ConnectedCodeView,
  5: ConnectedTestCaseView,
  6: ConnectedTestResultsView,
};

provide(PanelComponentMapKey, panelComponentMap);

// --- Layout Logic ---
const { currentLayout, layoutConfig, handleLayoutChange, initLayout } =
  useProblemLayout(contestId);

onUnmounted(() => {
  void problemHooks.emit("problem:view:unmount", { slug: slug.value });
});

// Initialize layout on mount
onMounted(() => {
  initLayout();
});
</script>

<template>
  <div class="h-screen flex flex-col bg-[var(--background)] antialiased">
    <a
      href="#main-content"
      class="sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:px-4 focus:py-2 focus:bg-primary focus:text-primary-foreground focus:rounded-none"
    >
      {{ t("common.skipToContent") }}
    </a>
    <Sheet v-model:open="isSidePanelOpen">
      <SheetContent side="left" class="p-0 w-[400px] sm:w-[540px]">
        <SheetHeader class="sr-only">
          <SheetTitle>{{ t("problem.drawer.problemList") }}</SheetTitle>
          <SheetDescription>{{
            t("problem.drawer.noProblemsFound")
          }}</SheetDescription>
        </SheetHeader>
        <ProblemListDrawer
          :current-problem-id="problem?.id"
          @close="isSidePanelOpen = false"
        />
      </SheetContent>
    </Sheet>

    <Sheet :open="isNotesOpen" @update:open="isNotesOpen = $event">
      <SheetContent side="right" class="p-0 w-[400px] sm:w-[500px]">
        <SheetHeader class="sr-only">
          <SheetTitle>{{ t("problem.notes.title") }}</SheetTitle>
          <SheetDescription>{{
            t("problem.notes.description")
          }}</SheetDescription>
        </SheetHeader>
        <ProblemNotesDrawer
          v-if="problem"
          :problem-id="Number(problem.id)"
          @close="isNotesOpen = false"
        />
      </SheetContent>
    </Sheet>

    <header
      class="relative flex h-12 w-full min-w-[100px] shrink-0 items-center justify-between gap-2 bg-[var(--background)] px-2.5"
    >
      <div
        class="relative z-10 flex h-full min-w-[240px] flex-1 items-center overflow-hidden"
      >
        <LayoutHeaderLeft />
      </div>
      <div
        class="pointer-events-none absolute inset-0 flex items-center justify-center"
      >
        <div class="pointer-events-auto">
          <LayoutHeaderCenter />
        </div>
      </div>
      <div
        class="relative z-10 ml-auto flex h-full flex-1 items-center justify-end gap-2"
      >
        <LayoutHeaderControls
          :current-layout="currentLayout"
          :problem="problem"
          @layout-change="handleLayoutChange"
        />
      </div>
    </header>

    <main
      id="main-content"
      class="flex-1 min-h-0 overflow-hidden w-full p-4 pt-0"
      role="main"
    >
      <MobileProblemLayout v-if="isMobile" />
      <LayoutTree
        v-else-if="layoutConfig"
        :layout="layoutConfig"
        class="h-full w-full"
      />
    </main>
  </div>
</template>
