import { defineStore } from "pinia";
import { ref, computed } from "vue";
import type { HeaderModel, HeaderGroup, LayoutNode } from "@/types/header";

// Re-export types for backward compatibility
export type { HeaderModel, HeaderGroup, LayoutNode } from "@/types/header";

export const useHeaderStore = defineStore("header", () => {
  const layoutConfig = ref<LayoutNode | null>(null);
  const activeGroupId = ref<string | null>(null);
  const headerGroups = ref<HeaderGroup[]>([]);
  const activeHeaderByGroup = ref<Record<string, number | null>>({});

  const visibleGroups = computed(() =>
    headerGroups.value.filter((g) => g.headers.length > 0),
  );

  const initData = (groups: HeaderGroup[], layout: LayoutNode) => {
    headerGroups.value = groups;
    layoutConfig.value = layout;
    activeHeaderByGroup.value = groups.reduce<Record<string, number | null>>(
      (acc, group) => {
        acc[group.id] = group.headers[0]?.id ?? null;
        return acc;
      },
      {},
    );
    // 默认选中第一个有内容的组
    const firstVisible = groups.find((g) => g.headers.length > 0);
    if (firstVisible) {
      activeGroupId.value = firstVisible.id;
    }
  };

  const updateGroupHeaders = (groupId: string, newHeaders: HeaderModel[]) => {
    const group = headerGroups.value.find((g) => g.id === groupId);
    if (group) {
      group.headers = newHeaders.map((header, idx) => ({
        ...header,
        index: idx,
      }));
      const activeId = activeHeaderByGroup.value[groupId];
      const stillExists = newHeaders.some((h) => h.id === activeId);
      if (!stillExists) {
        activeHeaderByGroup.value[groupId] = newHeaders[0]?.id ?? null;
      }
    }
  };

  const moveHeaderBetweenGroups = (
    sourceGroupId: string,
    targetGroupId: string,
    sourceIndex: number,
    targetIndex: number,
  ) => {
    const sourceGroup = headerGroups.value.find((g) => g.id === sourceGroupId);
    const targetGroup = headerGroups.value.find((g) => g.id === targetGroupId);

    if (!sourceGroup || !targetGroup) return;

    const [movedItem] = sourceGroup.headers.splice(sourceIndex, 1);
    if (movedItem) {
      targetGroup.headers.splice(targetIndex, 0, movedItem);

      sourceGroup.headers = sourceGroup.headers.map((header, idx) => ({
        ...header,
        index: idx,
      }));

      targetGroup.headers = targetGroup.headers.map((header, idx) => ({
        ...header,
        index: idx,
      }));

      const sourceActive = activeHeaderByGroup.value[sourceGroupId];
      if (!sourceGroup.headers.some((h) => h.id === sourceActive)) {
        activeHeaderByGroup.value[sourceGroupId] =
          sourceGroup.headers[0]?.id ?? null;
      }
      activeHeaderByGroup.value[targetGroupId] = movedItem.id;
    }
  };

  const setActiveGroup = (groupId: string) => {
    activeGroupId.value = groupId;
  };

  const setActiveHeader = (groupId: string, headerId: number | null) => {
    activeHeaderByGroup.value[groupId] = headerId;
  };

  const updateLayout = (layout: LayoutNode) => {
    layoutConfig.value = layout;
  };

  const splitGroup = (
    sourceGroupId: string,
    targetGroupId: string,
    sourceIndex: number,
    direction: "top" | "bottom" | "left" | "right" | "center",
  ) => {
    const sourceGroup = headerGroups.value.find((g) => g.id === sourceGroupId);
    const targetGroup = headerGroups.value.find((g) => g.id === targetGroupId);

    if (!sourceGroup || !targetGroup) return;

    // Remove from source
    const [movedItem] = sourceGroup.headers.splice(sourceIndex, 1);
    if (!movedItem) return;

    // Re-index source group
    sourceGroup.headers.forEach((h, i) => (h.index = i));
    const sourceActive = activeHeaderByGroup.value[sourceGroupId];
    if (!sourceGroup.headers.some((h) => h.id === sourceActive)) {
      activeHeaderByGroup.value[sourceGroupId] =
        sourceGroup.headers[0]?.id ?? null;
    }

    if (direction === "center") {
      // Move to target group (append)
      targetGroup.headers.push(movedItem);
      targetGroup.headers.forEach((h, i) => (h.index = i));
      activeHeaderByGroup.value[targetGroupId] = movedItem.id;
      return;
    }

    // Create new group for the split
    const newGroupId = `group-${Date.now()}`;
    const newGroup: HeaderGroup = {
      id: newGroupId,
      name: "New Group", // Could be dynamic based on header title
      headers: [movedItem],
    };
    newGroup.headers.forEach((h, i) => (h.index = i));
    headerGroups.value.push(newGroup);
    activeHeaderByGroup.value[newGroupId] = movedItem.id;

    // Helper to modify layout tree
    const modifyLayout = (node: LayoutNode): boolean => {
      if (node.type === "leaf" && node.groupId === targetGroupId) {
        // Convert leaf to container
        const originalGroupId = node.groupId;
        const originalGroupMetadata = node.groupMetadata;

        // Reset properties of the current node to become a container
        node.type = "container";
        node.groupId = undefined;
        node.groupMetadata = undefined;
        node.direction =
          direction === "left" || direction === "right"
            ? "horizontal"
            : "vertical";

        const newLeafNode: LayoutNode = {
          id: `leaf-${newGroupId}`,
          type: "leaf",
          groupId: newGroupId,
          groupMetadata: {
            id: newGroupId,
            name: "New Group",
          },
          size: 50,
        };

        const originalLeafNode: LayoutNode = {
          id: `leaf-${originalGroupId}-${Date.now()}`,
          type: "leaf",
          groupId: originalGroupId,
          groupMetadata: originalGroupMetadata,
          size: 50,
        };

        if (direction === "left" || direction === "top") {
          node.children = [newLeafNode, originalLeafNode];
        } else {
          node.children = [originalLeafNode, newLeafNode];
        }
        return true;
      }

      if (node.children) {
        for (const child of node.children) {
          if (modifyLayout(child)) return true;
        }
      }
      return false;
    };

    if (layoutConfig.value) {
      modifyLayout(layoutConfig.value);
    }

    // Ensure new group is active
    activeGroupId.value = newGroupId;
  };

  return {
    headerGroups,
    visibleGroups,
    layoutConfig,
    activeGroupId,
    activeHeaderByGroup,
    initData,
    updateGroupHeaders,
    moveHeaderBetweenGroups,
    setActiveGroup,
    setActiveHeader,
    updateLayout,
    splitGroup,
  };
});
