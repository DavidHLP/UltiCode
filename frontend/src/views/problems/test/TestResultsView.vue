<script setup lang="ts">
import { computed, watch } from "vue";
import { CheckCircle2, Circle, ScrollText, XCircle } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
} from "@/components/ui/empty";
import { useBottomPanelStore } from "./test";
import { useI18n } from "vue-i18n";
import type {
  ProblemCaseResultDetail,
  ProblemRunResult,
} from "@/types/test-results";

const props = defineProps<{
  runResult: ProblemRunResult | null;
}>();

const { t } = useI18n();
const { activeCaseLabel } = useBottomPanelStore();

const cases = computed<ProblemCaseResultDetail[]>(
  () => props.runResult?.cases ?? [],
);

watch(
  () => cases.value,
  (list) => {
    if (!list || !list.length) {
      activeCaseLabel.value = null;
      return;
    }
    const first = list[0];
    if (!activeCaseLabel.value && first) {
      activeCaseLabel.value = first.caseLabel;
    }
  },
  { immediate: true, deep: true },
);

const activeResult = computed<ProblemCaseResultDetail | undefined>(() => {
  if (!cases.value.length) return undefined;
  if (!activeCaseLabel.value) return cases.value[0];
  return (
    cases.value.find((r) => r.caseLabel === activeCaseLabel.value) ??
    cases.value[0]
  );
});

const verdictLabel = computed(() => {
  const verdict = props.runResult?.verdict;
  if (!verdict) return t("problem.layout.noVerdict");
  switch (verdict) {
    case "Accepted":
      return t("problem.status.solved");
    case "Wrong Answer":
      return t("problem.status.attempted"); // or map specifically
    default:
      return verdict; // maybe add more mappings if needed
  }
});

const verdictClass = computed(() => {
  const verdict = props.runResult?.verdict;
  if (!verdict) return "text-muted-foreground";
  switch (verdict) {
    case "Accepted":
      return "text-emerald-600 dark:text-emerald-400";
    case "Wrong Answer":
      return "text-red-600 dark:text-red-400";
    case "Runtime Error":
      return "text-amber-600 dark:text-amber-400";
    case "Time Limit Exceeded":
      return "text-sky-600 dark:text-sky-400";
    case "Memory Limit Exceeded":
    case "Output Limit Exceeded":
    case "Presentation Error":
      return "text-amber-600 dark:text-amber-400";
    case "Compile Error":
      return "text-red-600 dark:text-red-400";
    case "System Error":
      return "text-red-600 dark:text-red-400";
    case "Judging":
      return "text-sky-600 dark:text-sky-400";
    default:
      return "text-muted-foreground";
  }
});

const caseStatusIconClass = (status: ProblemCaseResultDetail["status"]) => {
  switch (status) {
    case "Accepted":
      return "text-emerald-500";
    case "Wrong Answer":
      return "text-red-500";
    case "Runtime Error":
      return "text-amber-500";
    case "Time Limit Exceeded":
      return "text-sky-500";
    case "Memory Limit Exceeded":
    case "Output Limit Exceeded":
    case "Presentation Error":
      return "text-amber-500";
    case "Compile Error":
      return "text-red-500";
    case "System Error":
      return "text-red-500";
    case "Judging":
    case "Pending":
      return "text-muted-foreground";
    default:
      return "text-muted-foreground";
  }
};

const isFailureStatus = (status: ProblemCaseResultDetail["status"]) =>
  [
    "Wrong Answer",
    "Runtime Error",
    "Time Limit Exceeded",
    "Memory Limit Exceeded",
    "Output Limit Exceeded",
    "Compile Error",
    "Presentation Error",
    "System Error",
  ].includes(status);

const selectCase = (label: string) => {
  activeCaseLabel.value = label;
};
</script>

<template>
  <div class="flex h-full flex-col gap-4">
    <Empty
      v-if="!props.runResult"
      class="border border-border bg-muted/40 dark:bg-muted/20"
    >
      <EmptyContent>
        <EmptyMedia variant="icon">
          <ScrollText class="h-6 w-6 text-muted-foreground" />
        </EmptyMedia>
        <EmptyHeader>
          <div class="text-base font-semibold text-foreground">
            {{ t("problem.layout.noTestResults") }}
          </div>
          <EmptyDescription>{{
            t("problem.layout.runCodeToSeeResults")
          }}</EmptyDescription>
        </EmptyHeader>
      </EmptyContent>
    </Empty>

    <template v-else>
      <div class="flex items-baseline justify-between gap-3">
        <div class="flex items-center gap-2">
          <span :class="['text-base font-semibold', verdictClass]">
            {{ verdictLabel }}
          </span>
        </div>
      </div>

      <div
        v-if="
          props.runResult?.verdict === 'Compile Error' &&
          props.runResult.error_message
        "
        class="rounded-md bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-900/30 p-3 text-xs font-mono text-red-600 dark:text-red-300"
      >
        {{ props.runResult.error_message }}
      </div>

      <div v-if="cases.length" class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center gap-3">
          <Button
            v-for="result in cases"
            :key="result.id"
            :variant="
              result.caseLabel === activeCaseLabel ? 'secondary' : 'ghost'
            "
            size="sm"
            class="h-7 rounded-md px-3 text-xs font-medium"
            :class="
              result.caseLabel === activeCaseLabel
                ? 'text-foreground shadow-none'
                : 'text-muted-foreground hover:text-foreground'
            "
            @click="selectCase(result.caseLabel)"
          >
            <span class="mr-1 inline-flex items-center gap-1">
              <CheckCircle2
                v-if="result.status === 'Accepted'"
                class="h-3 w-3"
                :class="caseStatusIconClass(result.status)"
              />
              <XCircle
                v-else-if="isFailureStatus(result.status)"
                class="h-3 w-3"
                :class="caseStatusIconClass(result.status)"
              />
              <Circle
                v-else
                class="h-3 w-3"
                :class="caseStatusIconClass(result.status)"
              />
            </span>
            <span>{{ result.caseLabel }}</span>
          </Button>
        </div>

        <div v-if="activeResult" class="space-y-4 text-xs md:text-sm">
          <div class="space-y-3">
            <div class="space-y-2">
              <template v-if="activeResult.inputs?.length">
                <div
                  v-for="field in activeResult.inputs"
                  :key="field.id"
                  class="space-y-1"
                >
                  <div class="text-xs font-medium text-muted-foreground">
                    {{ field.label }} =
                  </div>
                  <Input
                    :model-value="field.value"
                    readonly
                    class="font-mono text-xs md:text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
                  />
                </div>
              </template>
              <p v-else class="text-muted-foreground">
                {{ t("problem.layout.noPredefinedInputs") }}
              </p>
            </div>

            <div class="space-y-2">
              <div class="text-xs font-medium text-muted-foreground">
                {{ t("problem.layout.output") }} =
              </div>
              <Input
                :model-value="activeResult.output"
                readonly
                class="font-mono text-xs md:text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
              />
            </div>

            <div class="space-y-2">
              <div class="text-xs font-medium text-muted-foreground">
                {{ t("problem.layout.expected") }} =
              </div>
              <Input
                :model-value="activeResult.expectedOutput"
                readonly
                class="font-mono text-xs md:text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
              />
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
