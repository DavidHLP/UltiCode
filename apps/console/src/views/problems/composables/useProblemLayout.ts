import { ref, nextTick, watch, markRaw, computed, type Ref } from "vue";
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

// Header id 2 = solutions tab. Hidden in contest / virtual contest context
// so competitors cannot see other participants' editorial-style solutions
// while the contest is live.
const SOLUTIONS_HEADER_ID = 2;

function createInitialHeaderGroups(
  t: (key: string) => string,
  isContest: boolean,
): HeaderGroup[] {
  const problemInfoHeaders = [
    {
      id: 1,
      index: 0,
      title: t("problem.layout.problemDescription"),
      icon: "FileText",
      iconColor: "var(--accent-primary)",
    },
  ];
  if (!isContest) {
    problemInfoHeaders.push({
      id: SOLUTIONS_HEADER_ID,
      index: problemInfoHeaders.length,
      title: t("problem.layout.solution"),
      icon: "FlaskConical",
      iconColor: "var(--accent-primary)",
    });
  }
  problemInfoHeaders.push({
    id: 3,
    index: problemInfoHeaders.length,
    title: t("problem.layout.submissions"),
    icon: "History",
    iconColor: "var(--accent-primary)",
  });

  return [
    {
      id: "problem-info",
      name: t("problem.layout.problemInfo"),
      headers: problemInfoHeaders,
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
          iconColor: "var(--status-success-mark)",
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
          iconColor: "var(--status-success-mark)",
        },
        {
          id: 6,
          index: 1,
          title: t("problem.layout.testResults"),
          icon: "Terminal",
          iconColor: "var(--status-success-mark)",
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

function getLeetLayoutConfig(
  t: (key: string) => string,
  isContest: boolean,
) {
  const groups = createInitialHeaderGroups(t, isContest);
  const children: LayoutNodeType[] = [
    {
      id: "programming-left",
      type: "leaf",
      size: 55,
      groupId: "problem-info",
      groupMetadata: {
        id: "problem-info",
        name: t("problem.layout.problemInfo"),
      },
    },
    {
      id: "programming-right",
      type: "container",
      size: 45,
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

function getClassicLayoutConfig(
  t: (key: string) => string,
  isContest: boolean,
) {
  const groups = createInitialHeaderGroups(t, isContest);
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

function getCompactLayoutConfig(
  t: (key: string) => string,
  isContest: boolean,
) {
  const groups = createInitialHeaderGroups(t, isContest);
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

function getWideLayoutConfig(
  t: (key: string) => string,
  isContest: boolean,
) {
  const groups = createInitialHeaderGroups(t, isContest);
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

export function useProblemLayout(contestId: Ref<string | null>) {
  const { t } = useI18n();
  const route = useRoute();
  const router = useRouter();
  const headerStore = useHeaderStore();
  const { layoutConfig } = storeToRefs(headerStore);
  const currentLayout = ref<ProblemLayout>("leet");
  const lastTab = ref<string | null>(null);

  // Contest context is threaded in from ProblemDetailView (which derives it
  // once from `route.query.contestId`) — the same source the mobile layout,
  // contest dock, and header consume, so the contest-mode signal is derived
  // once instead of re-read from the route here. Solutions are hidden so
  // contestants cannot read other people's editorial write-ups while the
  // contest is live.
  const isContest = computed(() => contestId.value !== null);

  const getLayoutConfig = (newLayout: ProblemLayout) => {
    switch (newLayout) {
      case "leet":
        return getLeetLayoutConfig(t, isContest.value);
      case "classic":
        return getClassicLayoutConfig(t, isContest.value);
      case "compact":
        return getCompactLayoutConfig(t, isContest.value);
      case "wide":
        return getWideLayoutConfig(t, isContest.value);
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
      // In contest mode the solutions tab does not exist; fall back to
      // the description tab and rewrite the URL so reloads/deep links
      // never land on a panel that cannot be rendered.
      if (isContest.value && tabName === "solutions") {
        isUpdatingFromRoute.value = true;
        void router
          .replace({
            name: "problem-detail",
            params: { ...route.params, tab: "description" },
            query: route.query,
          })
          .finally(() => {
            nextTick(() => {
              isUpdatingFromRoute.value = false;
            });
          });
        return;
      }
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

  // Re-init the layout when the user navigates between a contest problem
  // and a regular problem so the solutions tab appears/disappears
  // consistently with the current contest state. Preserve the user's
  // currently-selected tab when possible; only fall back to Description
  // when the previous selection is no longer present (e.g. the user was
  // on Solutions and is now entering a contest).
  watch(isContest, () => {
    const previousActive = headerStore.activeHeaderByGroup["problem-info"];
    const config = getLayoutConfig(currentLayout.value);
    headerStore.initData(config.groups, config.layout);
    if (previousActive === null || previousActive === undefined) return;
    const problemInfoGroup = config.groups.find(
      (g) => g.id === "problem-info",
    );
    const stillExists = problemInfoGroup?.headers.some(
      (h) => h.id === previousActive,
    );
    if (stillExists) {
      headerStore.setActiveHeader("problem-info", previousActive);
    }
    // Otherwise initData already reset to the first header (Description),
    // which is the safe default for the contest-entry case.
  });

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
              // Preserve the query string (notably ?contestId=...) so the
              // contest-aware layout stays active. Dropping it here would
              // cause `isContest` to flip false and the hidden Solutions
              // tab to reappear on the very next tab click.
              query: route.query,
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
    const initialConfig = getLeetLayoutConfig(t, isContest.value);
    headerStore.initData(initialConfig.groups, initialConfig.layout);

    const tabParam = route.params.tab;
    let tabName = Array.isArray(tabParam) ? tabParam[0] : tabParam;
    // In contest mode the solutions tab is not rendered, so any
    // ?tab=solutions deep link should resolve to the description tab.
    if (isContest.value && tabName === "solutions") {
      tabName = "description";
    }
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
