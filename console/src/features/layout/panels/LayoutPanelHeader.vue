<script setup lang="ts">
import { computed, ref } from "vue";
import { useDraggable, useDroppable } from "@vue-dnd-kit/core";
import { Button } from "@/components/ui/button";
import type { HeaderModel } from "@/stores/headerStore";
import * as LucideIcons from "lucide-vue-next";

const props = defineProps<{
  header: HeaderModel;
  index: number;
  group: string;
  isDragging: boolean;
  isOver: boolean;
  dropPosition?: "before" | "after" | null;
  showSeparator: boolean;
  isActive?: boolean; // 添加是否激活的属性
}>();

const emit = defineEmits<{
  (
    e: "drag-start",
    event: PointerEvent,
    handleStart: (e: PointerEvent) => void,
  ): void;
  (e: "drag-over", event: PointerEvent): void;
  (e: "drag-end"): void;
  (e: "header-click", header: HeaderModel): void; // 修改事件定义，不再传递 group
}>();

const headerId = computed(
  () => `header-${props.group}-${props.header.id}-${props.index}`,
);

const { elementRef: draggableRef, handleDragStart } = useDraggable({
  id: headerId.value,
  data: { index: props.index, groupId: props.group },
  groups: ["headers"],
});

const { elementRef: droppableRef } = useDroppable({
  data: { index: props.index, groupId: props.group },
  groups: ["headers"],
});

// 拖拽相关状态
const pointerDownEvent = ref<PointerEvent | null>(null);
const initialPosition = ref<{ x: number; y: number } | null>(null);
const hasMoved = ref(false);
const MOVE_THRESHOLD = 5; // 移动阈值（像素）

const getIconComponent = (iconName?: string) => {
  if (!iconName) return null;
  const icon = (LucideIcons as Record<string, unknown>)[iconName];
  if (icon && (typeof icon === "object" || typeof icon === "function")) {
    return icon;
  }
  return null;
};

const onPointerDown = (e: PointerEvent) => {
  pointerDownEvent.value = e;
  initialPosition.value = { x: e.clientX, y: e.clientY };
  hasMoved.value = false;
};

const onPointerMove = (e: PointerEvent) => {
  if (!pointerDownEvent.value || !initialPosition.value) return;

  // 计算移动距离
  const deltaX = Math.abs(e.clientX - initialPosition.value.x);
  const deltaY = Math.abs(e.clientY - initialPosition.value.y);

  // 如果移动距离超过阈值，标记为已移动并开始拖拽
  if (!hasMoved.value && (deltaX > MOVE_THRESHOLD || deltaY > MOVE_THRESHOLD)) {
    hasMoved.value = true;
    if (pointerDownEvent.value) {
      emit("drag-start", pointerDownEvent.value, handleDragStart);
    }
  }
};

const onPointerUp = () => {
  // 重置状态
  pointerDownEvent.value = null;
  initialPosition.value = null;

  // 如果已经移动过（触发了拖拽），触发拖拽结束事件
  if (hasMoved.value) {
    emit("drag-end");
  }

  hasMoved.value = false;
};

const onPointerLeave = () => {
  // 指针离开时的处理逻辑保持不变
};

const onPointerOver = (e: PointerEvent) => {
  // 如果正在按下状态，检查移动
  if (pointerDownEvent.value) {
    onPointerMove(e);
  }
  emit("drag-over", e);
};

const activeTabStyle = computed(() => {
  if (!props.isActive) return {};
  const styles: Record<string, string> = {
    color: "var(--foreground)",
  };
  if (props.header.iconColor) {
    styles.borderColor = "var(--border)";
    styles.borderTopColor = props.header.iconColor;
    styles.backgroundColor = "var(--background)";
  }
  return styles;
});

const onHeaderClick = () => {
  // 只有在没有移动时才触发点击事件
  if (!hasMoved.value) {
    emit("header-click", props.header);
  }
};

const setRef = (el: unknown) => {
  if (el) {
    draggableRef.value = el as HTMLElement;
    droppableRef.value = el as HTMLElement;
  }
};
</script>

<template>
  <div
    :ref="setRef"
    class="flex items-center h-full cursor-move relative align-middle"
    :style="{ touchAction: 'none' }"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerover="onPointerOver"
    @pointerup="onPointerUp"
    @pointerleave="onPointerLeave"
  >
    <Button
      variant="ghost"
      size="sm"
      class="relative h-8 -mb-px rounded-none terminal-tab transition-all cursor-pointer"
      :class="[
        isActive
          ? 'text-foreground hover:text-foreground hover:bg-background bg-background border-l border-r border-t-2 border-b-transparent border-border z-10'
          : 'text-silver-500 hover:bg-[var(--silver-100)]/40 hover:text-silver-700 border border-transparent',
      ]"
      :style="activeTabStyle"
      @click="onHeaderClick"
    >
      <component
        :is="getIconComponent(header.icon)"
        v-if="getIconComponent(header.icon)"
        :style="{ color: header.iconColor }"
        class="h-3.5 w-3.5 mr-1.5"
      />
      <span class="text-xs font-semibold leading-none">{{ header.title }}</span>
    </Button>
  </div>
</template>
