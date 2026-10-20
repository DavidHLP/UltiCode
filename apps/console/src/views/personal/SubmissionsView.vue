<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { Badge } from "@ulticode/design-system/ui/badge";
import { onMounted, ref, computed } from "vue";
import { fetchUserSubmissions } from "@/api/submission";
import type { SubmissionRecord } from "@/types/submission";
import { formatDate } from "@/utils/datetime";
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Clock,
  Info,
  ChevronRight,
  ExternalLink,
  Loader2,
  ListX,
} from "@lucide/vue";
import { RouterLink, useRouter } from "vue-router";
import { Button } from "@ulticode/design-system/ui/button";
import { useI18n } from "vue-i18n";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import {
  DataTable,
  DataTableToolbar,
  type ColumnDef,
} from "@/components/common/data-table";
import type { SemanticColor } from "@ulticode/badge-config";
import {
  getStatusColor,
  getStatusIconKey,
  getStatusLabelI18nKey,
} from "@ulticode/submission-status";
import type { VerdictIconKey } from "@ulticode/submission-status";

/**
 * Stable verdict→icon map for this surface. The verdict→icon-key mapping
 * lives in shared/submission-status (the single source of truth); the
 * icon-key → @lucide/vue component mapping is local because the icon
 * set is a framework choice that does not belong in the shared seam.
 */
const ICON_BY_KEY: Record<VerdictIconKey, typeof CheckCircle2> = {
  success: CheckCircle2,
  error: XCircle,
  warning: AlertTriangle,
  pending: Clock,
  neutral: Info,
};

const getStatusIcon = (status: SubmissionRecord["status"]) =>
  ICON_BY_KEY[getStatusIconKey(status)];

const { t, locale } = useI18n();
const router = useRouter();
const submissions = ref<SubmissionRecord[]>([]);
const loading = ref(true);
const hasUser = ref(false);
const loadError = ref(false);
const searchQuery = ref("");

const filteredSubmissions = computed(() => {
  if (!searchQuery.value) return submissions.value;
  const query = searchQuery.value.toLowerCase();
  return submissions.value.filter(
    (s) =>
      s.problem?.title.toLowerCase().includes(query) ||
      s.id.toLowerCase().includes(query),
  );
});

const columns = computed<ColumnDef[]>(() => [
  {
    key: "problem",
    header: t("personal.submissions.problem"),
    class: "w-[300px]",
    headerClass: "w-[300px]",
  },
  {
    key: "status",
    header: t("personal.submissions.status"),
  },
  {
    key: "language",
    header: t("personal.submissions.language"),
  },
  {
    key: "runtime",
    header: t("personal.submissions.runtime"),
  },
  {
    key: "memory",
    header: t("personal.submissions.memory"),
  },
  {
    key: "created_at",
    header: t("personal.submissions.submittedAt"),
    class: "text-right",
    headerClass: "text-right",
  },
  {
    key: "actions",
    header: "",
    class: "w-10",
    headerClass: "w-10",
  },
]);

// Single SemanticColor → badge-token map for this surface, fed by the shared
// verdict→color truth so verdicts (TLE, System Error, Judging, …) no longer
// disagree with the submissions table. Covers every verdict incl. Sandbox
// Error (neutral); unknown statuses fall back to neutral.
const SEMANTIC_COLOR_CLASS: Record<SemanticColor, string> = {
  success:
    "text-foreground bg-status-success-mark/10 border-status-success-mark/20",
  warning:
    "text-foreground bg-status-warning-mark/10 border-status-warning-mark/20",
  error: "text-foreground bg-status-error-mark/10 border-status-error-mark/20",
  info: "text-foreground bg-status-info-mark/10 border-status-info-mark/20",
  purple:
    "text-foreground bg-status-special-mark/10 border-status-special-mark/20",
  electric: "text-primary bg-primary/10 border-primary/20",
  neutral: "text-muted-foreground bg-muted border-muted-foreground/20",
};

const getStatusColorClass = (status: SubmissionRecord["status"]) =>
  SEMANTIC_COLOR_CLASS[getStatusColor(status)];

const getSubmissionLabel = (status: string): string => {
  const key = getStatusLabelI18nKey(status);
  return key ? t(key) : status;
};

const handleRowClick = (submission: SubmissionRecord) => {
  router.push({ name: "submission-detail", params: { id: submission.id } });
};

async function loadSubmissions() {
  loading.value = true;
  loadError.value = false;
  submissions.value = [];
  try {
    const userId = useAuthStore().fetchCurrentUserId();
    if (!userId) return;
    hasUser.value = true;
    submissions.value = (await fetchUserSubmissions()).items;
  } catch (e) {
    loadError.value = true;
    console.error("Failed to load submissions", e);
  } finally {
    loading.value = false;
  }
}

onMounted(loadSubmissions);
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.submissions.title')"
      :description="t('personal.submissions.subtitle')"
    >
      <template #actions>
        <Button variant="outline" size="sm" class="gap-2" as-child>
          <RouterLink to="/problemset">
            {{ t("personal.submissions.browseProblems") }}
            <ExternalLink class="h-3.5 w-3.5" />
          </RouterLink>
        </Button>
      </template>
    </PersonalPageHeader>

    <div
      v-if="loading"
      class="flex flex-col items-center justify-center py-20 gap-4"
    >
      <Loader2 class="h-10 w-10 animate-spin text-primary" />
      <p class="text-sm text-muted-foreground">
        {{ t("personal.submissions.loadingSubmissions") }}
      </p>
    </div>

    <div
      v-else-if="!hasUser"
      class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 bg-muted/5 rounded-lg"
    >
      <p class="text-muted-foreground mb-4">
        {{ t("personal.submissions.loginToView") }}
      </p>
      <Button as-child class="px-6">
        <RouterLink to="/login">{{ t("personal.profile.signIn") }}</RouterLink>
      </Button>
    </div>

    <div v-else class="space-y-4">
      <DataTableToolbar
        v-model="searchQuery"
        :placeholder="
          t('personal.submissions.searchPlaceholder', 'Search submissions...')
        "
        :show-clear="!!searchQuery"
        :clear-label="t('common.actions.clear')"
        @clear="searchQuery = ''"
      />

      <div
        v-if="loadError"
        role="alert"
        class="space-y-3 rounded-lg border bg-card p-6 text-center [overflow-wrap:anywhere]"
      >
        <p class="text-destructive">
          {{ t("personal.submissions.loadError") }}
        </p>
        <RetryButton @retry="loadSubmissions" />
      </div>

      <DataTable
        v-else
        :data="filteredSubmissions"
        :columns="columns"
        :has-more="false"
        @row-click="handleRowClick"
      >
        <template #cell-problem="{ item }">
          <div class="flex flex-col">
            <span
              class="font-medium text-foreground hover:text-primary transition-colors"
            >
              {{
                (item as SubmissionRecord).problem?.title || "Unknown Problem"
              }}
            </span>
            <span
              class="text-xs text-muted-foreground uppercase tracking-wider font-medium"
            >
              ID: {{ (item as SubmissionRecord).id.substring(0, 8) }}
            </span>
          </div>
        </template>

        <template #cell-status="{ item }">
          <Badge
            variant="outline"
            class="gap-1.5 py-1 px-2"
            :class="getStatusColorClass((item as SubmissionRecord).status)"
          >
            <component
              :is="getStatusIcon((item as SubmissionRecord).status)"
              class="h-3.5 w-3.5"
            />
            {{ getSubmissionLabel((item as SubmissionRecord).status) }}
          </Badge>
        </template>

        <template #cell-language="{ item }">
          {{ (item as SubmissionRecord).language }}
        </template>

        <template #cell-runtime="{ item }">
          <div class="flex items-center gap-1.5 font-medium">
            {{ (item as SubmissionRecord).runtime
            }}<span class="text-xs text-muted-foreground uppercase">ms</span>
          </div>
        </template>

        <template #cell-memory="{ item }">
          <div class="flex items-center gap-1.5 font-medium">
            {{ (item as SubmissionRecord).memory
            }}<span class="text-xs text-muted-foreground uppercase">mb</span>
          </div>
        </template>

        <template #cell-created_at="{ item }">
          {{ formatDate((item as SubmissionRecord).created_at, locale) }}
        </template>

        <template #cell-actions>
          <Button
            variant="ghost"
            size="icon"
            class="w-8 group-hover:opacity-100"
            :aria-label="t('common.actions.view')"
          >
            <ChevronRight class="h-4 w-4" />
          </Button>
        </template>

        <template #empty>
          <div
            class="flex flex-col items-center justify-center py-24 rounded-lg border-2 border-dashed border-muted/50 bg-muted/5 text-center px-6"
          >
            <div
              class="flex h-16 w-16 items-center justify-center rounded-lg bg-muted/50 mb-4"
            >
              <ListX class="h-8 w-8 text-muted-foreground/50" />
            </div>
            <h3 class="text-xl font-bold">
              {{
                searchQuery
                  ? t("common.search.noResults", { query: searchQuery })
                  : t("personal.submissions.noSubmissions")
              }}
            </h3>
            <p
              v-if="!searchQuery"
              class="mb-8 mt-2 max-w-[300px] text-sm text-muted-foreground"
            >
              {{ t("personal.submissions.noSubmissionsDesc") }}
            </p>
            <Button v-if="!searchQuery" class="px-8" as-child>
              <RouterLink to="/problemset">{{
                t("personal.submissions.startSolving")
              }}</RouterLink>
            </Button>
          </div>
        </template>
      </DataTable>
    </div>
  </PersonalPageShell>
</template>
