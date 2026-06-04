<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Lock, ChevronRight, Target, Award } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ContestDetail, ContestProblemSummary } from "@/types/contest";

defineProps<{
  contest: ContestDetail;
  problems: ContestProblemSummary[];
  contestId: string;
  isRegistered: boolean;
  registering: boolean;
  getDifficultyColor: (difficulty: string) => string;
}>();

const emit = defineEmits<{
  (e: "register"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <Card
    id="contest-problems"
    class="border-none shadow-sm overflow-hidden rounded-none"
  >
    <CardHeader class="pb-3 border-b bg-muted/20">
      <CardTitle
        class="text-lg font-black uppercase tracking-widest text-muted-foreground"
        >{{ t("contest.detail.challenges") }}</CardTitle
      >
    </CardHeader>
    <CardContent class="p-0">
      <div
        v-if="contest.status === 'UPCOMING'"
        class="flex flex-col items-center justify-center gap-4 px-6 py-12 text-center"
      >
        <div
          class="flex h-16 w-16 items-center justify-center rounded-none bg-muted text-muted-foreground"
        >
          <Lock class="h-7 w-7" />
        </div>
        <div class="space-y-2">
          <h3 class="text-lg font-black">
            {{ t("contest.detail.problemsLocked") }}
          </h3>
          <p class="text-sm text-muted-foreground max-w-sm">
            {{ t("contest.detail.problemsUnlockHint") }}
          </p>
        </div>
        <Button
          v-if="!isRegistered"
          variant="outline"
          class="rounded-none px-6"
          :disabled="registering"
          @click="emit('register')"
        >
          {{ t("contest.detail.register") }}
        </Button>
      </div>
      <Table v-else>
        <TableHeader class="bg-muted/50">
          <TableRow>
            <TableHead class="w-20 pl-6 font-bold">#</TableHead>
            <TableHead class="font-bold">{{
              t("contest.detail.problemHeaders.title")
            }}</TableHead>
            <TableHead class="w-32 font-bold">{{
              t("contest.detail.problemHeaders.difficulty")
            }}</TableHead>
            <TableHead class="w-24 text-center font-bold">{{
              t("contest.detail.problemHeaders.score")
            }}</TableHead>
            <TableHead class="w-32 text-center font-bold">{{
              t("contest.detail.problemHeaders.acceptance")
            }}</TableHead>
            <TableHead class="w-20 pr-6"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="problem in problems"
            :key="problem.id"
            class="group cursor-pointer hover:bg-muted/30 transition-colors"
          >
            <TableCell class="pl-6">
              <div
                class="flex h-10 w-10 items-center justify-center rounded-none bg-muted font-mono text-sm font-black text-muted-foreground group-hover:bg-primary group-hover:text-primary-foreground transition-all"
              >
                {{ problem.problemIndex || "#" }}
              </div>
            </TableCell>
            <TableCell>
              <div class="space-y-1">
                <router-link
                  v-if="problem.slug"
                  :to="{
                    name: 'problem-detail',
                    params: { slug: problem.slug },
                    query: { contestId },
                  }"
                  class="text-base font-bold hover:text-primary transition-colors"
                >
                  {{ problem.title }}
                </router-link>
                <span v-else class="text-base font-bold">{{ problem.title }}</span>
                <div
                  class="flex items-center gap-3 text-[10px] font-bold text-muted-foreground uppercase tracking-wider"
                >
                  <span class="flex items-center gap-1">
                    <Target class="h-3 w-3" />
                    {{ problem.solvedCount || 0 }}
                    {{ t("problem.status.solved") }}
                  </span>
                  <span
                    >{{ problem.submissionCount || 0 }}
                    {{ t("problem.detail.submissions") }}</span
                  >
                </div>
              </div>
            </TableCell>
            <TableCell>
              <Badge
                variant="outline"
                :class="[
                  getDifficultyColor(problem.difficulty || 'Medium'),
                  'font-black text-[10px] uppercase h-5 px-2 rounded-none border-current/20 bg-current/5',
                ]"
              >
                {{
                  t(
                    `problem.difficulty.${(problem.difficulty || "medium").toLowerCase()}`,
                  )
                }}
              </Badge>
            </TableCell>
            <TableCell class="text-center">
              <span
                class="inline-flex items-center gap-1 font-black text-[var(--terminal-amber)]"
              >
                <Award class="h-4 w-4" />
                {{ problem.score || 0 }}
              </span>
            </TableCell>
            <TableCell class="text-center">
              <span class="text-sm font-bold text-muted-foreground">
                {{ problem.acceptanceRate || "0%" }}
              </span>
            </TableCell>
            <TableCell class="pr-6">
              <Button
                v-if="problem.slug"
                size="icon"
                variant="ghost"
                class="h-8 w-8 rounded-none opacity-0 group-hover:opacity-100 transition-opacity"
                @click="
                  $router.push({
                    name: 'problem-detail',
                    params: { slug: problem.slug },
                    query: { contestId },
                  })
                "
              >
                <ChevronRight class="h-4 w-4" />
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
