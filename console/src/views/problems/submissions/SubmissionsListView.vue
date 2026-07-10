<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import { Loader2, Inbox } from "lucide-vue-next";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
} from "@/components/ui/empty";
import { SemanticBadge } from "@/components/ui/terminal";
import { getStatusColor } from "@/shared/submission-status/src";

const props = defineProps<{
  submissions: SubmissionRecord[];
  isLoading: boolean;
  statusMetaByKey: Record<string, SubmissionStatusMeta>;
  isAuthenticated?: boolean;
}>();

const emit = defineEmits<{
  (e: "select", submission: SubmissionRecord): void;
}>();

const { t } = useI18n();

function getSubmissionLabel(status: string): string {
  const normalized = status.toUpperCase().replace(/\s+/g, "_");
  const map: Record<string, string> = {
    ACCEPTED: "submission.status.accepted",
    WRONG_ANSWER: "submission.status.wrongAnswer",
    TIME_LIMIT_EXCEEDED: "submission.status.timeLimitExceeded",
    MEMORY_LIMIT_EXCEEDED: "submission.status.memoryLimitExceeded",
    OUTPUT_LIMIT_EXCEEDED: "submission.status.outputLimitExceeded",
    RUNTIME_ERROR: "submission.status.runtimeError",
    COMPILE_ERROR: "submission.status.compileError",
    PRESENTATION_ERROR: "submission.status.presentationError",
    SYSTEM_ERROR: "submission.status.systemError",
    JUDGING: "submission.status.judging",
    PENDING: "submission.status.pending",
  };
  const key = map[normalized];
  return key ? t(key) : status;
}

const decoratedSubmissions = computed(() => {
  return props.submissions;
});

const handleSelect = (submission: SubmissionRecord) => {
  emit("select", submission);
};

const showLoginPrompt = computed(
  () =>
    props.isAuthenticated === false &&
    !props.isLoading &&
    props.submissions.length === 0,
);
</script>

<template>
  <div class="h-full">
    <div v-if="isLoading" class="flex h-full items-center justify-center p-8">
      <Loader2 class="h-8 w-8 animate-spin text-muted-foreground" />
    </div>
    <template v-else-if="showLoginPrompt">
      <Empty
        class="flex h-full items-center justify-center border-none bg-transparent px-6 py-8"
      >
        <EmptyContent>
          <EmptyMedia variant="icon">
            <Inbox class="h-10 w-10 text-muted-foreground" />
          </EmptyMedia>
          <EmptyHeader>
            <p class="text-base font-semibold text-foreground">
              {{ t("problem.submissions.loginRequired") }}
            </p>
            <EmptyDescription>
              {{ t("problem.submissions.loginToViewSubmissions") }}
            </EmptyDescription>
          </EmptyHeader>
        </EmptyContent>
      </Empty>
    </template>
    <template v-else>
      <Table>
        <TableCaption>{{
          decoratedSubmissions.length === 0
            ? t("problem.submissions.noSubmissionsDesc")
            : ""
        }}</TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead class="w-[140px]">
              {{ t("problem.submissions.status") }}
            </TableHead>
            <TableHead class="w-[140px]">
              {{ t("problem.submissions.language") }}
            </TableHead>
            <TableHead class="w-[120px] text-center">
              {{ t("problem.submissions.runtime") }}
            </TableHead>
            <TableHead class="w-[120px] text-center">
              {{ t("problem.submissions.memory") }}
            </TableHead>
            <TableHead>{{ t("problem.submissions.notes") }}</TableHead>
          </TableRow>
        </TableHeader>

        <TableBody>
          <template v-if="decoratedSubmissions.length">
            <TableRow
              v-for="submission in decoratedSubmissions"
              :key="submission.id"
              class="cursor-pointer transition-colors hover:bg-muted/50"
              @click="handleSelect(submission)"
            >
              <TableCell class="font-medium">
                <SemanticBadge
                  :color="getStatusColor(submission.status)"
                  :label="getSubmissionLabel(submission.status)"
                  size="xs"
                />
              </TableCell>
              <TableCell>{{ submission.language }}</TableCell>
              <TableCell class="text-center">
                {{ submission.runtime }}
              </TableCell>
              <TableCell class="text-center">
                {{ submission.memory }}
              </TableCell>
              <TableCell class="text-sm text-muted-foreground">
                {{ submission.notes || "—" }}
              </TableCell>
            </TableRow>
          </template>

          <TableRow v-else>
            <TableCell colspan="5" class="p-0">
              <Empty class="border-none bg-transparent px-6 py-8">
                <EmptyContent>
                  <EmptyMedia variant="icon">
                    <Inbox class="h-6 w-6 text-muted-foreground" />
                  </EmptyMedia>
                  <EmptyHeader>
                    <p class="text-base font-semibold text-foreground">
                      {{ t("problem.submissions.noSubmissionsTitle") }}
                    </p>
                    <EmptyDescription>
                      {{ t("problem.submissions.noSubmissionsDesc") }}
                    </EmptyDescription>
                  </EmptyHeader>
                </EmptyContent>
              </Empty>
            </TableCell>
          </TableRow>
        </TableBody>
        <TableFooter v-if="decoratedSubmissions.length">
          <TableRow>
            <TableCell colspan="4" class="text-sm font-medium">
              {{ t("personal.profile.totalProblems") }}
            </TableCell>
            <TableCell class="text-center font-bold">
              {{ decoratedSubmissions.length }}
            </TableCell>
          </TableRow>
        </TableFooter>
      </Table>
    </template>
  </div>
</template>
