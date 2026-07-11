<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  ArrowRight,
  BriefcaseBusiness,
  GraduationCap,
  School,
  Trophy,
} from "lucide-vue-next";

const { t } = useI18n();
const cases = [
  { key: "learner", icon: GraduationCap },
  { key: "school", icon: School },
  { key: "contest", icon: Trophy },
  { key: "interview", icon: BriefcaseBusiness },
] as const;
const selected = ref<(typeof cases)[number]["key"]>("learner");
const current = computed(
  () => cases.find((item) => item.key === selected.value) ?? cases[0],
);

const handleTabKey = (
  event: KeyboardEvent,
  key: (typeof cases)[number]["key"],
) => {
  const currentIndex = cases.findIndex((item) => item.key === key);
  let nextIndex = currentIndex;
  if (event.key === "ArrowRight" || event.key === "ArrowDown")
    nextIndex = (currentIndex + 1) % cases.length;
  else if (event.key === "ArrowLeft" || event.key === "ArrowUp")
    nextIndex = (currentIndex - 1 + cases.length) % cases.length;
  else if (event.key === "Home") nextIndex = 0;
  else if (event.key === "End") nextIndex = cases.length - 1;
  else return;

  event.preventDefault();
  selected.value = cases[nextIndex].key;
  const tablist = (event.currentTarget as HTMLElement).parentElement;
  requestAnimationFrame(() =>
    tablist
      ?.querySelectorAll<HTMLButtonElement>('[role="tab"]')
      [nextIndex]?.focus(),
  );
};
</script>

<template>
  <section
    class="container mx-auto max-w-6xl px-4 py-20 lg:py-28"
    aria-labelledby="usecase-title"
  >
    <div class="max-w-2xl">
      <p class="section-eyebrow">{{ t("landing.usecase.eyebrow") }}</p>
      <h2 id="usecase-title" class="mt-3 text-3xl font-black sm:text-5xl">
        {{ t("landing.usecase.title") }}
      </h2>
      <p class="mt-4 text-base leading-7 text-muted-foreground">
        {{ t("landing.usecase.subtitle") }}
      </p>
    </div>
    <div class="mt-10 grid gap-6 lg:grid-cols-[0.65fr_1.35fr]">
      <div
        class="grid gap-2"
        role="tablist"
        :aria-label="t('landing.usecase.tabsLabel')"
      >
        <button
          v-for="item in cases"
          :id="`usecase-tab-${item.key}`"
          :key="item.key"
          role="tab"
          :aria-selected="selected === item.key"
          :tabindex="selected === item.key ? 0 : -1"
          :aria-controls="`usecase-panel-${item.key}`"
          class="usecase-tab"
          :class="{ 'usecase-tab--active': selected === item.key }"
          @click="selected = item.key"
          @keydown="handleTabKey($event, item.key)"
        >
          <component :is="item.icon" class="size-5" />
          <span>{{ t(`landing.usecase.${item.key}.label`) }}</span>
          <ArrowRight class="ml-auto size-4" />
        </button>
      </div>
      <div
        :id="`usecase-panel-${current.key}`"
        role="tabpanel"
        :aria-labelledby="`usecase-tab-${current.key}`"
        class="usecase-panel"
      >
        <p class="font-data text-xs text-[var(--accent-electric)]">
          {{ t(`landing.usecase.${current.key}.signal`) }}
        </p>
        <h3 class="mt-4 text-2xl font-bold sm:text-3xl">
          {{ t(`landing.usecase.${current.key}.title`) }}
        </h3>
        <p class="mt-4 max-w-xl text-base leading-7 text-muted-foreground">
          {{ t(`landing.usecase.${current.key}.desc`) }}
        </p>
        <ul class="mt-8 grid gap-3 sm:grid-cols-2">
          <li
            v-for="index in 2"
            :key="index"
            class="flex gap-3 border-t border-silver pt-3 text-sm"
          >
            <span class="font-data text-[var(--terminal-green)]">✓</span
            >{{ t(`landing.usecase.${current.key}.point${index}`) }}
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>
