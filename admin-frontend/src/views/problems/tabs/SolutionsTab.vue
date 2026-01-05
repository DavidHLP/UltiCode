<script setup lang="ts">
import { Lightbulb } from 'lucide-vue-next'

defineProps<{
  problemId: string
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center py-16 text-center">
    <div class="w-10 h-10 rounded-full bg-muted flex items-center justify-center mb-3">
      <Lightbulb :size="20" class="text-muted-foreground" />
    </div>
    <h3 class="text-sm font-medium mb-1">Solutions Coming Soon</h3>
    <p class="text-xs text-muted-foreground max-w-xs">
      The solutions viewer will be available once the backend endpoint is implemented.
    </p>
  </div>
</template>
