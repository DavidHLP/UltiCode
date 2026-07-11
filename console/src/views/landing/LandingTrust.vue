<script setup lang="ts">
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { ChevronDown } from "lucide-vue-next";

const { t } = useI18n();
const openFaq = ref<string | null>("free");
const faqKeys = [
  "free",
  "judge",
  "privacy",
  "school",
  "languages",
  "api",
] as const;
const changelogItems = [
  { key: "judge", to: { name: "problemset" } },
  { key: "contest", to: { name: "contest-list" } },
  { key: "community", to: { name: "forum-home" } },
] as const;
</script>

<template>
  <section
    class="border-y border-silver bg-[var(--surface-sunken)]"
    aria-labelledby="timeline-title"
  >
    <div
      class="container mx-auto grid max-w-6xl gap-12 px-4 py-20 lg:grid-cols-[0.8fr_1.2fr] lg:py-28"
    >
      <div>
        <p class="section-eyebrow">{{ t("landing.timeline.eyebrow") }}</p>
        <h2 id="timeline-title" class="mt-3 text-3xl font-black sm:text-4xl">
          {{ t("landing.timeline.title") }}
        </h2>
        <p class="mt-4 text-base leading-7 text-muted-foreground">
          {{ t("landing.timeline.subtitle") }}
        </p>
      </div>
      <ol class="border-l-2 border-[var(--accent-electric)] pl-6">
        <li
          v-for="item in changelogItems"
          :key="item.key"
          class="timeline-item"
        >
          <span class="timeline-dot"></span>
          <div class="flex flex-wrap items-center gap-3">
            <h3 class="text-lg font-bold">
              {{ t(`landing.timeline.${item.key}.title`) }}
            </h3>
            <span class="status-chip">{{
              t("landing.timeline.available")
            }}</span>
          </div>
          <p class="mt-2 text-sm leading-6 text-muted-foreground">
            {{ t(`landing.timeline.${item.key}.desc`) }}
          </p>
          <RouterLink
            :to="item.to"
            class="mt-3 inline-flex text-sm font-bold text-[var(--accent-electric)]"
            >{{ t("landing.timeline.open") }} →</RouterLink
          >
        </li>
      </ol>
    </div>
  </section>

  <section
    class="container mx-auto max-w-4xl px-4 py-20 lg:py-28"
    aria-labelledby="faq-title"
  >
    <div class="text-center">
      <p class="section-eyebrow">{{ t("landing.faq.eyebrow") }}</p>
      <h2 id="faq-title" class="mt-3 text-3xl font-black sm:text-5xl">
        {{ t("landing.faq.title") }}
      </h2>
    </div>
    <div class="mt-10 border-t border-silver">
      <article v-for="key in faqKeys" :key="key" class="border-b border-silver">
        <h3>
          <button
            class="faq-trigger"
            :aria-expanded="openFaq === key"
            :aria-controls="`faq-${key}`"
            @click="openFaq = openFaq === key ? null : key"
          >
            <span>{{ t(`landing.faq.${key}.question`) }}</span
            ><ChevronDown
              class="size-5 transition-transform"
              :class="{ 'rotate-180': openFaq === key }"
            />
          </button>
        </h3>
        <div
          v-if="openFaq === key"
          :id="`faq-${key}`"
          class="pb-6 pr-10 text-sm leading-7 text-muted-foreground"
        >
          {{ t(`landing.faq.${key}.answer`) }}
        </div>
      </article>
    </div>
  </section>
</template>
