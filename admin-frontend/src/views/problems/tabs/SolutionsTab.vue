<script setup lang="ts">
defineProps<{
  problemId: string
}>()
</script>

<template>
  <div class="p-4">
    <h3 class="text-lg font-semibold mb-4">Solutions</h3>
    <div class="flex flex-col items-center justify-center py-12 text-center">
      <p class="text-muted-foreground">
        Solutions view will be available once the backend endpoint is implemented.
      </p>
      <p class="text-sm text-muted-foreground mt-2">
        Add a
        <code class="bg-muted px-1 py-0.5 rounded">GET /api/admin/problems/:id/solutions</code>
        endpoint to enable this feature.
      </p>
    </div>
  </div>
</template>
