<script setup lang="ts">
import { provide, ref } from "vue";

import { useHeaderStore, type LayoutNode } from "@/stores/headerStore";

import LayoutTreeNode from "./LayoutTreeNode.vue";

defineProps<{
  layout: LayoutNode;
}>();

const headerStore = useHeaderStore();
const { moveHeaderBetweenGroups, splitGroup } = headerStore;

const dragState = ref<{
  sourceGroupId: string | null;
  sourceIndex: number | null;
}>({ sourceGroupId: null, sourceIndex: null });

provide("dragState", dragState);
provide("moveHeaderBetweenGroups", moveHeaderBetweenGroups);
provide("splitGroup", splitGroup);
</script>

<template>
  <div class="h-full w-full">
    <div class="h-full w-full">
      <LayoutTreeNode :node="layout" :is-root="true" :key="layout.id" />
    </div>
  </div>
</template>
