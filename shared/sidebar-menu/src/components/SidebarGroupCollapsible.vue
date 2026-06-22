<script setup lang="ts">
import { inject, ref } from 'vue'
import { CollapsibleRoot, CollapsibleContent, useForwardPropsEmits, type CollapsibleRootProps, type CollapsibleRootEmits } from 'reka-ui'

const props = withDefaults(
  defineProps<CollapsibleRootProps>(),
  { defaultOpen: true },
)

const emits = defineEmits<CollapsibleRootEmits>()

const forwarded = useForwardPropsEmits(props, emits)
</script>

<template>
  <CollapsibleRoot
    v-slot="{ open }"
    v-bind="forwarded"
    data-slot="collapsible"
    class="group/collapsible"
  >
    <slot :open="open" />
  </CollapsibleRoot>
</template>
