<script setup lang="ts">
import type { AlertDialogActionProps } from "reka-ui";
import type { HTMLAttributes } from "vue";
import type { ButtonVariants } from "@ulticode/design-system/ui/button";
import { reactiveOmit } from "@vueuse/core";
import { AlertDialogAction } from "reka-ui";
import { cn } from "@ulticode/design-system/utils/cn";
import { buttonVariants } from "@ulticode/design-system/ui/button";

const props = withDefaults(
  defineProps<
    AlertDialogActionProps & {
      class?: HTMLAttributes["class"];
      variant?: ButtonVariants["variant"];
      size?: ButtonVariants["size"];
    }
  >(),
  {
    variant: "default",
    size: "default",
  },
);

const delegatedProps = reactiveOmit(props, "class", "variant", "size");
</script>

<template>
  <AlertDialogAction
    data-slot="alert-dialog-action"
    v-bind="delegatedProps"
    :class="cn('', buttonVariants({ variant, size }), props.class)"
  >
    <slot />
  </AlertDialogAction>
</template>
