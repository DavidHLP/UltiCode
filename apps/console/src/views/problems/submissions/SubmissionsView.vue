<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import SubmissionsListView from "./SubmissionsListView.vue";
import SubmissionsDetail from "./SubmissionsDetail.vue";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import {
  fetchProblemSubmissions,
  fetchSubmissionStatuses,
  fetchSubmission,
} from "@/api/submission";
import { fetchContestProblemSubmissions } from "@/api/contest";
import { ApiError } from "@/utils/request";
import { useAuthStore } from "@/stores/auth";
import { problemHooks } from "@/hooks/problem-hooks";
import { useErrorHandler } from "@/composables/useErrorHandler";
import { useSocket } from "@/composables/useSocket";
import { Loader2 } from "lucide-vue-next";

const props = defineProps<{
  problemId: number;
  contestId?: string;
}>();

const { handleError } = useErrorHandler();
const { onSubmissionResult } = useSocket();
const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const submissions = ref<SubmissionRecord[]>([]);
const isLoading = ref(true);
const selectedSubmissionId = ref<string | null>(null);
const statusMeta = ref<SubmissionStatusMeta[]>([]);

const selectedSubmission = ref<SubmissionRecord | null>(null);

watch(selectedSubmissionId, async (newId) => {
  if (!newId) {
    selectedSubmission.value = null;
    return;
  }
  try {
    const detail = await fetchSubmission(newId);
    // Stale-response guard: only apply the detail when the user has not
    // selected another submission while this request was in flight.
    if (selectedSubmissionId.value !== newId) return;
    selectedSubmission.value = detail;
  } catch (error) {
    if (selectedSubmissionId.value !== newId) return;
    if (error instanceof ApiError && error.code === 404) {
      // Drop the vanished row and clear only this still-active selection;
      // never clobber a newer selection made while the request ran.
      submissions.value = submissions.value.filter(
        (submission) => submission.id !== newId,
      );
      selectedSubmissionId.value = null;
      if (route.query.submissionId === newId) {
        const query = { ...route.query };
        delete query.submissionId;
        await router.replace({ query });
      }
      console.warn(
        "[submissions] detail record disappeared from the active owner",
        { submissionId: newId },
      );
      handleError(error, {
        fallbackMessage: "problem.submissions.error.notFound",
        logToConsole: false,
      });
      return;
    }
    handleError(error, {
      fallbackMessage: "problem.submissions.error.loadFailed",
      logToConsole: true,
    });
    selectedSubmission.value = null;
  }
});

const statusMetaByKey = computed<Record<string, SubmissionStatusMeta>>(() => {
  return statusMeta.value.reduce(
    (acc, meta) => {
      acc[meta.key] = meta;
      return acc;
    },
    {} as Record<string, SubmissionStatusMeta>,
  );
});

const isAuthenticated = computed(() => authStore.isAuthenticated);

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
  if (!authStore.isAuthenticated) {
    isLoading.value = false;
    submissions.value = [];
    return;
  }
  isLoading.value = true;
  const userId = useAuthStore().fetchCurrentUserId();
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

watch(
  [() => submissions.value, () => route.query.submissionId, () => route.query.resubmit],
  ([currentSubmissions, querySubId, resubmit]) => {
    const rawSubId = Array.isArray(querySubId) ? querySubId[0] : querySubId;
    const rawResubmit = Array.isArray(resubmit) ? resubmit[0] : resubmit;

    if (rawSubId && typeof rawSubId === "string") {
      selectedSubmissionId.value = rawSubId;
    } else if (
      rawResubmit === "true" &&
      currentSubmissions &&
      currentSubmissions.length > 0 &&
      !selectedSubmissionId.value
    ) {
      const targetSub =
        currentSubmissions.find(
          (s) => s.status === "System Error" || s.status === "Sandbox Error",
        ) ?? currentSubmissions[0];
      selectedSubmissionId.value = targetSub.id;
    }
  },
  { immediate: true },
);

// WebSocket: Listen for submission results and refresh list
let unsubscribe: (() => void) | null = null;

onMounted(() => {
  unsubscribe = onSubmissionResult(async (data) => {
    // Only refresh if the result is for the current problem
    if (String(props.problemId) === data.problemId) {
      // Refresh the submissions list to get the updated result
      await loadSubmissions();
    }
  });
});

onUnmounted(() => {
  if (unsubscribe) {
    unsubscribe();
    unsubscribe = null;
  }
});

const handleSelect = (submission: SubmissionRecord) => {
  selectedSubmissionId.value = submission.id;
};

const handleBack = () => {
  selectedSubmissionId.value = null;
};

const handleResubmitted = async () => {
  selectedSubmissionId.value = null;
  if (route.query.resubmit) {
    const query = { ...route.query };
    delete query.resubmit;
    await router.replace({ query });
  }
  await loadSubmissions();
};
</script>

<template>
  <div class="flex flex-col gap-4">
    <div
      v-if="selectedSubmissionId && !selectedSubmission"
      class="flex h-full items-center justify-center p-8"
    >
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>
    <SubmissionsDetail
      v-else-if="selectedSubmission"
      :submission="selectedSubmission"
      :status-meta-by-key="statusMetaByKey"
      @back="handleBack"
      @resubmitted="handleResubmitted"
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
