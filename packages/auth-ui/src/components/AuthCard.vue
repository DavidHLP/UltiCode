<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@ulticode/design-system/ui/card";
defineProps<{ class?: HTMLAttributes["class"]; title?: string }>();
</script>
<template>
  <Card :class="$props.class" class="w-full max-w-md">
    <CardHeader v-if="title"
      ><CardTitle>{{ title }}</CardTitle></CardHeader
    >
    <CardContent><slot /></CardContent>
  </Card>
</template>
