<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import { ArrowLeft, Loader2 } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import SubmissionTestResults from "./components/SubmissionTestResults.vue";
import SubmissionCodeBlock from "./components/SubmissionCodeBlock.vue";
import SubmissionPerformancePanel from "./components/SubmissionPerformancePanel.vue";
import { useSubmissionDetail } from "./composables/useSubmissionDetail";
import { createSubmission, resolveSubmissionProblemId } from "@/api/submission";
import { toast } from "vue-sonner";
const props = defineProps({
  submission: {
    type: Object as () => SubmissionRecord,
  } as const,
  statusMetaByKey: {
    type: Object as () => Record<string, SubmissionStatusMeta>,
    default: () => ({}),
  },
});

const emit = defineEmits<{
  (e: "back"): void;
  (e: "resubmitted"): void;
}>();

const { t } = useI18n();
const router = useRouter();

const {
  statusLabel,
  statusDescription,
  statusSuggestion,
  statusToneClass,
  isAccepted,
  isCompileError,
  isPending,
  isStuck,
  pendingSeconds,
  showCaseDetails,
  showVerdictMeta,
  verdictDetail,
  codeMarkdown,
  pairedDist,
  totalCount,
  highlightIndex,
  pairedMemoryDist,
  totalMemoryCount,
  memoryHighlightIndex,
} = useSubmissionDetail(
  () => props.submission,
  () => props.statusMetaByKey,
);

const isResubmitting = ref(false);

const handleResubmit = async () => {
  const sub = props.submission;
  if (!sub) return;
  const problemId = resolveSubmissionProblemId(sub);

  if (!problemId) {
    toast.error(t("problem.submissions.error.loadFailed"));
    console.error("Resubmit failed: missing valid problem_id", sub);
    return;
  }
  if (!sub.code || !sub.language) {
    toast.error(t("problem.submissions.error.loadFailed"));
    return;
  }
  isResubmitting.value = true;
  try {
    const result = await createSubmission(problemId, {
      language: sub.language,
      code: sub.code,
    });
    toast.success(`${t("problem.editor.submit")} ${result.status}!`);
    emit("resubmitted");
  } catch (e) {
    toast.error(t("problem.problemList.messages.saveFailed"));
    console.error(e);
  } finally {
    isResubmitting.value = false;
  }
};

const handleWriteSolution = () => {
  if (props.submission?.id) {
    router.push({
      name: "solution-create-from-submission",
      query: { submissionId: props.submission.id },
    });
  }
};
</script>

<template>
  <div
    v-if="props.submission"
    class="mx-auto flex w-full max-w-[700px] flex-col gap-4 px-3 py-2"
  >
    <!-- Header -->
    <div class="flex w-full items-center justify-between gap-3">
      <div class="flex flex-1 flex-col items-start gap-0.5 overflow-hidden">
        <div class="flex items-center gap-2 mb-1">
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 rounded-none hover:bg-muted"
            @click="emit('back')"
          >
            <ArrowLeft class="h-4 w-4" />
          </Button>
          <div
            class="flex flex-1 items-center gap-1.5 text-lg font-data font-semibold uppercase leading-tight tracking-wider"
            :class="statusToneClass"
          >
            <Loader2 v-if="isPending" class="h-4 w-4 animate-spin" />
            <span data-e2e-locator="submission-result">{{ statusLabel }}</span>
            <span
              v-if="isPending && pendingSeconds > 30"
              class="text-xs font-data text-muted-foreground tabular-nums"
            >
              ({{ pendingSeconds }}s)
            </span>
          </div>
        </div>
        <div
          v-if="!isCompileError && !isPending"
          class="text-xs font-normal text-muted-foreground"
        >
          <span v-if="isAccepted">{{
            t("problem.submissions.allTestsPassed")
          }}</span>
          <span v-else class="font-data tabular-nums">
            {{
              t("problem.submissions.testsPassed", {
                count:
                  props.submission?.tests?.filter(
                    (tc) => tc.status === "Accepted",
                  ).length ?? 0,
                total: props.submission?.tests?.length ?? 0,
              })
            }}
          </span>
        </div>
        <div class="flex items-center gap-2 mt-2 text-xs text-muted-foreground">
          <div class="flex items-center gap-1">
            <Avatar class="h-4 w-4 rounded-none">
              <AvatarImage
                class="rounded-none"
                :src="
                  props.submission.user?.avatar ||
                  'https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png'
                "
              />
              <AvatarFallback class="rounded-none">U</AvatarFallback>
            </Avatar>
            <span class="font-data font-medium text-foreground">{{
              props.submission.user?.name ||
              props.submission.user?.username ||
              "User"
            }}</span>
            <span class="text-muted-foreground/60">{{
              t("problem.submissions.submittedAt")
            }}</span>
            <span class="font-data tabular-nums">{{
              new Date(
                props.submission.submittedAt ?? props.submission.created_at,
              ).toLocaleString()
            }}</span>
          </div>
        </div>
      </div>
      <div class="flex flex-none gap-2">
        <Button
          v-if="isAccepted"
          variant="default"
          size="sm"
          class="h-7 text-xs rounded-none bg-[var(--terminal-green)] hover:bg-[var(--terminal-green)] text-[var(--background)]"
          @click="handleWriteSolution"
        >
          {{ t("problem.solutions.writeSolution") }}
        </Button>
        <Button
          v-if="isStuck"
          variant="outline"
          size="sm"
          class="h-7 text-xs rounded-none"
          :disabled="isResubmitting"
          @click="handleResubmit"
        >
          <Loader2 v-if="isResubmitting" class="mr-1 h-3 w-3 animate-spin" />
          {{ t("problem.submissions.resubmit") }}
        </Button>
      </div>
    </div>

    <!-- Stuck pending warning -->
    <div
      v-if="isPending && pendingSeconds > 120"
      class="rounded-none border border-[var(--terminal-amber)]/30 bg-[var(--terminal-amber)]/5 px-4 py-3 text-xs text-[var(--terminal-amber)]"
    >
      {{ t("problem.submissions.stuckWarning") }}
    </div>

    <!-- Verdict info -->
    <div
      v-if="showVerdictMeta"
      class="rounded-none border border-border bg-muted/40 px-4 py-3 text-xs"
    >
      <div class="text-xs font-medium text-muted-foreground">
        {{ t("problem.submissions.verdictInfo") }}
      </div>
      <div v-if="statusDescription" class="mt-2 text-sm text-foreground">
        {{ statusDescription }}
      </div>
      <div
        v-if="verdictDetail"
        class="mt-2 rounded-none bg-muted px-3 py-2 font-data text-xs text-foreground"
      >
        {{ verdictDetail }}
      </div>
      <div v-if="statusSuggestion" class="mt-2 text-xs text-muted-foreground">
        {{ t("problem.submissions.suggestion") }}: {{ statusSuggestion }}
      </div>
    </div>

    <!-- Compile Error -->
    <div
      v-if="isCompileError"
      class="rounded-none bg-[var(--terminal-red)]/10 border border-[var(--terminal-red)]/30 p-4"
    >
      <h3 class="font-medium text-[var(--terminal-red)] text-sm mb-2">
        {{ t("problem.submissions.compileError") }}
      </h3>
      <pre
        class="whitespace-pre-wrap text-sm font-data text-[var(--terminal-red)] bg-transparent p-0"
        >{{
          props.submission.compiler_error ||
          t("problem.submissions.noErrorMessage")
        }}</pre
      >
    </div>

    <!-- Failure details -->
    <SubmissionTestResults
      v-else-if="showCaseDetails"
      :submission="props.submission"
    />

    <!-- Accepted (Charts) -->
    <SubmissionPerformancePanel
      v-else-if="isAccepted"
      :runtime-points="pairedDist"
      :total-runtime-count="totalCount"
      :runtime-highlight-index="highlightIndex"
      :memory-points="pairedMemoryDist"
      :total-memory-count="totalMemoryCount"
      :memory-highlight-index="memoryHighlightIndex"
      :runtime="props.submission?.runtime"
      :memory="props.submission?.memory"
      :runtime-percentile="props.submission?.runtimePercentile"
      :memory-percentile="props.submission?.memoryPercentile"
      :avatar-url="
        props.submission?.user?.avatar ||
        'https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png'
      "
    />

    <div
      v-else-if="!showVerdictMeta"
      class="rounded-none border border-dashed border-border bg-muted/30 px-4 py-3 text-xs text-muted-foreground"
    >
      {{ t("problem.submissions.detailsNotAvailable") }}
    </div>

    <!-- Code Section -->
    <SubmissionCodeBlock :code-markdown="codeMarkdown" />
  </div>
</template>
