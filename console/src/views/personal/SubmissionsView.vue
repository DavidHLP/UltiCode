<script setup lang="ts">
import { useAuthStore } from "@/stores/auth";
import { Badge } from "@/components/ui/badge";
import { onMounted, ref, computed } from "vue";
import { fetchUserSubmissions } from "@/api/submission";
import type { SubmissionRecord } from "@/types/submission";
import { formatDate } from "@/utils/datetime";
import {
  CheckCircle2,
  XCircle,
  Clock,
  ChevronRight,
  ExternalLink,
  Loader2,
  ListX,
} from "lucide-vue-next";
import { RouterLink, useRouter } from "vue-router";
import { Button } from "@/components/ui/button";
import { useI18n } from "vue-i18n";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import {
  DataTable,
  DataTableToolbar,
  type ColumnDef,
} from "@/components/common/data-table";
import type { SemanticColor } from "@/shared/badge-config/src";
import {
  getStatusColor,
  getStatusLabelI18nKey,
} from "@/shared/submission-status/src";

const { t, locale } = useI18n();
const router = useRouter();
const submissions = ref<SubmissionRecord[]>([]);
const loading = ref(true);
const hasUser = ref(false);
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

const getStatusIcon = (status: SubmissionRecord["status"]) => {
  switch (status) {
    case "Accepted":
      return CheckCircle2;
    case "Wrong Answer":
    case "Runtime Error":
    case "Compile Error":
    case "System Error":
      return XCircle;
    case "Time Limit Exceeded":
    case "Memory Limit Exceeded":
    case "Output Limit Exceeded":
    case "Presentation Error":
      return Clock;
    case "Judging":
    case "Pending":
      return Clock;
    default:
      return Clock;
  }
};

// Single SemanticColor → badge-token map for this surface, fed by the shared
// verdict→color truth so verdicts (TLE, System Error, Judging, …) no longer
// disagree with the submissions table. Covers every verdict incl. Sandbox
// Error (neutral); unknown statuses fall back to neutral.
const SEMANTIC_COLOR_CLASS: Record<SemanticColor, string> = {
  success:
    "text-[var(--terminal-green)] bg-[var(--terminal-green)]/10 border-[var(--terminal-green)]/20",
  warning:
    "text-[var(--terminal-amber)] bg-[var(--terminal-amber)]/10 border-[var(--terminal-amber)]/20",
  error:
    "text-[var(--terminal-red)] bg-[var(--terminal-red)]/10 border-[var(--terminal-red)]/20",
  info: "text-[var(--terminal-cyan)] bg-[var(--terminal-cyan)]/10 border-[var(--terminal-cyan)]/20",
  purple:
    "text-[var(--terminal-purple)] bg-[var(--terminal-purple)]/10 border-[var(--terminal-purple)]/20",
  electric:
    "text-[var(--accent-electric)] bg-[var(--accent-electric)]/10 border-[var(--accent-electric)]/20",
  neutral: "text-muted-foreground bg-muted border-muted-foreground/20",
};

const getStatusColorClass = (status: SubmissionRecord["status"]) =>
  SEMANTIC_COLOR_CLASS[getStatusColor(status)];

const getSubmissionLabel = (status: string): string => {
  const key = getStatusLabelI18nKey(status);
  return key ? t(key) : status;
};

const handleRowClick = (submission: SubmissionRecord) => {
  if (submission.problem?.slug) {
    router.push(`/problems/${submission.problem.slug}`);
  }
};

onMounted(async () => {
  try {
    const userId = useAuthStore().fetchCurrentUserId();
    if (!userId) return;
    hasUser.value = true;
    submissions.value = await fetchUserSubmissions();
  } catch (e) {
    console.error("Failed to load submissions", e);
  } finally {
    loading.value = false;
  }
});
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
      class="flex flex-col items-center justify-center py-24 border-2 border-dashed border-muted/50 bg-muted/5 rounded-none"
    >
      <p class="text-muted-foreground mb-4">
        {{ t("personal.submissions.loginToView") }}
      </p>
      <Button as-child class="px-6 h-10 font-bold">
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
        @clear="searchQuery = ''"
      />

      <DataTable
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
              class="text-2xs text-muted-foreground uppercase tracking-wider font-medium"
            >
              ID: {{ (item as SubmissionRecord).id.substring(0, 8) }}
            </span>
          </div>
        </template>

        <template #cell-status="{ item }">
          <Badge
            variant="outline"
            class="gap-1.5 py-1 px-2 font-medium rounded-none border"
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
            }}<span class="text-2xs text-muted-foreground uppercase"
              >ms</span
            >
          </div>
        </template>

        <template #cell-memory="{ item }">
          <div class="flex items-center gap-1.5 font-medium">
            {{ (item as SubmissionRecord).memory
            }}<span class="text-2xs text-muted-foreground uppercase"
              >mb</span
            >
          </div>
        </template>

        <template #cell-created_at="{ item }">
          {{
            formatDate((item as SubmissionRecord).created_at, locale)
          }}
        </template>

        <template #cell-actions>
          <Button
            variant="ghost"
            size="icon"
            class="h-8 w-8 text-muted-foreground hover:text-primary rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
          >
            <ChevronRight class="h-4 w-4" />
          </Button>
        </template>

        <template #empty>
          <div
            class="flex flex-col items-center justify-center py-24 rounded-none border-2 border-dashed border-muted/50 bg-muted/5 text-center px-6"
          >
            <div
              class="flex h-16 w-16 items-center justify-center rounded-none bg-muted/50 mb-4"
            >
              <ListX class="h-8 w-8 text-muted-foreground/50" />
            </div>
            <h3 class="text-xl font-bold">
              {{ t("personal.submissions.noSubmissions") }}
            </h3>
            <p class="mb-8 mt-2 max-w-[300px] text-sm text-muted-foreground">
              {{ t("personal.submissions.noSubmissionsDesc") }}
            </p>
            <Button class="px-8 h-10 font-bold" as-child>
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
