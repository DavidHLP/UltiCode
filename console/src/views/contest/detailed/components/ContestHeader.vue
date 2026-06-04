<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ArrowLeft, Users, PlayCircle, Trophy } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ContestDetail } from "@/types/contest";

defineProps<{
  contest: ContestDetail;
  isRegistered: boolean;
  registering: boolean;
  startingVirtual: boolean;
  virtualSessionActive: boolean;
}>();

const emit = defineEmits<{
  (e: "register"): void;
  (e: "unregister"): void;
  (e: "startVirtual"): void;
  (e: "scrollToProblems"): void;
  (e: "scrollToRanking"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="space-y-6">
    <Button
      variant="ghost"
      size="sm"
      class="gap-2"
      @click="$router.push({ name: 'contest-list' })"
    >
      <ArrowLeft class="h-4 w-4" />
      {{ t("contest.detail.backToList") }}
    </Button>

    <div
      class="flex flex-col md:flex-row md:items-center justify-between gap-6"
    >
      <div class="space-y-2 flex-1">
        <div class="flex items-center gap-3">
          <h1 class="text-4xl font-black tracking-tight">
            {{ contest.title }}
          </h1>
          <Badge
            :variant="
              contest.status === 'RUNNING'
                ? 'destructive'
                : contest.status === 'UPCOMING'
                  ? 'default'
                  : 'secondary'
            "
            class="rounded-none px-3 h-6 font-bold uppercase text-[10px] tracking-widest"
          >
            {{
              contest.status === "UPCOMING"
                ? t("contest.status.upcoming")
                : contest.status === "RUNNING"
                  ? t("contest.list.liveBadge")
                  : t("contest.status.finished")
            }}
          </Badge>
        </div>
        <p
          v-if="contest.description"
          class="text-lg text-muted-foreground max-w-3xl leading-relaxed"
        >
          {{ contest.description }}
        </p>
      </div>

      <div class="flex flex-col sm:flex-row gap-3">
        <Button
          v-if="!isRegistered && contest.status === 'UPCOMING'"
          size="lg"
          class="gap-2 rounded-none h-12 px-8 font-bold shadow-[var(--shadow-float)] shadow-primary/20"
          :disabled="registering"
          @click="emit('register')"
        >
          <Users class="h-5 w-5" />
          {{
            registering
              ? t("contest.detail.registering")
              : t("contest.detail.register")
          }}
        </Button>
        <Button
          v-else-if="isRegistered && contest.status === 'UPCOMING'"
          size="lg"
          variant="outline"
          class="gap-2 rounded-none h-12 px-8 font-bold"
          :disabled="registering"
          @click="emit('unregister')"
        >
          <Users class="h-5 w-5" />
          {{
            registering
              ? t("contest.detail.unregistering")
              : t("contest.detail.unregister")
          }}
        </Button>
        <template v-else-if="contest.status === 'RUNNING'">
          <Button
            size="lg"
            class="gap-2 rounded-none h-12 px-8 font-bold shadow-[var(--shadow-float)] shadow-primary/20"
            @click="emit('scrollToProblems')"
          >
            <PlayCircle class="h-5 w-5" />
            {{ t("contest.detail.enterContest") }}
          </Button>
          <Button
            size="lg"
            variant="outline"
            class="gap-2 rounded-none h-12 px-8 font-bold"
            @click="emit('scrollToRanking')"
          >
            <Trophy class="h-5 w-5" />
            {{ t("contest.detail.liveRanking") }}
          </Button>
        </template>
        <template v-if="contest.status === 'FINISHED'">
          <Button
            v-if="!virtualSessionActive"
            size="lg"
            class="gap-2 rounded-none h-12 px-8 font-bold shadow-[var(--shadow-float)] shadow-primary/20"
            :disabled="startingVirtual"
            @click="emit('startVirtual')"
          >
            <PlayCircle class="h-5 w-5" />
            {{
              startingVirtual
                ? t("contest.detail.starting")
                : t("contest.virtual.start")
            }}
          </Button>
          <Button
            v-else
            size="lg"
            variant="outline"
            class="gap-2 rounded-none h-12 px-8 font-bold"
            disabled
          >
            <PlayCircle class="h-5 w-5" />
            {{ t("contest.virtual.active") }}
          </Button>
        </template>
      </div>
    </div>
  </div>
</template>
