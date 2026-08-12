<script setup lang="ts">
import type { DialogContentEmits, DialogContentProps } from "reka-ui";
import type { HTMLAttributes } from "vue";
import { reactiveOmit } from "@vueuse/core";
import { X } from "lucide-vue-next";
import {
  DialogClose,
  DialogContent,
  DialogPortal,
  useForwardPropsEmits,
} from "reka-ui";
import { cn } from "@/lib/utils";
import DialogOverlay from "./DialogOverlay.vue";

const props = defineProps<
  DialogContentProps & { class?: HTMLAttributes["class"]; hideClose?: boolean }
>();
const emits = defineEmits<DialogContentEmits>();

const delegatedProps = reactiveOmit(props, ["class", "hideClose"]);

const forwarded = useForwardPropsEmits(delegatedProps, emits);
</script>

<template>
  <DialogPortal>
    <DialogOverlay />
    <DialogContent
      data-slot="dialog-content"
      v-bind="forwarded"
      :class="
        cn(
          'bg-surface-elevated text-foreground data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 fixed top-[50%] left-[50%] z-50 grid w-full max-w-[calc(100%-2rem)] translate-x-[-50%] translate-y-[-50%] gap-4 rounded-xl border border-border-control p-6 shadow-float duration-200 sm:max-w-lg',
          props.class,
        )
      "
    >
      <slot />

      <DialogClose
        v-if="!hideClose"
        class="ring-offset-background text-foreground-muted data-[state=open]:bg-surface-highlight data-[state=open]:text-foreground-strong absolute top-4 right-4 rounded-md p-1 opacity-70 transition-opacity hover:bg-surface-highlight hover:text-foreground-strong hover:opacity-100 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:pointer-events-none [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4"
      >
        <X />
        <span class="sr-only">Close</span>
      </DialogClose>
    </DialogContent>
  </DialogPortal>
</template>
