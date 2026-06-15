<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { Loader2 } from "lucide-vue-next";
import { fetchSubmission, fetchSubmissionStatuses } from "@/api/submission";
import { useErrorHandler } from "@/composables/useErrorHandler";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import SubmissionsDetail from "@/views/problems/submissions/SubmissionsDetail.vue";

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const { handleError } = useErrorHandler();

const submission = ref<SubmissionRecord | null>(null);
const statusMeta = ref<SubmissionStatusMeta[]>([]);
const isLoading = ref(true);
const notFound = ref(false);
const forbidden = ref(false);

const statusMetaByKey = computed<Record<string, SubmissionStatusMeta>>(() =>
  statusMeta.value.reduce(
    (acc, meta) => {
      acc[meta.key] = meta;
      return acc;
    },
    {} as Record<string, SubmissionStatusMeta>,
  ),
);

const submissionId = computed(() => {
  const raw = route.params.id;
  return Array.isArray(raw) ? raw[0] : raw;
});

const loadData = async (id: string) => {
  isLoading.value = true;
  notFound.value = false;
  forbidden.value = false;
  submission.value = null;
  try {
    const [submissionResult, statusesResult] = await Promise.all([
      fetchSubmission(id),
      statusMeta.value.length
        ? Promise.resolve(statusMeta.value)
        : fetchSubmissionStatuses(),
    ]);
    submission.value = submissionResult;
    if (!statusMeta.value.length) {
      statusMeta.value = statusesResult;
    }
  } catch (error) {
    // Extract HTTP status from axios-shaped error or other shapes.
    const status =
      (error as { response?: { status?: number } } | null)?.response?.status ??
      (error as { status?: number } | null)?.status;
    if (status === 404) {
      notFound.value = true;
    } else if (status === 403) {
      // Don't reveal whether the submission exists for non-owners.
      forbidden.value = true;
    } else if (status === 401) {
      // Route has requiresAuth, so this should be rare; redirect defensively.
      router.push({ name: "login", query: { redirect: route.fullPath } });
    } else {
      handleError(error, {
        fallbackMessage: "problem.submissions.error.loadFailed",
        logToConsole: true,
      });
    }
  } finally {
    isLoading.value = false;
  }
};

watch(
  submissionId,
  (id) => {
    if (id) {
      void loadData(id);
    }
  },
  { immediate: true },
);

function handleBack() {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push({ name: "personal-submissions" });
  }
}
</script>

<template>
  <div class="flex flex-col gap-4 p-4">
    <div
      v-if="isLoading"
      class="flex h-full min-h-[200px] items-center justify-center p-8"
    >
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>
    <div
      v-else-if="notFound"
      class="flex h-full min-h-[200px] flex-col items-center justify-center gap-2 p-8 text-muted-foreground"
    >
      <p class="text-lg font-medium">
        {{ t("personal.submissions.notFound") }}
      </p>
      <button
        type="button"
        class="text-sm text-primary underline-offset-4 hover:underline"
        @click="handleBack"
      >
        {{ t("personal.submissions.goBack") }}
      </button>
    </div>
    <div
      v-else-if="forbidden"
      class="flex h-full min-h-[200px] flex-col items-center justify-center gap-2 p-8 text-muted-foreground"
    >
      <p class="text-lg font-medium">
        {{ t("problem.submissions.error.forbidden") }}
      </p>
      <button
        type="button"
        class="text-sm text-primary underline-offset-4 hover:underline"
        @click="handleBack"
      >
        {{ t("personal.submissions.goBack") }}
      </button>
    </div>
    <SubmissionsDetail
      v-else-if="submission"
      :submission="submission"
      :status-meta-by-key="statusMetaByKey"
      @back="handleBack"
    />
  </div>
</template>
