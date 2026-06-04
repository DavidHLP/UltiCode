import { ref, nextTick, watch, markRaw } from "vue";
import { useRoute, useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useHeaderStore, type HeaderGroup } from "@/stores/headerStore";
import type { LayoutNode as LayoutNodeType } from "@/types/header";
import { problemHooks, type ProblemLayout } from "@/hooks/problem-hooks";
import { useI18n } from "vue-i18n";

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

function createInitialHeaderGroups(t: (key: string) => string): HeaderGroup[] {
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
          iconColor: "oklch(0.6149 0.1394 244.9)",
        },
        {
          id: 2,
          index: 1,
          title: t("problem.layout.solution"),
          icon: "FlaskConical",
          iconColor: "oklch(0.6149 0.1394 244.9)",
        },
        {
          id: 3,
          index: 2,
          title: t("problem.layout.submissions"),
          icon: "History",
          iconColor: "oklch(0.6149 0.1394 244.9)",
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
          iconColor: "oklch(0.6444 0.1508 118.6)",
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
          iconColor: "oklch(0.6444 0.1508 118.6)",
        },
        {
          id: 6,
          index: 1,
          title: t("problem.layout.testResults"),
          icon: "Terminal",
          iconColor: "oklch(0.6444 0.1508 118.6)",
        },
      ],
    },
  ];
}

function buildLayoutConfig(
  groups: HeaderGroup[],
  children: LayoutNodeType[],
): LayoutNodeType {
  return markRaw({
    id: "root",
    type: "container" as const,
    direction: children.some((c) => c.direction)
      ? ("horizontal" as const)
      : ("vertical" as const),
    children,
  });
}

function getLeetLayoutConfig(t: (key: string) => string) {
  const groups = createInitialHeaderGroups(t);
  const children: LayoutNodeType[] = [
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
      size: 50,
      direction: "vertical",
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
  ];
  const layout = buildLayoutConfig(groups, children);
  return { groups, layout };
}

function getClassicLayoutConfig(t: (key: string) => string) {
  const groups = createInitialHeaderGroups(t);
  const children: LayoutNodeType[] = [
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
      size: 60,
      direction: "horizontal",
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
  ];
  const layout = buildLayoutConfig(groups, children);
  return { groups, layout };
}

function getCompactLayoutConfig(t: (key: string) => string) {
  const groups = createInitialHeaderGroups(t);
  const children: LayoutNodeType[] = [
    {
      id: "compact-left",
      type: "container",
      size: 30,
      direction: "vertical",
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
  ];
  const layout = buildLayoutConfig(groups, children);
  return { groups, layout };
}

function getWideLayoutConfig(t: (key: string) => string) {
  const groups = createInitialHeaderGroups(t);
  const children: LayoutNodeType[] = [
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
  ];
  const layout = buildLayoutConfig(groups, children);
  return { groups, layout };
}

export function useProblemLayout() {
  const { t } = useI18n();
  const route = useRoute();
  const router = useRouter();
  const headerStore = useHeaderStore();
  const { layoutConfig } = storeToRefs(headerStore);
  const currentLayout = ref<ProblemLayout>("leet");
  const lastTab = ref<string | null>(null);

  const getLayoutConfig = (newLayout: ProblemLayout) => {
    switch (newLayout) {
      case "leet":
        return getLeetLayoutConfig(t);
      case "classic":
        return getClassicLayoutConfig(t);
      case "compact":
        return getCompactLayoutConfig(t);
      case "wide":
        return getWideLayoutConfig(t);
    }
  };

  const handleLayoutChange = (newLayout: ProblemLayout) => {
    currentLayout.value = newLayout;
    const config = getLayoutConfig(newLayout);
    headerStore.initData(config.groups, config.layout);
    void problemHooks.emit("problem:layout:change", { layout: newLayout });
  };

  // Guards to prevent infinite loop between URL and store sync
  const isUpdatingFromRoute = ref(false);
  const isUpdatingFromStore = ref(false);

  // Sync URL to Store (when route changes, e.g. back button)
  watch(
    () => route.params.tab,
    (newTab) => {
      if (isUpdatingFromStore.value) return;
      const tabName = Array.isArray(newTab) ? newTab[0] : newTab;
      if (tabName && Object.prototype.hasOwnProperty.call(TAB_MAP, tabName)) {
        const targetId = TAB_MAP[tabName];
        if (
          targetId !== undefined &&
          headerStore.activeHeaderByGroup["problem-info"] !== targetId
        ) {
          isUpdatingFromRoute.value = true;
          headerStore.setActiveHeader("problem-info", targetId);
          nextTick(() => {
            isUpdatingFromRoute.value = false;
          });
        }
      } else if (!tabName) {
        if (headerStore.activeHeaderByGroup["problem-info"] !== 1) {
          isUpdatingFromRoute.value = true;
          headerStore.setActiveHeader("problem-info", 1);
          nextTick(() => {
            isUpdatingFromRoute.value = false;
          });
        }
      }
    },
  );

  // Sync Store to URL (when user clicks tabs)
  watch(
    () => headerStore.activeHeaderByGroup["problem-info"],
    (newHeaderId) => {
      if (isUpdatingFromRoute.value) return;
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
          isUpdatingFromStore.value = true;
          router
            .push({
              name: "problem-detail",
              params: { ...route.params, tab: tabName },
            })
            .then(() => {
              nextTick(() => {
                isUpdatingFromStore.value = false;
              });
            });
        }
      }
    },
  );

  function initLayout() {
    const initialConfig = getLeetLayoutConfig(t);
    headerStore.initData(initialConfig.groups, initialConfig.layout);

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
  }

  return {
    currentLayout,
    layoutConfig,
    lastTab,
    handleLayoutChange,
    initLayout,
  };
}
