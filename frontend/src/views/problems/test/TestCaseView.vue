<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useDebounceFn } from "@vueuse/core";
import { X } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useBottomPanelStore } from "./test";
import { useI18n } from "vue-i18n";
import type {
  ProblemTestCase,
  ProblemTestCaseInput,
} from "@/types/problem-detail";

const props = defineProps<{
  testCases: ProblemTestCase[];
}>();

const { t } = useI18n();
const activeId = ref("");
const localCases = ref<ProblemTestCase[]>([]);

const { activeCaseLabel, updateTestCases } = useBottomPanelStore();

const generateId = (prefix: string) =>
  `${prefix}-${Math.random().toString(36).slice(2, 8)}`;

const createEmptyInputs = (
  template?: ProblemTestCase,
): ProblemTestCaseInput[] => {
  if (template?.inputs?.length) {
    return template.inputs.map((input) => ({
      ...input,
      value: "",
    }));
  }

  return [
    {
      id: generateId("input"),
      name: "input",
      fieldName: "input",
      label: "input",
      value: "",
    },
  ];
};

watch(
  () => props.testCases,
  (cases) => {
    if (!cases?.length) {
      localCases.value = [];
      return;
    }
    localCases.value = cases.map((testCase) => ({
      ...testCase,
      inputs: testCase.inputs?.map((input) => ({ ...input })) ?? [],
    }));
    updateTestCases(localCases.value);
  },
  { immediate: true },
);

// Watch structural changes (add/remove test cases)
watch(
  () => localCases.value.length,
  () => {
    updateTestCases(localCases.value);

    const firstCase = localCases.value[0];
    if (!firstCase) {
      activeId.value = "";
      return;
    }

    const exists = localCases.value.some(
      (testCase) => testCase.id === activeId.value,
    );
    if (!exists) {
      activeId.value = firstCase.id;
    }
  },
  { immediate: true },
);

// Debounced sync for input value changes
const syncInputChanges = useDebounceFn(() => {
  updateTestCases(localCases.value);
}, 500);

watch(
  () =>
    localCases.value
      .map((c) => c.inputs?.map((i) => i.value).join(","))
      .join(";"),
  syncInputChanges,
);

watch(activeCaseLabel, (newLabel) => {
  if (!newLabel || !localCases.value.length) return;
  const matched = caseTabs.value.find(
    (testCase) => testCase.displayLabel === newLabel,
  );
  if (matched && matched.id !== activeId.value) {
    activeId.value = matched.id;
  }
});

const activeCase = computed(() => {
  if (!localCases.value.length) return undefined;
  return (
    localCases.value.find((t) => t.id === activeId.value) ?? localCases.value[0]
  );
});

const caseTabs = computed(() =>
  localCases.value.map((testCase, index) => ({
    ...testCase,
    displayLabel: `${t("common.labels.example")} ${index + 1}`,
  })),
);

watch(
  () => activeCase.value?.label,
  () => {
    const tab = caseTabs.value.find((item) => item.id === activeCase.value?.id);
    activeCaseLabel.value = tab?.displayLabel ?? null;
  },
  { immediate: true },
);

const inputFields = computed(() => activeCase.value?.inputs ?? []);
const canRemoveCases = computed(() => localCases.value.length > 1);

const addCase = () => {
  const template = activeCase.value ?? localCases.value[0];
  const newId = generateId("case");

  const newCase: ProblemTestCase = {
    id: newId,
    label: "",
    explanation: template?.explanation,
    inputs: createEmptyInputs(template),
  };

  localCases.value = [...localCases.value, newCase];
  activeId.value = newId;
  updateTestCases(localCases.value);
};

const selectCase = (id: string) => {
  activeId.value = id;
};

const removeCase = (id: string) => {
  if (!canRemoveCases.value) return;
  localCases.value = localCases.value.filter((testCase) => testCase.id !== id);
  updateTestCases(localCases.value);
};

// Keyboard navigation handlers
const handleTabKeydown = (event: KeyboardEvent, id: string) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    selectCase(id);
  }
};

const handleRemoveKeydown = (event: KeyboardEvent, id: string) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    event.stopPropagation();
    removeCase(id);
  }
};

const handleAddKeydown = (event: KeyboardEvent) => {
  if (event.key === "Enter" || event.key === " ") {
    event.preventDefault();
    addCase();
  }
};
</script>

<template>
  <div class="flex h-full flex-col gap-4">
    <div class="flex flex-wrap items-center gap-3" role="tablist">
      <Button
        v-for="testCase in caseTabs"
        :key="testCase.id"
        :variant="testCase.id === activeId ? 'secondary' : 'ghost'"
        size="sm"
        class="h-7 rounded-md px-3 text-xs font-medium"
        :class="
          testCase.id === activeId
            ? 'text-foreground shadow-none'
            : 'text-muted-foreground hover:text-foreground'
        "
        role="tab"
        :aria-selected="testCase.id === activeId"
        :aria-label="testCase.displayLabel"
        tabindex="0"
        @click="selectCase(testCase.id)"
        @keydown="(e: KeyboardEvent) => handleTabKeydown(e, testCase.id)"
      >
        <span>{{ testCase.displayLabel }}</span>
        <button
          v-if="testCase.id === activeId && canRemoveCases"
          type="button"
          class="ml-1 inline-flex h-4 w-4 items-center justify-center rounded-md text-[10px] text-muted-foreground hover:text-foreground"
          :aria-label="t('problem.layout.removeTestCase') || 'Remove test case'"
          tabindex="0"
          @click.stop="removeCase(testCase.id)"
          @keydown="(e: KeyboardEvent) => handleRemoveKeydown(e, testCase.id)"
        >
          <X class="h-3 w-3" />
        </button>
      </Button>

      <Button
        variant="ghost"
        size="sm"
        class="h-7 rounded-md px-2 text-xs text-muted-foreground hover:text-foreground"
        :aria-label="t('problem.layout.addTestCase') || 'Add test case'"
        tabindex="0"
        @click="addCase"
        @keydown="handleAddKeydown"
      >
        +
      </Button>
    </div>

    <div v-if="activeCase" class="space-y-4 text-xs md:text-sm">
      <div class="space-y-2">
        <template v-if="inputFields.length">
          <div v-for="field in inputFields" :key="field.id" class="space-y-1">
            <div class="text-xs font-medium text-muted-foreground">
              {{ field.label }} =
            </div>
            <Input
              v-model="field.value"
              class="font-mono text-xs md:text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
            />
          </div>
        </template>
        <p v-else class="text-muted-foreground">
          {{ t("problem.layout.noPredefinedInputs") }}
        </p>
      </div>
    </div>
  </div>
</template>
