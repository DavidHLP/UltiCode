<script setup lang="ts">
import { computed, ref } from "vue";
import type { ProblemDetail } from "@/types/problem-detail";
import DescriptionMarkdown, {
  type ProblemDescription,
} from "./DescriptionMarkdown.vue";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Separator } from "@/components/ui/separator";
import { useI18n } from "vue-i18n";
import { SemanticBadge, DIFFICULTY_COLOR_MAP } from "@/components/ui/terminal";

const props = defineProps<{
  problem: ProblemDetail;
}>();

const { t } = useI18n();

const showTags = ref(false);
const showCompanies = ref(false);

const reactionCounts = computed(
  () =>
    props.problem.interactions?.counts ?? {
      likes: 0,
      dislikes: 0,
      favorites: 0,
    },
);

// Refs for accordion sections
const tagsSection = ref<HTMLElement | null>(null);
const companiesSection = ref<HTMLElement | null>(null);
const hintsSection = ref<HTMLElement | null>(null);
const accordionSection = ref<HTMLElement | null>(null);

/**
 * Scroll to specific accordion section
 */
const scrollToSection = (element: HTMLElement | null) => {
  element?.scrollIntoView({
    behavior: "smooth",
    block: "start",
  });
};

/**
 * Calculate acceptance rate
 */
const acceptanceRate = computed(() => {
  const accepted = reactionCounts.value.likes;
  const total = reactionCounts.value.likes + reactionCounts.value.dislikes;
  return total > 0 ? ((accepted / total) * 100).toFixed(1) : "0.0";
});

/**
 * Normalize problem detail into the structure expected by DescriptionMarkdown.
 */
const problemDescription = computed<ProblemDescription>(() => ({
  content: props.problem.content || props.problem.summary || "",
  examples: (props.problem.examples || []).map((example) => {
    const ex = example as {
      input: string;
      output: string;
      explanation?: string;
    };
    return {
      input: ex.input,
      output: ex.output,
      explanation: ex.explanation,
    };
  }),
  constraints: props.problem.constraints || [],
  followUp: props.problem.followUp,
  starterNotes: props.problem.starterNotes ?? [],
}));
</script>

<template>
  <section class="problem-description-view space-y-4">
    <section class="space-y-2.5">
      <!-- Title -->
      <h1 class="problem-description-title text-foreground">
        {{ props.problem.id }}. {{ props.problem.title }}
      </h1>

      <!-- Badges Row -->
      <div class="flex flex-wrap gap-1.5 items-center">
        <!-- Difficulty Badge -->
        <SemanticBadge
          :color="
            DIFFICULTY_COLOR_MAP[props.problem.difficulty?.toUpperCase()] ??
            'neutral'
          "
          :label="
            t(`problem.difficulty.${props.problem.difficulty.toLowerCase()}`)
          "
          size="sm"
        />

        <!-- Tags Button -->
        <button
          v-if="props.problem.tags?.length"
          class="relative inline-flex items-center justify-center px-2 py-0.5 gap-1 rounded-none border border-border cursor-pointer transition-all duration-200 text-xxs text-muted-foreground font-data hover:bg-[var(--accent-electric)] hover:text-white hover:border-[var(--accent-electric)]"
          @click="scrollToSection((tagsSection as any).$el)"
        >
          <svg
            class="h-3 w-3"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 448 512"
            fill="currentColor"
          >
            <path
              d="M197.5 32c17 0 33.3 6.7 45.3 18.7l176 176c25 25 25 65.5 0 90.5L285.3 450.7c-25 25-65.5 25-90.5 0l-176-176C6.7 262.7 0 246.5 0 229.5V80C0 53.5 21.5 32 48 32H197.5zM48 229.5c0 4.2 1.7 8.3 4.7 11.3l176 176c6.2 6.2 16.4 6.2 22.6 0L384.8 283.3c6.2-6.2 6.2-16.4 0-22.6l-176-176c-3-3-7.1-4.7-11.3-4.7H48V229.5zM112 112a32 32 0 1 1 0 64 32 32 0 1 1 0-64z"
            />
          </svg>
          <span>{{ t("problem.detail.tags") }}</span>
        </button>

        <!-- Companies Button -->
        <button
          v-if="
            Array.isArray(props.problem.companies) &&
            props.problem.companies.length
          "
          class="relative inline-flex items-center justify-center px-2 py-0.5 gap-1 rounded-none border border-border cursor-pointer transition-all duration-200 text-xxs text-[var(--terminal-amber)] font-data hover:bg-[var(--terminal-amber)] hover:text-white hover:border-[var(--terminal-amber)]"
          @click="scrollToSection((companiesSection as any).$el)"
        >
          <span>{{ t("problem.detail.companies") }}</span>
        </button>

        <!-- Hint Button -->
        <button
          v-if="props.problem.followUp || props.problem.starterNotes?.length"
          class="relative inline-flex items-center justify-center px-2 py-0.5 gap-1 rounded-none border border-border cursor-pointer transition-all duration-200 text-xxs text-muted-foreground font-data hover:bg-[var(--accent-electric)] hover:text-white hover:border-[var(--accent-electric)]"
          @click="scrollToSection((hintsSection as any)?.$el)"
        >
          <svg
            class="h-3 w-3"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 384 512"
            fill="currentColor"
          >
            <path
              d="M297.2 248.9C311.6 228.3 320 203.2 320 176c0-70.7-57.3-128-128-128S64 105.3 64 176c0 27.2 8.4 52.3 22.8 72.9c3.7 5.3 8.1 11.3 12.8 17.7l0 0c12.9 17.7 28.3 38.9 39.8 59.8c10.4 19 15.7 38.8 18.3 57.5H109c-2.2-12-5.9-23.7-11.8-34.5c-9.9-18-22.2-34.9-34.5-51.8l0 0 0 0c-5.2-7.1-10.4-14.2-15.4-21.4C27.6 247.9 16 213.3 16 176C16 78.8 94.8 0 192 0s176 78.8 176 176c0 37.3-11.6 71.9-31.4 100.3c-5 7.2-10.2 14.3-15.4 21.4l0 0 0 0c-12.3 16.8-24.6 33.7-34.5 51.8c-5.9 10.8-9.6 22.5-11.8 34.5H226.4c2.6-18.7 7.9-38.6 18.3-57.5c11.5-20.9 26.9-42.1 39.8-59.8l0 0 0 0 0 0c4.7-6.4 9-12.4 12.7-17.7zM192 128c-26.5 0-48 21.5-48 48c0 8.8-7.2 16-16 16s-16-7.2-16-16c0-44.2 35.8-80 80-80c8.8 0 16 7.2 16 16s-7.2 16-16 16zm0 384c-44.2 0-80-35.8-80-80V416H272v16c0 44.2-35.8 80-80 80z"
            />
          </svg>
          <span>{{ t("problem.detail.hints") }}</span>
        </button>
      </div>

      <!-- Tags Section (Collapsible) -->
      <section
        v-if="showTags && props.problem.tags?.length"
        class="flex flex-wrap gap-1.5"
      >
        <span
          v-for="tag in props.problem.tags || []"
          :key="tag"
          class="rounded-none bg-muted px-2.5 py-0.5 text-xxs text-foreground font-data border border-transparent hover:border-[var(--accent-electric)]/50 transition-colors"
        >
          {{ tag }}
        </span>
      </section>

      <!-- Companies Section (Collapsible) -->
      <section
        v-if="
          showCompanies &&
          Array.isArray(props.problem.companies) &&
          props.problem.companies.length
        "
        class="flex flex-wrap gap-1.5"
      >
        <span
          v-for="company in props.problem.companies"
          :key="company.id"
          class="rounded-none bg-muted px-2.5 py-0.5 text-xxs font-medium text-foreground font-data border border-transparent hover:border-[var(--terminal-amber)]/50 transition-colors"
        >
          {{ company.name }}
        </span>
      </section>

      <!-- 使用 DescriptionMarkdown 组件渲染题目描述 -->
      <DescriptionMarkdown :description="problemDescription" />

      <!-- Statistics and Accordion Section -->
      <div ref="accordionSection" class="mt-4 flex flex-col gap-3">
        <Separator />

        <!-- Acceptance Stats -->
        <div class="flex flex-wrap items-center gap-4">
          <div class="flex items-center gap-2 whitespace-nowrap">
            <div class="text-xs text-muted-foreground">
              {{ t("personal.submissions.status") }}
            </div>
            <div class="font-data tabular-nums">
              <span class="text-xs text-foreground">{{
                reactionCounts.likes.toLocaleString()
              }}</span>
              <span class="ml-0.5 text-2xs text-muted-foreground">
                /
                {{
                  (
                    reactionCounts.likes + reactionCounts.dislikes
                  ).toLocaleString()
                }}
              </span>
            </div>
          </div>
          <Separator orientation="vertical" class="h-2.5" />
          <div class="flex items-center gap-2 whitespace-nowrap">
            <div class="text-xs text-muted-foreground">
              {{ t("problem.list.acceptanceRate") }}
            </div>
            <div class="font-data tabular-nums">
              <span class="text-xs text-foreground">{{ acceptanceRate }}</span>
              <span class="ml-0.5 text-2xs text-muted-foreground">%</span>
            </div>
          </div>
        </div>

        <Separator />

        <!-- Accordion for Tags, Companies, Hints -->
        <Accordion
          type="multiple"
          class="w-full divide-y divide-border/20 border-t border-b border-border/30"
        >
          <!-- Related Tags -->
          <AccordionItem
            v-if="props.problem.tags?.length"
            ref="tagsSection"
            value="tags"
            class="border-b-0"
          >
            <AccordionTrigger
              class="text-xs hover:no-underline py-2.5 px-1 hover:bg-muted/30 transition-colors"
            >
              <div class="flex items-center gap-2 text-foreground">
                <svg
                  class="h-3.5 w-3.5 text-[var(--accent-electric)]"
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 448 512"
                  fill="currentColor"
                >
                  <path
                    d="M197.5 32c17 0 33.3 6.7 45.3 18.7l176 176c25 25 25 65.5 0 90.5L285.3 450.7c-25 25-65.5 25-90.5 0l-176-176C6.7 262.7 0 246.5 0 229.5V80C0 53.5 21.5 32 48 32H197.5zM48 229.5c0 4.2 1.7 8.3 4.7 11.3l176 176c6.2 6.2 16.4 6.2 22.6 0L384.8 283.3c6.2-6.2 6.2-16.4 0-22.6l-176-176c-3-3-7.1-4.7-11.3-4.7H48V229.5zM112 112a32 32 0 1 1 0 64 32 32 0 1 1 0-64z"
                  />
                </svg>
                <span class="font-bold uppercase tracking-wider text-xxs">{{
                  t("problem.detail.tags")
                }}</span>
              </div>
            </AccordionTrigger>
            <AccordionContent class="pb-3 pt-1">
              <div class="mt-1 flex flex-wrap gap-1.5 pl-5">
                <span
                  v-for="tag in props.problem.tags || []"
                  :key="tag"
                  class="rounded-none bg-muted px-2.5 py-0.5 text-xxs text-foreground font-data border border-transparent hover:border-[var(--accent-electric)]/50 transition-colors"
                >
                  {{ tag }}
                </span>
              </div>
            </AccordionContent>
          </AccordionItem>

          <!-- Related Companies -->
          <AccordionItem
            v-if="
              Array.isArray(props.problem.companies) &&
              props.problem.companies.length
            "
            ref="companiesSection"
            value="companies"
            class="border-b-0"
          >
            <AccordionTrigger
              class="text-xs hover:no-underline py-2.5 px-1 hover:bg-muted/30 transition-colors"
            >
              <div class="flex items-center gap-2 text-foreground">
                <span
                  class="text-[var(--terminal-amber)] font-data font-bold uppercase tracking-wider text-xxs"
                  >{{ t("problem.detail.companies") }}</span
                >
              </div>
            </AccordionTrigger>
            <AccordionContent class="pb-3 pt-1">
              <div class="mt-1 flex flex-wrap gap-1.5 pl-5">
                <span
                  v-for="company in props.problem.companies"
                  :key="company.id"
                  class="rounded-none bg-muted px-2.5 py-0.5 text-xxs font-medium text-foreground font-data border border-transparent hover:border-[var(--terminal-amber)]/50 transition-colors"
                >
                  {{ company.name }}
                </span>
              </div>
            </AccordionContent>
          </AccordionItem>

          <!-- Hints -->
          <AccordionItem
            v-if="props.problem.starterNotes?.length"
            ref="hintsSection"
            value="hints"
            class="border-b-0"
          >
            <AccordionTrigger
              class="text-xs hover:no-underline py-2.5 px-1 hover:bg-muted/30 transition-colors"
            >
              <div class="flex items-center gap-2 text-foreground">
                <svg
                  class="h-3.5 w-3.5 text-[var(--accent-electric)]"
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 384 512"
                  fill="currentColor"
                >
                  <path
                    d="M297.2 248.9C311.6 228.3 320 203.2 320 176c0-70.7-57.3-128-128-128S64 105.3 64 176c0 27.2 8.4 52.3 22.8 72.9c3.7 5.3 8.1 11.3 12.8 17.7l0 0c12.9 17.7 28.3 38.9 39.8 59.8c10.4 19 15.7 38.8 18.3 57.5H109c-2.2-12-5.9-23.7-11.8-34.5c-9.9-18-22.2-34.9-34.5-51.8l0 0 0 0c-5.2-7.1-10.4-14.2-15.4-21.4C27.6 247.9 16 213.3 16 176C16 78.8 94.8 0 192 0s176 78.8 176 176c0 37.3-11.6 71.9-31.4 100.3c-5 7.2-10.2 14.3-15.4 21.4l0 0 0 0c-12.3 16.8-24.6 33.7-34.5 51.8c-5.9 10.8-9.6 22.5-11.8 34.5H226.4c2.6-18.7 7.9-38.6 18.3-57.5c11.5-20.9 26.9-42.1 39.8-59.8l0 0 0 0 0 0c4.7-6.4 9-12.4 12.7-17.7zM192 128c-26.5 0-48 21.5-48 48c0 8.8-7.2 16-16 16s-16-7.2-16-16c0-44.2 35.8-80 80-80c8.8 0 16 7.2 16 16s-7.2 16-16 16zm0 384c-44.2 0-80-35.8-80-80V416H272v16c0 44.2-35.8 80-80 80z"
                  />
                </svg>
                <span class="font-bold uppercase tracking-wider text-xxs">{{
                  t("problem.detail.hints")
                }}</span>
              </div>
            </AccordionTrigger>
            <AccordionContent class="pb-3 pt-1">
              <ul class="mt-1 space-y-1.5 pl-5">
                <li
                  v-for="(hint, index) in props.problem.starterNotes"
                  :key="index"
                  class="text-xs text-muted-foreground flex gap-1.5 items-start"
                >
                  <span
                    class="font-bold text-[var(--accent-electric)] text-2xs bg-muted px-1 rounded-none"
                    >{{ index + 1 }}</span
                  >
                  <span class="text-foreground">{{ hint }}</span>
                </li>
              </ul>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </div>
    </section>
  </section>
</template>

<style scoped>
.problem-description-title {
  font-family: var(--uc-font-ui);
  font-size: var(--uc-text-lg);
  line-height: var(--uc-leading-snug);
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: var(--uc-tracking-normal);
}
</style>
