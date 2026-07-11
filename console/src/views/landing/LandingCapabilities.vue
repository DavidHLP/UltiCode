<script setup lang="ts">
import { useI18n } from "vue-i18n";
import {
  BookOpen,
  Code2,
  ListChecks,
  MessageSquare,
  Trophy,
  Users,
} from "lucide-vue-next";

const { t } = useI18n();
const capabilities = [
  { key: "editor", icon: Code2, tone: "electric" },
  { key: "judge", icon: ListChecks, tone: "green" },
  { key: "contest", icon: Trophy, tone: "amber" },
  { key: "lists", icon: BookOpen, tone: "purple" },
  { key: "solutions", icon: MessageSquare, tone: "cyan" },
  { key: "community", icon: Users, tone: "red" },
] as const;
const proofItems = [
  { key: "practice", to: { name: "problemset" } },
  { key: "contest", to: { name: "contest-list" } },
  { key: "community", to: { name: "forum-home" } },
] as const;
</script>

<template>
  <section class="border-y border-silver bg-card" aria-labelledby="proof-title">
    <div
      class="container mx-auto grid max-w-6xl md:grid-cols-[1.4fr_repeat(3,1fr)]"
    >
      <div class="proof-cell proof-cell--lead">
        <p class="section-eyebrow">{{ t("landing.social.eyebrow") }}</p>
        <h2 id="proof-title" class="mt-2 text-xl font-bold">
          {{ t("landing.social.title") }}
        </h2>
      </div>
      <RouterLink
        v-for="item in proofItems"
        :key="item.key"
        :to="item.to"
        class="proof-cell"
      >
        <strong class="font-data text-sm text-[var(--accent-electric)]">{{
          t(`landing.social.${item.key}.label`)
        }}</strong>
        <span class="mt-1 text-sm text-muted-foreground">{{
          t(`landing.social.${item.key}.desc`)
        }}</span>
        <span class="mt-3 text-xs font-bold"
          >{{ t("landing.social.verify") }} →</span
        >
      </RouterLink>
    </div>
  </section>

  <section class="judge-section text-white" aria-labelledby="features-title">
    <div class="container mx-auto max-w-6xl px-4 py-20 lg:py-28">
      <div class="grid gap-8 lg:grid-cols-[0.75fr_1.25fr] lg:items-end">
        <div>
          <p class="section-eyebrow text-[var(--terminal-cyan)]">
            {{ t("landing.feature.eyebrow") }}
          </p>
          <h2 id="features-title" class="mt-3 text-3xl font-black sm:text-5xl">
            {{ t("landing.feature.title") }}
          </h2>
        </div>
        <div
          class="judge-pipeline"
          :aria-label="t('landing.feature.pipeline.label')"
        >
          <span>{{ t("landing.feature.pipeline.source") }}</span
          ><i></i><span>{{ t("landing.feature.pipeline.compile") }}</span
          ><i></i><span>{{ t("landing.feature.pipeline.run") }}</span
          ><i></i><strong>{{ t("landing.feature.pipeline.accepted") }}</strong>
        </div>
      </div>
      <div
        class="mt-12 grid border border-white/20 sm:grid-cols-2 lg:grid-cols-3"
      >
        <article
          v-for="item in capabilities"
          :key="item.key"
          class="feature-cell"
        >
          <component :is="item.icon" class="size-6" :data-tone="item.tone" />
          <p class="mt-8 font-data text-xs text-white/50">
            {{ t(`landing.feature.${item.key}.command`) }}
          </p>
          <h3 class="mt-2 text-xl font-bold">
            {{ t(`landing.feature.${item.key}.title`) }}
          </h3>
          <p class="mt-3 text-sm leading-6 text-white/65">
            {{ t(`landing.feature.${item.key}.desc`) }}
          </p>
        </article>
      </div>
    </div>
  </section>
</template>
