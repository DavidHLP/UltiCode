<script setup lang="ts">
import { computed, ref, watch } from "vue";
import SubmissionsListView from "./SubmissionsListView.vue";
import SubmissionsDetail from "./SubmissionsDetail.vue";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import {
  fetchProblemSubmissions,
  fetchSubmissionStatuses,
} from "@/api/submission";
import { fetchContestProblemSubmissions } from "@/api/contest";
import {
  fetchCurrentUserId,
  isAuthenticated as checkAuthenticated,
} from "@/utils/auth";
import { problemHooks } from "@/hooks/problem-hooks";
import { useErrorHandler } from "@/composables/useErrorHandler";

const props = defineProps<{
  problemId: number;
  contestId?: string;
}>();

const { handleError } = useErrorHandler();

const submissions = ref<SubmissionRecord[]>([]);
const isLoading = ref(true);
const selectedSubmissionId = ref<string | null>(null);
const statusMeta = ref<SubmissionStatusMeta[]>([]);

const selectedSubmission = computed(
  () =>
    submissions.value.find(
      (submission) => submission.id === selectedSubmissionId.value,
    ) ?? null,
);

const statusMetaByKey = computed<Record<string, SubmissionStatusMeta>>(() => {
  return statusMeta.value.reduce(
    (acc, meta) => {
      acc[meta.key] = meta;
      return acc;
    },
    {} as Record<string, SubmissionStatusMeta>,
  );
});

const isAuthenticated = computed(() => checkAuthenticated());

const loadStatusMeta = async () => {
  if (statusMeta.value.length) return;
  try {
    statusMeta.value = await fetchSubmissionStatuses();
  } catch (error) {
    handleError(error, {
      fallbackMessage: "problem.submissions.error.statusLoadFailed",
      logToConsole: true,
      resetState: () => {
        statusMeta.value = [];
      },
    });
  }
};

const loadSubmissions = async () => {
  if (!checkAuthenticated()) {
    isLoading.value = false;
    submissions.value = [];
    return;
  }
  isLoading.value = true;
  const userId = fetchCurrentUserId();
  await problemHooks.emit("problem:submissions:load:before", {
    problemId: props.problemId,
    userId,
  });
  try {
    submissions.value = props.contestId
      ? await fetchContestProblemSubmissions(props.contestId, props.problemId)
      : await fetchProblemSubmissions(props.problemId);
    await problemHooks.emit("problem:submissions:load:after", {
      problemId: props.problemId,
      userId,
      submissions: submissions.value,
    });
  } catch (error) {
    handleError(error, {
      fallbackMessage: "problem.submissions.error.loadFailed",
      logToConsole: true,
      resetState: () => {
        submissions.value = [];
      },
    });
    await problemHooks.emit("problem:submissions:load:error", {
      problemId: props.problemId,
      userId,
      error,
    });
  } finally {
    isLoading.value = false;
  }
};

watch(
  () => [props.problemId, props.contestId],
  () => {
    selectedSubmissionId.value = null;
    void Promise.all([loadStatusMeta(), loadSubmissions()]);
  },
  { immediate: true },
);

const handleSelect = (submission: SubmissionRecord) => {
  selectedSubmissionId.value = submission.id;
};

const handleBack = () => {
  selectedSubmissionId.value = null;
};
</script>

<template>
  <div class="flex flex-col gap-4">
    <SubmissionsDetail
      v-if="selectedSubmission"
      :submission="selectedSubmission"
      :status-meta-by-key="statusMetaByKey"
      @back="handleBack"
    />
    <SubmissionsListView
      v-else
      :submissions="submissions"
      :is-loading="isLoading"
      :is-authenticated="isAuthenticated"
      :status-meta-by-key="statusMetaByKey"
      @select="handleSelect"
    />
  </div>
</template>
