<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import {
  BookOpen,
  Code2,
  ListChecks,
  MessageSquare,
  Trophy,
  Users,
} from 'lucide-vue-next';
import { landingNavItems } from '@/composables/landing/useLandingNav';
import type { CapabilityKey, Tone } from '@/types/landing';
import type { Component } from 'vue';

const { t } = useI18n();
const capabilities: ReadonlyArray<{
  key: CapabilityKey;
  tone: Tone;
  icon: Component;
}> = [
  { key: 'editor', tone: 'electric', icon: Code2 },
  { key: 'judge', tone: 'green', icon: ListChecks },
  { key: 'contest', tone: 'amber', icon: Trophy },
  { key: 'lists', tone: 'magenta', icon: BookOpen },
  { key: 'solutions', tone: 'cyan', icon: MessageSquare },
  { key: 'community', tone: 'red', icon: Users },
];
const pipelineSteps: ReadonlyArray<'source' | 'compile' | 'run' | 'accepted'> =
  ['source', 'compile', 'run', 'accepted'];
</script>

<template>
  <section class="border-y border-silver bg-card" aria-labelledby="proof-title">
    <div
      class="container mx-auto grid max-w-6xl md:grid-cols-[1.4fr_repeat(3,1fr)]"
    >
      <div class="proof-cell proof-cell--lead">
        <p class="section-eyebrow">{{ t('landing.social.eyebrow') }}</p>
        <h2 id="proof-title" class="mt-2 text-xl font-bold">
          {{ t('landing.social.title') }}
        </h2>
      </div>
      <RouterLink
        v-for="item in landingNavItems"
        :key="item.key"
        :to="item.to"
        class="proof-cell"
      >
        <strong class="font-data text-sm text-[var(--accent-electric)]">{{
          t(`${item.i18nPath}.label`)
        }}</strong>
        <span class="mt-1 text-sm text-muted-foreground">{{
          t(`${item.i18nPath}.desc`)
        }}</span>
        <span class="mt-3 text-xs font-bold"
          >{{ t('landing.social.verify') }} →</span
        >
      </RouterLink>
    </div>
  </section>

  <section class="judge-section" aria-labelledby="features-title">
    <div class="container mx-auto max-w-6xl px-4 py-20 lg:py-28">
      <div class="grid gap-8 lg:grid-cols-[0.75fr_1.25fr] lg:items-end">
        <div>
          <p class="section-eyebrow">
            {{ t('landing.feature.eyebrow') }}
          </p>
          <h2 id="features-title" class="mt-3 text-3xl font-black sm:text-5xl">
            {{ t('landing.feature.title') }}
          </h2>
        </div>
        <div
          class="judge-pipeline"
          :aria-label="t('landing.feature.pipeline.label')"
        >
          <template v-for="(step, index) in pipelineSteps" :key="step">
            <strong v-if="step === 'accepted'">{{
              t(`landing.feature.pipeline.${step}`)
            }}</strong>
            <span v-else>{{ t(`landing.feature.pipeline.${step}`) }}</span>
            <i v-if="index < pipelineSteps.length - 1"></i>
          </template>
        </div>
      </div>
      <div
        class="judge-grid mt-12 grid border sm:grid-cols-2 lg:grid-cols-3"
      >
        <article
          v-for="item in capabilities"
          :key="item.key"
          class="feature-cell"
        >
          <component
            :is="item.icon"
            class="size-6"
            :data-tone="item.tone"
          />
          <p class="feature-cell-command mt-8 font-data text-xs">
            {{ t(`landing.feature.${item.key}.command`) }}
          </p>
          <h3 class="mt-2 text-xl font-bold">
            {{ t(`landing.feature.${item.key}.title`) }}
          </h3>
          <p class="feature-cell-desc mt-3 text-sm leading-6">
            {{ t(`landing.feature.${item.key}.desc`) }}
          </p>
        </article>
      </div>
    </div>
  </section>
</template>