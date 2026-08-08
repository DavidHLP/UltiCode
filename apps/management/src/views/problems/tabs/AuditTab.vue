<script setup lang="ts">
import AuditLogViewer from '@/components/audit/AuditLogViewer.vue'

defineProps<{
  problemId: string
}>()
</script>

<template>
  <AuditLogViewer entity-type="PROBLEM" :entity-id="problemId" />
</template>
