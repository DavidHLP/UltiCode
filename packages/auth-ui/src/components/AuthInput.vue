<script setup lang="ts">
import type { HTMLAttributes, InputHTMLAttributes } from "vue";
import { computed, useId } from "vue";
import {
  Field,
  FieldLabel,
  FieldError,
} from "@ulticode/design-system/ui/field";
import { Input } from "@ulticode/design-system/ui/input";
const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes["class"];
    modelValue?: string;
    label: string;
    type?: InputHTMLAttributes["type"];
    id?: string;
    placeholder?: string;
    disabled?: boolean;
    error?: string;
    autocomplete?: string;
  }>(),
  { type: "text", placeholder: "" },
);
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
defineOptions({ inheritAttrs: false });
const autoId = useId();
const inputId = computed(() => props.id || `auth-input-${autoId}`);
</script>
<template>
  <Field
    :class="$props.class"
    :data-invalid="!!error"
    :data-disabled="disabled"
  >
    <FieldLabel :for="inputId">{{ label }}</FieldLabel>
    <Input
      v-bind="$attrs"
      :id="inputId"
      :model-value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :disabled="disabled"
      :autocomplete="autocomplete"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${inputId}-error` : undefined"
      @update:model-value="emit('update:modelValue', String($event))"
    />
    <FieldError v-if="error" :id="`${inputId}-error`">{{ error }}</FieldError>
  </Field>
</template>
