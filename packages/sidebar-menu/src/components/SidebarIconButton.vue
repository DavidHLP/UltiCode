<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";
defineProps<{ label: string; class?: string }>();
const emit = defineEmits<{ click: [MouseEvent] }>();
</script>
<template>
  <Button
    type="button"
    variant="ghost"
    size="icon-sm"
    :class="$props.class"
    :aria-label="label"
    @click="emit('click', $event)"
    ><slot
  /></Button>
</template>
