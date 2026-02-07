<script setup lang="ts">
import { computed, inject, onMounted, ref, type Ref, watch } from "vue";
import { storeToRefs } from "pinia";

import type { HeaderModel } from "@/stores/headerStore";
import { useHeaderStore } from "@/stores/headerStore";

import LayoutPanelContent from "./LayoutPanelContent.vue";
import LayoutPanelHeader from "./LayoutPanelHeader.vue";
import PanelDropOverlay from "./PanelDropOverlay.vue";

const props = defineProps<{
  headers: HeaderModel[];
  onUpdate: (newHeaders: HeaderModel[]) => void;
  group?: string;
  isActive?: boolean;
}>();

const activeHeader = ref<HeaderModel | null>(null);
const localHeaders = ref<HeaderModel[]>([...props.headers]);
const draggedIndex = ref<number | null>(null);
const overIndex = ref<number | null>(null);
const dropPosition = ref<"before" | "after" | null>(null);

const dragState =
  inject<Ref<{ sourceGroupId: string | null; sourceIndex: number | null }>>(
    "dragState",
  );
const moveHeaderBetweenGroups = inject<
  (
    sourceGroupId: string,
    targetGroupId: string,
    sourceIndex: number,
    targetIndex: number,
  ) => void
>("moveHeaderBetweenGroups");

const splitGroup =
  inject<
    (
      sourceGroupId: string,
      targetGroupId: string,
      sourceIndex: number,
      direction: "top" | "bottom" | "left" | "right" | "center",
    ) => void
  >("splitGroup");

const headerStore = useHeaderStore();
const { activeHeaderByGroup } = storeToRefs(headerStore);
const { setActiveHeader } = headerStore;
const groupKey = computed(() => props.group || "default");

watch(
  () => props.headers,
  (newHeaders) => {
    localHeaders.value = [...newHeaders];
  },
  { deep: true },
);

onMounted(() => {
  if (localHeaders.value.length > 0 && !activeHeader.value) {
    activeHeader.value = localHeaders.value[0] || null;
    if (activeHeader.value) {
      setActiveHeader(groupKey.value, activeHeader.value.id);
    }
  }
});

watch(
  () => localHeaders.value,
  (newHeaders) => {
    const activeId = activeHeader.value?.id;
    const stillExists =
      activeId !== undefined &&
      newHeaders.some((header) => header.id === activeId);
    if (!stillExists) {
      activeHeader.value = newHeaders[0] || null;
    }
  },
  { immediate: true },
);

watch(
  () => activeHeaderByGroup.value[groupKey.value],
  (targetId) => {
    if (targetId === undefined) return;
    const next =
      localHeaders.value.find((header) => header.id === targetId) ||
      localHeaders.value[0] ||
      null;
    activeHeader.value = next;
  },
  { immediate: true },
);

watch(
  () => activeHeader.value,
  (header) => {
    setActiveHeader(groupKey.value, header?.id ?? null);
  },
);

const handleDragStart = (
  index: number,
  event: PointerEvent,
  handleStart: (e: PointerEvent) => void,
) => {
  draggedIndex.value = index;
  if (dragState) {
    dragState.value.sourceGroupId = props.group || "default";
    dragState.value.sourceIndex = index;
  }
  handleStart(event);
};

const handleDragOver = (index: number, event: PointerEvent) => {
  if (dragState?.value.sourceGroupId && dragState.value.sourceIndex !== null) {
    const target = event.currentTarget as HTMLElement;
    if (!target) return;

    const rect = target.getBoundingClientRect();
    const mouseX = event.clientX;
    const elementCenter = rect.left + rect.width / 2;

    overIndex.value = index;
    dropPosition.value = mouseX < elementCenter ? "before" : "after";
  }
};

const handleHeaderSelect = (header: HeaderModel) => {
  activeHeader.value = header;
  setActiveHeader(groupKey.value, header.id);
};

const handleDragEnd = () => {
  if (
    dragState?.value.sourceGroupId &&
    dragState.value.sourceIndex !== null &&
    overIndex.value !== null
  ) {
    const sourceGroupId = dragState.value.sourceGroupId;
    const sourceIndex = dragState.value.sourceIndex;
    const targetGroupId = props.group || "default";
    let targetIndex = overIndex.value;

    if (dropPosition.value === "after") {
      targetIndex += 1;
    }

    if (sourceGroupId === targetGroupId) {
      if (sourceIndex !== targetIndex) {
        const newHeaders = [...localHeaders.value];
        const [movedItem] = newHeaders.splice(sourceIndex, 1);
        if (movedItem) {
          const adjustedTargetIndex =
            targetIndex > sourceIndex ? targetIndex - 1 : targetIndex;
          newHeaders.splice(adjustedTargetIndex, 0, movedItem);
          localHeaders.value = newHeaders;
          props.onUpdate(newHeaders);
        }
      }
    } else if (moveHeaderBetweenGroups) {
      moveHeaderBetweenGroups(
        sourceGroupId,
        targetGroupId,
        sourceIndex,
        targetIndex,
      );
    }
  }

  draggedIndex.value = null;
  overIndex.value = null;
  dropPosition.value = null;
  if (dragState) {
    dragState.value.sourceGroupId = null;
    dragState.value.sourceIndex = null;
  }
};

const handleOverlayDrop = (
  zone: "top" | "bottom" | "left" | "right" | "center",
) => {
  if (
    dragState?.value.sourceGroupId &&
    dragState.value.sourceIndex !== null &&
    splitGroup
  ) {
    splitGroup(
      dragState.value.sourceGroupId,
      props.group || "default",
      dragState.value.sourceIndex,
      zone,
    );

    // Drag state cleanup is handled by the source component's dragEnd,
    // but we can also reset it here to be safe/responsive
    dragState.value.sourceGroupId = null;
    dragState.value.sourceIndex = null;
  }
};
</script>

<template>
  <div class="flex flex-col h-full rounded-lg overflow-hidden shadow-sm">
    <header class="flex items-center bg-[#fafafa] py-1 px-2 min-h-9">
      <div class="flex items-center min-h-8 flex-1">
        <LayoutPanelHeader
          v-for="(header, idx) in localHeaders"
          :key="header.id"
          :header="header"
          :index="idx"
          :group="group || 'default'"
          :is-dragging="draggedIndex === idx"
          :is-over="overIndex === idx && draggedIndex !== idx"
          :drop-position="overIndex === idx ? dropPosition : null"
          :show-separator="idx > 0"
          :is-active="activeHeader?.id === header.id"
          @drag-start="
            (event, handleStart) => handleDragStart(idx, event, handleStart)
          "
          @drag-over="(event) => handleDragOver(idx, event)"
          @drag-end="handleDragEnd"
          @header-click="handleHeaderSelect"
        />
      </div>
    </header>
    <main class="flex-1 overflow-hidden p-2 relative">
      <div class="h-full overflow-auto rounded-md bg-white">
        <LayoutPanelContent
          :active-header="activeHeader || null"
          :is-active="isActive"
        />
      </div>
      <PanelDropOverlay
        v-if="dragState?.sourceGroupId"
        @drop="handleOverlayDrop"
      />
    </main>
  </div>
</template>
