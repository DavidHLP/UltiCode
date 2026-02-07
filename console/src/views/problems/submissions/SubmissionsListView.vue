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

const decoratedSubmissions = computed(() => {
  return props.submissions;
});

const statusClass = (status: string) => {
  const meta = props.statusMetaByKey[status];
  const severity = meta?.severity ?? meta?.category;
  switch (severity) {
    case "success":
      return "text-green-600 dark:text-green-400";
    case "error":
      return "text-red-600 dark:text-red-400";
    case "warning":
      return "text-amber-600 dark:text-amber-400";
    case "info":
      return "text-sky-600 dark:text-sky-400";
    default:
      return status === "Accepted"
        ? "text-green-600 dark:text-green-400"
        : "text-red-600 dark:text-red-400";
  }
};

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
          decoratedSubmissions.length
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
                <div :class="statusClass(submission.status)">
                  {{ submission.status }}
                </div>
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
