<script setup lang="ts">
import type { NavigationMenuLinkEmits, NavigationMenuLinkProps } from "reka-ui";
import type { HTMLAttributes } from "vue";
import { reactiveOmit } from "@vueuse/core";
import { NavigationMenuLink, useForwardPropsEmits } from "reka-ui";
import { cn } from "@/lib/utils";

const props = defineProps<
  NavigationMenuLinkProps & { class?: HTMLAttributes["class"] }
>();
const emits = defineEmits<NavigationMenuLinkEmits>();

const delegatedProps = reactiveOmit(props, "class");
const forwarded = useForwardPropsEmits(delegatedProps, emits);
</script>

<template>
  <NavigationMenuLink
    data-slot="navigation-menu-link"
    v-bind="forwarded"
    :class="
      cn(
        'data-[active]:bg-surface-highlight data-[active]:text-foreground-strong data-[active]:font-semibold hover:bg-surface-highlight hover:text-foreground-strong focus:bg-surface-highlight focus:text-foreground-strong ring-ring/10 flex flex-col gap-1 rounded-md p-2 text-sm transition-[color,background-color,box-shadow] focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none [&_svg:not([class*=\'text-\'])]:text-muted-foreground [&_svg:not([class*=\'size-\'])]:size-4',
        props.class,
      )
    "
  >
    <slot />
  </NavigationMenuLink>
</template>
