<script setup lang="ts">
import { computed, toRefs } from "vue";
import { storeToRefs } from "pinia";

import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "@/components/ui/resizable";
import { useHeaderStore, type LayoutNode } from "@/stores/headerStore";

import LayoutPanel from "../panels/LayoutPanel.vue";

defineOptions({ name: "LayoutTreeNode" });

const props = defineProps<{
  node: LayoutNode;
  isRoot?: boolean;
}>();

const { node, isRoot } = toRefs(props);

const headerStore = useHeaderStore();
const { headerGroups, activeGroupId } = storeToRefs(headerStore);
const { updateGroupHeaders, setActiveGroup } = headerStore;

const handleGroupClick = (groupId: string) => {
  setActiveGroup(groupId);
};

const getGroupHeaders = (groupId: string) => {
  return headerGroups.value.find((g) => g.id === groupId)?.headers || [];
};

const shouldShowNode = (layoutNode: LayoutNode): boolean => {
  if (layoutNode.type === "leaf" && layoutNode.groupId) {
    return getGroupHeaders(layoutNode.groupId).length > 0;
  }
  if (layoutNode.type === "container" && layoutNode.children) {
    return layoutNode.children.some((child) => shouldShowNode(child));
  }
  return false;
};

const visibleChildren = computed(() => {
  const currentNode = node.value;
  if (currentNode.type !== "container" || !currentNode.children) {
    return [];
  }
  return currentNode.children.filter((child) => shouldShowNode(child));
});
</script>

<template>
  <div class="h-full w-full">
    <!-- Container Node -->
    <ResizablePanelGroup
      v-if="node.type === 'container' && visibleChildren.length > 0"
      :id="node.id"
      :key="node.id"
      :direction="node.direction || 'horizontal'"
      :class="['h-full w-full gap-2', { 'p-2': isRoot }]"
    >
      <template v-for="(child, index) in visibleChildren" :key="child.id">
        <ResizablePanel
          :id="child.id"
          :default-size="child.size"
          :min-size="20"
          class="rounded-xl overflow-hidden"
        >
          <!-- Recursively render children -->
          <LayoutTreeNode :node="child" />
        </ResizablePanel>

        <ResizableHandle
          v-if="index < visibleChildren.length - 1"
          with-handle
        />
      </template>
    </ResizablePanelGroup>

    <!-- Leaf Node -->
    <div
      v-else-if="
        node.type === 'leaf' &&
        node.groupId &&
        getGroupHeaders(node.groupId).length > 0
      "
      class="h-full cursor-pointer rounded-xl border border-transparent overflow-hidden bg-white"
      :class="{ 'border-[#dedede] shadow-sm': activeGroupId === node.groupId }"
      @click="handleGroupClick(node.groupId)"
    >
      <LayoutPanel
        :headers="getGroupHeaders(node.groupId)"
        :group="node.groupId"
        :on-update="
          (newHeaders) => updateGroupHeaders(node.groupId!, newHeaders)
        "
        :is-active="activeGroupId === node.groupId"
        class="h-full"
      />
    </div>
  </div>
</template>
