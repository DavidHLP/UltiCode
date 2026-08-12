<script setup lang="ts">
import type { DialogContentEmits, DialogContentProps } from "reka-ui";
import type { HTMLAttributes } from "vue";
import { reactiveOmit } from "@vueuse/core";
import { X } from "lucide-vue-next";
import {
  DialogClose,
  DialogContent,
  DialogOverlay,
  DialogPortal,
  useForwardPropsEmits,
} from "reka-ui";
import { cn } from "@/lib/utils";

const props = defineProps<
  DialogContentProps & { class?: HTMLAttributes["class"] }
>();
const emits = defineEmits<DialogContentEmits>();

const delegatedProps = reactiveOmit(props, "class");

const forwarded = useForwardPropsEmits(delegatedProps, emits);
</script>

<template>
  <DialogPortal>
    <DialogOverlay
      class="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-overlay data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"
    >
      <DialogContent
        :class="
          cn(
            'relative z-50 grid w-full max-w-lg my-8 gap-4 rounded-xl border border-border-control bg-surface-elevated p-6 text-foreground shadow-float duration-200 md:w-full',
            props.class,
          )
        "
        v-bind="forwarded"
        @pointer-down-outside="
          (event) => {
            const originalEvent = event.detail.originalEvent;
            const target = originalEvent.target as HTMLElement;
            if (
              originalEvent.offsetX > target.clientWidth ||
              originalEvent.offsetY > target.clientHeight
            ) {
              event.preventDefault();
            }
          }
        "
      >
        <slot />

        <DialogClose
          class="text-foreground-muted hover:bg-surface-highlight hover:text-foreground-strong absolute top-4 right-4 rounded-md p-1 transition-colors focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        >
          <X class="w-4 h-4" />
          <span class="sr-only">Close</span>
        </DialogClose>
      </DialogContent>
    </DialogOverlay>
  </DialogPortal>
</template>
