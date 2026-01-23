<script setup lang="ts">
import {
  computed,
  onMounted,
  onUnmounted,
  ref,
  watch,
  provide,
  h,
  defineComponent,
  type Component,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";

import LayoutHeaderLeft from "./headers/LayoutHeaderLeft.vue";
import LayoutHeaderCenter from "./headers/LayoutHeaderCenter.vue";
import LayoutHeaderControls from "./headers/LayoutHeaderControls.vue";
import LayoutTree from "@/features/layout/tree/LayoutTree.vue";
import {
  useHeaderStore,
  type HeaderGroup,
  type LayoutNode,
} from "@/stores/headerStore";

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
import { problemHooks, type ProblemLayout } from "@/hooks/problem-hooks";
import {
  ProblemContextKey,
  ToggleNotesKey,
  ToggleSidePanelKey,
} from "./problem-context";
import { PanelComponentMapKey } from "@/features/layout/panels/panel-context";
import { useProblemContext } from "./useProblemContext";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const isSidePanelOpen = ref(false);
const toggleSidePanel = () => {
  isSidePanelOpen.value = !isSidePanelOpen.value;
};

const isNotesOpen = ref(false);
const toggleNotes = () => {
  isNotesOpen.value = !isNotesOpen.value;
};

provide(ToggleSidePanelKey, toggleSidePanel);
provide(ToggleNotesKey, toggleNotes);

// --- Data Fetching ---
const route = useRoute();
const router = useRouter();
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
  return typeof contestParam === "string" ? contestParam : null;
});
const { problem, runResult } = useProblemDetail(slug);

onMounted(() => {
  void problemHooks.emit("problem:view:mount", { slug: slug.value });
});

// --- Context Provider ---
// Provide problem and runResult to connector components
provide(ProblemContextKey, { problem, runResult, contestId });

// --- Connector Components ---
// These wrappers adapt the injected context to the specific props required by the views

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
const headerStore = useHeaderStore();
const { layoutConfig } = storeToRefs(headerStore);
const currentLayout = ref<ProblemLayout>("leet");
const lastTab = ref<string | null>(null);

const createInitialHeaderGroups = (): HeaderGroup[] => {
  return [
    {
      id: "problem-info",
      name: t("problem.layout.problemInfo"),
      headers: [
        {
          id: 1,
          index: 0,
          title: t("problem.layout.problemDescription"),
          icon: "FileText",
          color: "#1a1a1a",
          iconColor: "#007bff",
        },
        {
          id: 2,
          index: 1,
          title: t("problem.layout.solution"),
          icon: "FlaskConical",
          color: "#1a1a1a",
          iconColor: "#007bff",
        },
        {
          id: 3,
          index: 2,
          title: t("problem.layout.submissions"),
          icon: "History",
          color: "#1a1a1a",
          iconColor: "#007bff",
        },
      ],
    },
    {
      id: "code-editor",
      name: t("problem.layout.codeEditor"),
      headers: [
        {
          id: 4,
          index: 0,
          title: t("problem.layout.code"),
          icon: "Code2",
          color: "#1a1a1a",
          iconColor: "#02b128",
        },
      ],
    },
    {
      id: "test-info",
      name: t("problem.layout.testInfo"),
      headers: [
        {
          id: 5,
          index: 0,
          title: t("problem.layout.testCases"),
          icon: "SquareCheck",
          color: "#1a1a1a",
          iconColor: "#02b128",
        },
        {
          id: 6,
          index: 1,
          title: t("problem.layout.testResults"),
          icon: "Terminal",
          color: "#1a1a1a",
          iconColor: "#02b128",
        },
      ],
    },
  ];
};

const getLeetLayoutConfig = () => {
  const groups = createInitialHeaderGroups();
  const layout: LayoutNode = {
    id: "programming-root",
    type: "container",
    direction: "horizontal",
    children: [
      {
        id: "programming-left",
        type: "leaf",
        size: 50,
        groupId: "problem-info",
        groupMetadata: {
          id: "problem-info",
          name: t("problem.layout.problemInfo"),
        },
      },
      {
        id: "programming-right",
        type: "container",
        direction: "vertical",
        size: 50,
        children: [
          {
            id: "programming-right-top",
            type: "leaf",
            size: 50,
            groupId: "code-editor",
            groupMetadata: {
              id: "code-editor",
              name: t("problem.layout.codeEditor"),
            },
          },
          {
            id: "programming-right-bottom",
            type: "leaf",
            size: 50,
            groupId: "test-info",
            groupMetadata: {
              id: "test-info",
              name: t("problem.layout.testInfo"),
            },
          },
        ],
      },
    ],
  };
  return { groups, layout };
};

const getClassicLayoutConfig = () => {
  const groups = createInitialHeaderGroups();
  const layout: LayoutNode = {
    id: "classic-root",
    type: "container",
    direction: "vertical",
    children: [
      {
        id: "classic-top",
        type: "leaf",
        size: 40,
        groupId: "problem-info",
        groupMetadata: {
          id: "problem-info",
          name: t("problem.layout.problemInfo"),
        },
      },
      {
        id: "classic-bottom",
        type: "container",
        direction: "horizontal",
        size: 60,
        children: [
          {
            id: "classic-bottom-left",
            type: "leaf",
            size: 50,
            groupId: "code-editor",
            groupMetadata: {
              id: "code-editor",
              name: t("problem.layout.codeEditor"),
            },
          },
          {
            id: "classic-bottom-right",
            type: "leaf",
            size: 50,
            groupId: "test-info",
            groupMetadata: {
              id: "test-info",
              name: t("problem.layout.testInfo"),
            },
          },
        ],
      },
    ],
  };
  return { groups, layout };
};

const getCompactLayoutConfig = () => {
  const groups = createInitialHeaderGroups();
  const layout: LayoutNode = {
    id: "compact-root",
    type: "container",
    direction: "horizontal",
    children: [
      {
        id: "compact-left",
        type: "container",
        direction: "vertical",
        size: 30,
        children: [
          {
            id: "compact-left-top",
            type: "leaf",
            size: 50,
            groupId: "problem-info",
            groupMetadata: {
              id: "problem-info",
              name: t("problem.layout.problemInfo"),
            },
          },
          {
            id: "compact-left-bottom",
            type: "leaf",
            size: 50,
            groupId: "test-info",
            groupMetadata: {
              id: "test-info",
              name: t("problem.layout.testInfo"),
            },
          },
        ],
      },
      {
        id: "compact-right",
        type: "leaf",
        size: 70,
        groupId: "code-editor",
        groupMetadata: {
          id: "code-editor",
          name: t("problem.layout.codeEditor"),
        },
      },
    ],
  };
  return { groups, layout };
};

const getWideLayoutConfig = () => {
  const groups = createInitialHeaderGroups();
  const layout: LayoutNode = {
    id: "wide-root",
    type: "container",
    direction: "horizontal",
    children: [
      {
        id: "wide-left",
        type: "leaf",
        size: 25,
        groupId: "problem-info",
        groupMetadata: {
          id: "problem-info",
          name: t("problem.layout.problemInfo"),
        },
      },
      {
        id: "wide-center",
        type: "leaf",
        size: 50,
        groupId: "code-editor",
        groupMetadata: {
          id: "code-editor",
          name: t("problem.layout.codeEditor"),
        },
      },
      {
        id: "wide-right",
        type: "leaf",
        size: 25,
        groupId: "test-info",
        groupMetadata: { id: "test-info", name: t("problem.layout.testInfo") },
      },
    ],
  };
  return { groups, layout };
};

const handleLayoutChange = (newLayout: ProblemLayout) => {
  currentLayout.value = newLayout;
  let config;
  switch (newLayout) {
    case "leet":
      config = getLeetLayoutConfig();
      break;
    case "classic":
      config = getClassicLayoutConfig();
      break;
    case "compact":
      config = getCompactLayoutConfig();
      break;
    case "wide":
      config = getWideLayoutConfig();
      break;
  }
  headerStore.initData(config.groups, config.layout);
  void problemHooks.emit("problem:layout:change", { layout: newLayout });
};

const TAB_MAP: Record<string, number> = {
  description: 1,
  solutions: 2,
  submissions: 3,
};

const REV_TAB_MAP: Record<number, string> = {
  1: "description",
  2: "solutions",
  3: "submissions",
};

// Sync URL to Store (when route changes, e.g. back button)
watch(
  () => route.params.tab,
  (newTab) => {
    const tabName = Array.isArray(newTab) ? newTab[0] : newTab;
    if (tabName && Object.prototype.hasOwnProperty.call(TAB_MAP, tabName)) {
      const targetId = TAB_MAP[tabName];
      if (
        targetId !== undefined &&
        headerStore.activeHeaderByGroup["problem-info"] !== targetId
      ) {
        headerStore.setActiveHeader("problem-info", targetId);
      }
    } else if (!tabName) {
      // Default to description if no tab specified
      if (headerStore.activeHeaderByGroup["problem-info"] !== 1) {
        headerStore.setActiveHeader("problem-info", 1);
      }
    }
  },
);

// Sync Store to URL (when user clicks tabs)
watch(
  () => headerStore.activeHeaderByGroup["problem-info"],
  (newHeaderId) => {
    if (newHeaderId && newHeaderId in REV_TAB_MAP) {
      const tabName = REV_TAB_MAP[newHeaderId];
      if (!tabName) return;
      if (tabName !== lastTab.value) {
        void problemHooks.emit("problem:tab:change", {
          from: lastTab.value,
          to: tabName,
        });
        lastTab.value = tabName;
      }
      if (route.params.tab !== tabName) {
        router.push({
          name: "problem-detail",
          params: { ...route.params, tab: tabName },
        });
      }
    }
  },
);

onMounted(() => {
  const initialConfig = getLeetLayoutConfig();
  headerStore.initData(initialConfig.groups, initialConfig.layout);

  // Restore tab from URL
  const tabParam = route.params.tab;
  const tabName = Array.isArray(tabParam) ? tabParam[0] : tabParam;
  if (tabName) {
    const targetId = TAB_MAP[tabName];
    if (targetId !== undefined) {
      headerStore.setActiveHeader("problem-info", targetId);
    }
    lastTab.value = tabName;
  } else {
    lastTab.value = "description";
  }
});

onUnmounted(() => {
  void problemHooks.emit("problem:view:unmount", { slug: slug.value });
});
</script>

<template>
  <div class="h-screen flex flex-col bg-[#f0f0f0] antialiased">
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
          <SheetDescription>
            {{ t("problem.notes.description") }}
          </SheetDescription>
        </SheetHeader>
        <ProblemNotesDrawer
          v-if="problem"
          :problem-id="Number(problem.id)"
          @close="isNotesOpen = false"
        />
      </SheetContent>
    </Sheet>

    <header
      class="relative flex h-12 w-full min-w-[100px] shrink-0 items-center justify-between gap-2 bg-[#f0f0f0] px-2.5"
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

    <!-- Dynamic layout area -->
    <main class="flex-1 min-h-0 overflow-hidden w-full p-4 pt-0">
      <LayoutTree
        v-if="layoutConfig"
        :layout="layoutConfig"
        class="h-full w-full"
      />
    </main>
  </div>
</template>
