import { markRaw } from "vue";
import type { LayoutNode, HeaderGroup } from "@/stores/headerStore";
import { type ProblemLayout } from "@/hooks/problem-hooks";

export function useProblemLayoutConfig() {
  const createInitialHeaderGroups = (): HeaderGroup[] => {
    return [
      {
        id: "problem-info",
        name: "problem.layout.problemInfo",
        headers: [
          {
            id: 1,
            index: 0,
            title: "problem.layout.problemDescription",
            icon: "FileText",
            color: "#1a1a1a",
            iconColor: "#007bff",
          },
          {
            id: 2,
            index: 1,
            title: "problem.layout.solution",
            icon: "FlaskConical",
            color: "#1a1a1a",
            iconColor: "#007bff",
          },
          {
            id: 3,
            index: 2,
            title: "problem.layout.submissions",
            icon: "History",
            color: "#1a1a1a",
            iconColor: "#007bff",
          },
        ],
      },
      {
        id: "code-editor",
        name: "problem.layout.codeEditor",
        headers: [
          {
            id: 4,
            index: 0,
            title: "problem.layout.code",
            icon: "Code2",
            color: "#1a1a1a",
            iconColor: "#02b128",
          },
        ],
      },
      {
        id: "test-info",
        name: "problem.layout.testInfo",
        headers: [
          {
            id: 5,
            index: 0,
            title: "problem.layout.testCases",
            icon: "SquareCheck",
            color: "#1a1a1a",
            iconColor: "#02b128",
          },
          {
            id: 6,
            index: 1,
            title: "problem.layout.testResults",
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
    const layout: LayoutNode = markRaw({
      id: "programming-root",
      type: "container",
      direction: "horizontal",
      children: [
        {
          id: "left-panel",
          type: "leaf",
          groupId: "problem-info",
          flex: 1,
          minSize: 300,
          defaultSize: 450,
        },
        {
          id: "center-split",
          type: "container",
          direction: "vertical",
          flex: 1.5,
          minSize: 400,
          children: [
            {
              id: "code-panel",
              type: "leaf",
              groupId: "code-editor",
              flex: 1,
              minSize: 200,
            },
            {
              id: "test-panel",
              type: "leaf",
              groupId: "test-info",
              flex: 0.8,
              minSize: 150,
            },
          ],
        },
      ],
    });

    return { groups, layout };
  };

  const getStackedLayoutConfig = () => {
    const groups = createInitialHeaderGroups();
    const layout: LayoutNode = markRaw({
      id: "stacked-root",
      type: "container",
      direction: "vertical",
      children: [
        {
          id: "top-panel",
          type: "leaf",
          groupId: "problem-info",
          flex: 1,
          minSize: 200,
        },
        {
          id: "middle-panel",
          type: "leaf",
          groupId: "code-editor",
          flex: 1,
          minSize: 200,
        },
        {
          id: "bottom-panel",
          type: "leaf",
          groupId: "test-info",
          flex: 0.8,
          minSize: 150,
        },
      ],
    });

    return { groups, layout };
  };

  const getFocusLayoutConfig = () => {
    const groups = createInitialHeaderGroups();
    const layout: LayoutNode = markRaw({
      id: "focus-root",
      type: "leaf",
      groupId: "code-editor",
      flex: 1,
      minSize: 300,
    });

    return { groups, layout };
  };

  const getLayoutConfig = (layoutType: ProblemLayout) => {
    switch (layoutType) {
      case "leet":
        return getLeetLayoutConfig();
      case "stacked":
        return getStackedLayoutConfig();
      case "focus":
        return getFocusLayoutConfig();
      default:
        return getLeetLayoutConfig();
    }
  };

  return {
    createInitialHeaderGroups,
    getLeetLayoutConfig,
    getStackedLayoutConfig,
    getFocusLayoutConfig,
    getLayoutConfig,
  };
}
