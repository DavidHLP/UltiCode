<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { SubmissionRecord } from "@/types/submission";

defineProps<{
  submission: SubmissionRecord;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="space-y-4">
    <div v-if="submission.input" class="space-y-1.5">
      <div class="text-xs font-medium text-muted-foreground">
        {{ t("problem.layout.input") }}
      </div>
      <div
        class="rounded-none bg-muted px-3 py-2 text-sm font-mono text-foreground"
      >
        {{ submission.input }}
      </div>
    </div>

    <div v-if="submission.output" class="space-y-1.5">
      <div class="text-xs font-medium text-muted-foreground">
        {{ t("problem.layout.output") }}
      </div>
      <div
        class="rounded-none bg-muted px-3 py-2 text-sm font-mono text-foreground"
      >
        {{ submission.output }}
      </div>
    </div>

    <div v-if="submission.expected_output" class="space-y-1.5">
      <div class="text-xs font-medium text-muted-foreground">
        {{ t("problem.layout.expected") }}
      </div>
      <div
        class="rounded-none bg-muted px-3 py-2 text-sm font-mono text-foreground"
      >
        {{ submission.expected_output }}
      </div>
    </div>
  </div>
</template>
