<script setup lang="ts">
import type { InputGroupButtonProps } from ".";
import { cn } from "@ulticode/design-system/utils/cn";
import { Button } from "@ulticode/design-system/ui/button";
import { inputGroupButtonVariants } from ".";

const props = withDefaults(defineProps<InputGroupButtonProps>(), {
  size: "xs",
  variant: "ghost",
});
</script>

<template>
  <Button
    type="button"
    :data-size="props.size"
    :variant="props.variant"
    :class="cn(inputGroupButtonVariants({ size: props.size }), props.class)"
  >
    <slot />
  </Button>
</template>
