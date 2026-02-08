<script setup lang="ts">
import {
  computed,
  onMounted,
  onUnmounted,
  ref,
  provide,
  type Component,
} from "vue";
import { useRoute } from "vue-router";
import { storeToRefs } from "pinia";

import { useHeaderStore } from "@/stores/headerStore";
import { useProblemDetail } from "./useProblemDetail";
import { problemHooks, type ProblemLayout } from "@/hooks/problem-hooks";
import {
  ProblemContextKey,
  ToggleNotesKey,
  ToggleSidePanelKey,
} from "./problem-context";
import { PanelComponentMapKey } from "@/features/layout/panels/panel-context";
import { useProblemLayout } from "./composables/useProblemLayout";
import { useProblemLayoutConfig } from "./composables/useProblemLayoutConfig";
import { useProblemTabSync } from "./composables/useProblemTabSync";

// Import connector components
import {
  ConnectedDescriptionView,
  ConnectedSolutionsView,
  ConnectedSubmissionsView,
  ConnectedCodeView,
  ConnectedTestCaseView,
  ConnectedTestResultsView,
} from "./components/ProblemConnector.vue";

// Import layout component
import ProblemLayoutComponent from "./components/ProblemLayout.vue";

const { isSidePanelOpen, isNotesOpen, toggleSidePanel, toggleNotes } =
  useProblemLayout();
const { getLayoutConfig } = useProblemLayoutConfig();
const { initializeTab } = useProblemTabSync();

provide(ToggleSidePanelKey, toggleSidePanel);
provide(ToggleNotesKey, toggleNotes);

// --- Data Fetching ---
const slug = computed(() => {
  const slugParam = useRoute().params.slug;
  const resolved = Array.isArray(slugParam) ? slugParam[0] : slugParam;
  return resolved ?? null;
});
const contestId = computed(() => {
  const contestParam = useRoute().query.contestId;
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
provide(ProblemContextKey, { problem, runResult, contestId });

// --- Panel Component Map ---
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

const handleLayoutChange = (newLayout: ProblemLayout) => {
  currentLayout.value = newLayout;
  const config = getLayoutConfig(newLayout);
  headerStore.initData(config.groups, config.layout);
  void problemHooks.emit("problem:layout:change", { layout: newLayout });
};

onMounted(() => {
  const initialConfig = getLayoutConfig("leet");
  headerStore.initData(initialConfig.groups, initialConfig.layout);
  initializeTab();
});

onUnmounted(() => {
  void problemHooks.emit("problem:view:unmount", { slug: slug.value });
});
</script>

<template>
  <ProblemLayoutComponent
    :problem="problem"
    :is-side-panel-open="isSidePanelOpen"
    :is-notes-open="isNotesOpen"
    :current-layout="currentLayout"
    :layout-config="layoutConfig"
    @update:is-side-panel-open="isSidePanelOpen = $event"
    @update:is-notes-open="isNotesOpen = $event"
    @layout-change="handleLayoutChange"
  />
</template>
