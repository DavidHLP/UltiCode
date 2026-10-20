<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";
import { Badge } from "@ulticode/design-system/ui/badge";
defineOptions({ inheritAttrs: false });
withDefaults(
  defineProps<{
    isActive?: boolean;
    size?: "sm" | "md";
    class?: string;
    badge?: string | number;
    iconClass?: string;
  }>(),
  { size: "md" },
);
</script>
<template>
  <Button
    variant="ghost"
    as-child
    class="w-full justify-start group-data-[collapsible=icon]:hidden"
    :class="$props.class"
    :size="size === 'sm' ? 'sm' : 'default'"
    data-sidebar="menu-sub-button"
    :data-active="isActive || undefined"
  >
    <component :is="$attrs.to ? 'router-link' : 'a'" v-bind="$attrs"
      ><span
        v-if="$slots.icon"
        class="flex shrink-0 items-center"
        :class="iconClass"
        ><slot name="icon" /></span
      ><slot /><Badge
        v-if="badge !== undefined && badge !== null"
        variant="secondary"
        class="ms-auto tabular-nums"
        >{{ badge }}</Badge
      ></component
    >
  </Button>
</template>
