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
import { Lock, ChevronRight, Target, Award, Check, AlertCircle } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ContestDetail, ContestProblemSummary } from "@/types/contest";

const props = defineProps<{
  contest: ContestDetail;
  problems: ContestProblemSummary[];
  contestId: string;
  isRegistered: boolean;
  registering: boolean;
  getDifficultyColor: (difficulty: string) => string;
  problemStatuses?: Record<number, "solved" | "attempted" | "todo">;
}>();

const emit = defineEmits<{
  (e: "register"): void;
}>();

const { t } = useI18n();

const getProblemStatus = (problemId: number) => {
  return props.problemStatuses?.[problemId] || 'todo';
};
</script>

<template>
  <Card
    id="contest-problems"
    class="border border-border bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] shadow-[var(--shadow-float)] overflow-hidden rounded-none"
  >
    <CardHeader class="pb-3 border-b border-border bg-[var(--silver-100)]/50 dark:bg-[var(--solarized-base03)]/50">
      <CardTitle
        class="text-xs font-bold font-mono uppercase tracking-widest text-[var(--solarized-base01)] dark:text-[var(--solarized-base1)]"
        >{{ t("contest.detail.challenges") }}</CardTitle
      >
    </CardHeader>
    <CardContent class="p-0">
      <div
        v-if="contest.status === 'UPCOMING'"
        class="flex flex-col items-center justify-center gap-4 px-6 py-12 text-center"
      >
        <div
          class="flex h-16 w-16 items-center justify-center rounded-none bg-[var(--silver-100)] dark:bg-[var(--solarized-base03)] border border-border text-muted-foreground"
        >
          <Lock class="h-6 w-6" />
        </div>
        <div class="space-y-2">
          <h3 class="text-base font-bold text-foreground">
            {{ t("contest.detail.problemsLocked") }}
          </h3>
          <p class="text-xs text-muted-foreground max-w-sm">
            {{ t("contest.detail.problemsUnlockHint") }}
          </p>
        </div>
        <Button
          v-if="!isRegistered"
          variant="outline"
          class="rounded-none px-6 h-9 font-bold text-xs uppercase tracking-wider border border-border hover:bg-[var(--silver-100)] cursor-pointer"
          :disabled="registering"
          @click="emit('register')"
        >
          {{ t("contest.detail.register") }}
        </Button>
      </div>
      
      <Table v-else>
        <TableHeader class="bg-[var(--silver-100)]/45 dark:bg-[var(--solarized-base03)]/45 border-b border-border/40">
          <TableRow class="hover:bg-transparent">
            <TableHead class="w-20 pl-6 font-bold font-mono text-[10px] tracking-wider uppercase text-muted-foreground h-10">#</TableHead>
            <TableHead class="font-bold font-mono text-[10px] tracking-wider uppercase text-muted-foreground h-10">{{
              t("contest.detail.problemHeaders.title")
            }}</TableHead>
            <TableHead class="w-32 font-bold font-mono text-[10px] tracking-wider uppercase text-muted-foreground h-10">{{
              t("contest.detail.problemHeaders.difficulty")
            }}</TableHead>
            <TableHead class="w-24 text-center font-bold font-mono text-[10px] tracking-wider uppercase text-muted-foreground h-10">{{
              t("contest.detail.problemHeaders.score")
            }}</TableHead>
            <TableHead class="w-32 text-center font-bold font-mono text-[10px] tracking-wider uppercase text-muted-foreground h-10">{{
              t("contest.detail.problemHeaders.acceptance")
            }}</TableHead>
            <TableHead class="w-20 pr-6 h-10"></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="problem in problems"
            :key="problem.id"
            class="group cursor-pointer hover:bg-[var(--silver-100)]/30 dark:hover:bg-[var(--solarized-base03)]/30 border-b border-border/30 last:border-b-0 transition-colors"
            @click="
              problem.slug && $router.push({
                name: 'problem-detail',
                params: { slug: problem.slug },
                query: { contestId },
              })
            "
          >
            <TableCell class="pl-6 py-3">
              <!-- Solved Status Block Indicator -->
              <div
                v-if="getProblemStatus(problem.problemId) === 'solved'"
                class="flex h-9 w-9 items-center justify-center rounded-none border font-mono text-xs font-black transition-all bg-[var(--terminal-green)]/10 text-[var(--terminal-green)] border-[var(--terminal-green)]/35 shadow-sm"
                :title="t('problem.status.solved') || 'Solved'"
              >
                <Check class="h-4.5 w-4.5 stroke-[3]" />
              </div>
              <div
                v-else-if="getProblemStatus(problem.problemId) === 'attempted'"
                class="flex h-9 w-9 items-center justify-center rounded-none border font-mono text-xs font-black transition-all bg-[var(--terminal-amber)]/10 text-[var(--terminal-amber)] border-[var(--terminal-amber)]/35 shadow-sm"
                :title="t('problem.status.attempted') || 'Attempted'"
              >
                {{ problem.problemIndex || "?" }}
              </div>
              <div
                v-else
                class="flex h-9 w-9 items-center justify-center rounded-none border font-mono text-xs font-black transition-all bg-[var(--silver-100)] dark:bg-[var(--solarized-base03)] text-muted-foreground border-border/40 group-hover:bg-[var(--solarized-base3)] dark:group-hover:bg-[var(--solarized-base02)]"
              >
                {{ problem.problemIndex || "#" }}
              </div>
            </TableCell>
            
            <TableCell class="py-3">
              <div class="space-y-0.5">
                <router-link
                  v-if="problem.slug"
                  :to="{
                    name: 'problem-detail',
                    params: { slug: problem.slug },
                    query: { contestId },
                  }"
                  class="text-[15px] font-bold text-[var(--solarized-base01)] dark:text-[var(--solarized-base1)] group-hover:text-[var(--accent-electric)] transition-colors"
                  @click.stop
                >
                  {{ problem.title }}
                </router-link>
                <span v-else class="text-[15px] font-bold text-foreground">{{ problem.title }}</span>
                <div
                  class="flex items-center gap-3 text-[10px] font-bold text-muted-foreground uppercase tracking-wider"
                >
                  <span class="flex items-center gap-1 font-mono">
                    <Target class="h-3.5 w-3.5" />
                    {{ problem.solvedCount || 0 }}
                    {{ t("problem.status.solved") }}
                  </span>
                  <span class="font-mono"
                    >{{ problem.submissionCount || 0 }}
                    {{ t("problem.detail.submissions") }}</span
                  >
                </div>
              </div>
            </TableCell>
            
            <TableCell class="py-3">
              <Badge
                variant="outline"
                :class="[
                  getDifficultyColor(problem.difficulty || 'Medium'),
                  'font-black text-[9px] uppercase tracking-wider h-5 px-2 rounded-none border-current/25 bg-current/5',
                ]"
              >
                {{
                  t(
                    `problem.difficulty.${(problem.difficulty || "medium").toLowerCase()}`,
                  )
                }}
              </Badge>
            </TableCell>
            
            <TableCell class="text-center py-3">
              <span
                class="inline-flex items-center gap-1 font-black text-sm text-[var(--terminal-amber)] font-mono"
              >
                <Award class="h-4.5 w-4.5" />
                {{ problem.score || 0 }}
              </span>
            </TableCell>
            
            <TableCell class="text-center py-3">
              <span class="text-xs font-bold text-[var(--solarized-base00)] dark:text-[var(--solarized-base0)] font-mono">
                {{ problem.acceptanceRate || "0%" }}
              </span>
            </TableCell>
            
            <TableCell class="pr-6 py-3 text-right">
              <Button
                v-if="problem.slug"
                size="icon"
                variant="ghost"
                class="h-8 w-8 rounded-none text-muted-foreground group-hover:text-[var(--accent-electric)] group-hover:bg-[var(--silver-100)]/50 dark:group-hover:bg-[var(--solarized-base03)]/50 transition-all cursor-pointer"
                @click.stop="
                  $router.push({
                    name: 'problem-detail',
                    params: { slug: problem.slug },
                    query: { contestId },
                  })
                "
              >
                <ChevronRight class="h-4.5 w-4.5" />
              </Button>
            </TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </CardContent>
  </Card>
</template>
