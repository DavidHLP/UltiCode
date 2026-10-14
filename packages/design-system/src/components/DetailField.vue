<script setup lang="ts">
import {
  Field,
  FieldDescription,
  FieldLabel,
} from "@ulticode/design-system/ui/field";
withDefaults(
  defineProps<{
    label: string;
    value?: string | number | null;
    hint?: string;
    mono?: boolean;
    size?: "sm" | "md" | "lg";
  }>(),
  { mono: false, size: "md" },
);
</script>
<template>
  <Field>
    <FieldLabel>{{ label }}</FieldLabel>
    <div
      :class="{
        'font-mono tabular-nums': mono,
        'text-xs': size === 'sm',
        'text-lg': size === 'lg',
      }"
    >
      <slot>{{ value ?? "—" }}</slot>
    </div>
    <FieldDescription v-if="hint">{{ hint }}</FieldDescription>
  </Field>
</template>
