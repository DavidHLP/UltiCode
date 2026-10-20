<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { Button } from "@ulticode/design-system/ui/button";
import { Spinner } from "@ulticode/design-system/ui/spinner";
withDefaults(
  defineProps<{
    class?: HTMLAttributes["class"];
    type?: "button" | "submit" | "reset";
    disabled?: boolean;
    loading?: boolean;
    variant?: "primary" | "secondary";
  }>(),
  { type: "submit", variant: "primary" },
);
</script>
<template>
  <Button
    :class="$props.class"
    class="w-full"
    :type="type"
    :variant="variant === 'primary' ? 'default' : 'outline'"
    :disabled="disabled || loading"
    :aria-busy="loading"
    ><Spinner v-if="loading" /><slot
  /></Button>
</template>
