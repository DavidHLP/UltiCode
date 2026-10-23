<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@ulticode/design-system/ui/dropdown-menu";
import { ChevronDown } from "@lucide/vue";
import { computed, type Component } from "vue";

export interface CategoryOption {
  label: string;
  value: string;
  icon?: Component;
}

const props = defineProps<{
  categories: CategoryOption[];
  modelValue: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const selectedCategory = computed(() =>
  props.categories.find((category) => category.value === props.modelValue),
);
</script>

<template>
  <DropdownMenu>
    <DropdownMenuTrigger as-child>
      <Button variant="outline" class="gap-2 cursor-pointer">
        <component
          :is="selectedCategory.icon"
          v-if="selectedCategory?.icon"
          class="size-3.5 shrink-0 text-muted-foreground"
          aria-hidden="true"
        />
        {{ selectedCategory?.label || modelValue }}
        <ChevronDown
          class="size-3.5 text-muted-foreground"
          aria-hidden="true"
        />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuContent align="start" class="min-w-44">
      <DropdownMenuRadioGroup
        :model-value="modelValue"
        @update:model-value="
          typeof $event === 'string' && emit('update:modelValue', $event)
        "
      >
        <DropdownMenuRadioItem
          v-for="category in categories"
          :key="category.value"
          :value="category.value"
          class="gap-2 cursor-pointer"
        >
          <component
            :is="category.icon"
            v-if="category.icon"
            class="size-3.5 text-muted-foreground"
            aria-hidden="true"
          />
          {{ category.label }}
        </DropdownMenuRadioItem>
      </DropdownMenuRadioGroup>
    </DropdownMenuContent>
  </DropdownMenu>
</template>
