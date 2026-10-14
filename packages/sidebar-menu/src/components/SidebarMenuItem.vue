<script setup lang="ts">
import { computed } from "vue";
import { Button } from "@ulticode/design-system/ui/button";
import { Badge } from "@ulticode/design-system/ui/badge";
import { ChevronRight } from "@lucide/vue";
defineOptions({ inheritAttrs: false });
const props = withDefaults(
  defineProps<{
    isActive?: boolean;
    as?: "link" | "button" | "a";
    class?: string;
    badge?: string | number;
    badgeVariant?: "default" | "accent" | "muted";
    iconClass?: string;
    showChevron?: boolean;
  }>(),
  { as: "link", badgeVariant: "default", showChevron: false },
);
const emit = defineEmits<{ toggle: [] }>();
const tag = computed(() =>
  props.as === "link" ? "router-link" : props.as === "a" ? "a" : "button",
);
function onChevronClick(event: MouseEvent) {
  event.preventDefault();
  event.stopPropagation();
  emit("toggle");
}
</script>
<template>
  <div class="flex min-w-0 items-center gap-1" data-slot="sidebar-menu-item">
    <Button
      variant="ghost"
      as-child
      class="flex-1 justify-start"
      :class="props.class"
      data-sidebar="menu-button"
      :data-active="isActive || undefined"
    >
      <component :is="tag" v-bind="$attrs">
        <span
          v-if="$slots.icon"
          class="flex shrink-0 items-center"
          :class="iconClass"
          ><slot name="icon"
        /></span>
        <slot />
        <Badge
          v-if="badge !== undefined && badge !== null"
          class="ms-auto tabular-nums"
          :variant="badgeVariant === 'accent' ? 'default' : 'secondary'"
          >{{ badge }}</Badge
        >
      </component>
    </Button>
    <Button
      v-if="showChevron"
      type="button"
      variant="ghost"
      size="icon-sm"
      aria-label="toggle section"
      @click="onChevronClick"
      ><slot name="chevron"><ChevronRight /></slot
    ></Button>
  </div>
</template>
