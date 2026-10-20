<script setup lang="ts">
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "@ulticode/design-system/ui/card";
import { Separator } from "@ulticode/design-system/ui/separator";
defineProps<{ title?: string; showSeparator?: boolean }>();
</script>
<template>
  <Card>
    <CardHeader v-if="title"
      ><CardTitle>{{ title }}</CardTitle></CardHeader
    >
    <CardContent><slot /></CardContent>
    <Separator v-if="showSeparator" />
    <CardFooter v-if="$slots.footer"><slot name="footer" /></CardFooter>
  </Card>
</template>
