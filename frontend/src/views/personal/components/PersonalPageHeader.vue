<script setup lang="ts">
import { Separator } from "@/components/ui/separator";

defineProps<{
  title: string;
  description?: string;
}>();
</script>

<template>
  <div class="space-y-4">
    <div
      class="flex flex-col md:flex-row md:items-center justify-between gap-4"
    >
      <div class="space-y-1">
        <h2 class="text-3xl font-bold tracking-tight">{{ title }}</h2>
        <p v-if="description" class="text-muted-foreground">
          {{ description }}
        </p>
      </div>
      <div v-if="$slots.actions" class="flex items-center gap-2">
        <slot name="actions" />
      </div>
    </div>
    <Separator />
  </div>
</template>
