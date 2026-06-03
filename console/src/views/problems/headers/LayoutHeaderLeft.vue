<script setup lang="ts">
import { RouterLink, useRouter } from "vue-router";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import {
  Indent,
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  Shuffle,
} from "lucide-vue-next";
import logoIcon from "@/ico/favicon.ico";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import { inject, ref, watch } from "vue";
import { fetchAdjacentProblems, fetchRandomProblem } from "@/api/problem";
import { ToggleSidePanelKey } from "../problem-context";
import { useProblemContext } from "../useProblemContext";
import { useI18n } from "vue-i18n";
import { useErrorHandler } from "@/composables/useErrorHandler";

const toggleSidePanel = inject(ToggleSidePanelKey, () => {});
const problemContext = useProblemContext();
const router = useRouter();
const { t } = useI18n();
const { handleError } = useErrorHandler();

const adj = ref<{ prev: string | null; next: string | null }>({
  prev: null,
  next: null,
});

watch(
  () => problemContext.problem.value?.id,
  async (id) => {
    if (id) {
      try {
        adj.value = await fetchAdjacentProblems(Number(id));
      } catch (e) {
        handleError(e, {
          fallbackMessage: "problem.error.adjacentLoadFailed",
          logToConsole: true,
        });
      }
    }
  },
  { immediate: true },
);

async function handleRandom() {
  try {
    const random = await fetchRandomProblem();
    if (random && random.slug) {
      router.push(`/problems/${random.slug}`);
    }
  } catch (e) {
    handleError(e, {
      fallbackMessage: "problem.error.randomLoadFailed",
      logToConsole: true,
    });
  }
}
</script>

<template>
  <div class="flex min-w-60 flex-1 items-center overflow-hidden">
    <ul class="relative ml-2.5 mr-2 flex h-10 flex-none items-center">
      <RouterLink to="/" class="mr-2 self-center">
        <div class="mb-0.5 pl-1">
          <img :src="logoIcon" alt="Ulticode" class="h-5 w-5" />
        </div>
      </RouterLink>
      <li class="h-4 w-px bg-[var(--border)]"></li>
    </ul>

    <!-- Navigation menu composite component -->
    <div
      class="flex items-center overflow-hidden rounded-none hover:bg-[var(--surface-sunken)] focus:outline-none"
    >
      <div class="relative group/nav-back flex items-center">
        <!-- Main button HoverCard - Expand panel -->
        <HoverCard :open-delay="200">
          <HoverCardTrigger as-child>
            <Button
              variant="ghost"
              class="group cursor-pointer gap-2 overflow-hidden hover:text-[var(--accent-electric)] flex items-center h-8 transition-none hover:bg-[var(--surface-sunken)] text-muted-foreground px-2 rounded-none"
              role="button"
              data-state="closed"
              @click="toggleSidePanel"
            >
              <Indent class="h-4 w-4" />
              <div class="relative flex items-center gap-1 overflow-hidden">
                <div
                  class="truncate font-medium group-hover:text-[var(--accent-electric)] text-foreground hover:text-[var(--accent-electric)]"
                >
                  {{ t("problem.layout.problemSet") }}
                </div>
              </div>
            </Button>
          </HoverCardTrigger>
          <HoverCardContent class="h-auto w-auto p-2">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.layout.expandPanel") }}
              </p>
              <KbdGroup class="text-xs">
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs"> Ctrl </Kbd>
                <span class="text-xs">+</span>
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">]</Kbd>
              </KbdGroup>
            </div>
          </HoverCardContent>
        </HoverCard>

        <!-- External link button HoverCard - Open problem set in new tab -->
        <HoverCard :open-delay="200">
          <HoverCardTrigger as-child>
            <div class="hidden group-hover/nav-back:block ml-0">
              <RouterLink
                target="_blank"
                rel="noopener noreferrer"
                class="flex-none cursor-pointer justify-center flex items-center h-6 w-6 focus:outline-none focus:ring-0 focus:ring-offset-0 rounded-none no-underline -translate-x-3 hover:bg-[var(--surface-sunken)] hover:text-foreground transition-colors duration-200"
                :to="{ name: 'problemset' }"
              >
                <ExternalLink class="h-3 w-3 text-muted-foreground" />
              </RouterLink>
            </div>
          </HoverCardTrigger>
          <HoverCardContent class="h-auto w-auto p-2">
            <p class="text-xs leading-none">
              {{ t("problem.layout.openInNewTab") }}
            </p>
          </HoverCardContent>
        </HoverCard>
      </div>
    </div>

    <Separator orientation="vertical" class="h-7 w-px flex-none bg-[var(--border)]" />

    <!-- Previous problem button with HoverCard -->
    <HoverCard :open-delay="200">
      <HoverCardTrigger as-child>
        <Button
          variant="ghost"
          size="icon"
          class="group flex-none cursor-pointer flex items-center h-8 transition-none hover:bg-[var(--surface-sunken)] text-muted-foreground w-8 focus:outline-none focus:ring-0 focus:ring-offset-0 rounded-none"
          :class="!adj.prev && 'opacity-50 pointer-events-none'"
        >
          <RouterLink
            v-if="adj.prev"
            :to="{
              name: 'problem-detail',
              params: { slug: adj.prev, tab: $route.params.tab },
            }"
            class="flex items-center justify-center w-full h-full"
          >
            <ChevronLeft class="h-4 w-4" />
          </RouterLink>
          <ChevronLeft v-else class="h-4 w-4" />
        </Button>
      </HoverCardTrigger>
      <HoverCardContent class="h-auto w-auto p-2">
        <div class="flex items-center gap-1">
          <p class="text-xs leading-none">
            {{ t("problem.layout.previousProblem") }}
          </p>
          <KbdGroup class="text-xs">
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs"> Ctrl </Kbd>
            <span class="text-xs">+</span>
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">←</Kbd>
          </KbdGroup>
        </div>
      </HoverCardContent>
    </HoverCard>

    <Separator orientation="vertical" class="h-7 w-px flex-none bg-[var(--border)]" />

    <!-- Next problem button with HoverCard -->
    <HoverCard :open-delay="200">
      <HoverCardTrigger as-child>
        <Button
          variant="ghost"
          size="icon"
          class="group flex-none cursor-pointer flex items-center h-8 transition-none hover:bg-[var(--surface-sunken)] text-muted-foreground w-8 focus:outline-none focus:ring-0 focus:ring-offset-0 rounded-none"
          :class="!adj.next && 'opacity-50 pointer-events-none'"
        >
          <RouterLink
            v-if="adj.next"
            :to="{
              name: 'problem-detail',
              params: { slug: adj.next, tab: $route.params.tab },
            }"
            class="flex items-center justify-center w-full h-full"
          >
            <ChevronRight class="h-4 w-4" />
          </RouterLink>
          <ChevronRight v-else class="h-4 w-4" />
        </Button>
      </HoverCardTrigger>
      <HoverCardContent class="h-auto w-auto p-2">
        <div class="flex items-center gap-1">
          <p class="text-xs leading-none">
            {{ t("problem.layout.nextProblem") }}
          </p>
          <KbdGroup class="text-xs">
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs"> Ctrl </Kbd>
            <span class="text-xs">+</span>
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">→</Kbd>
          </KbdGroup>
        </div>
      </HoverCardContent>
    </HoverCard>

    <Separator orientation="vertical" class="h-7 w-px flex-none bg-[var(--border)]" />

    <!-- Random problem button with HoverCard -->
    <HoverCard :open-delay="200">
      <HoverCardTrigger as-child>
        <Button
          variant="ghost"
          size="icon"
          class="flex-none cursor-pointer justify-center flex items-center h-8 transition-none hover:bg-[var(--surface-sunken)] text-muted-foreground w-8 focus:outline-none focus:ring-0 focus:ring-offset-0 rounded-none"
          @click="handleRandom"
        >
          <Shuffle class="h-4 w-4" />
        </Button>
      </HoverCardTrigger>
      <HoverCardContent class="h-auto w-auto p-2">
        <div class="flex items-center gap-1">
          <p class="text-xs leading-none">
            {{ t("problem.layout.randomProblem") }}
          </p>
          <KbdGroup class="text-xs">
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs"> Ctrl </Kbd>
            <span class="text-xs">+</span>
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">R</Kbd>
          </KbdGroup>
        </div>
      </HoverCardContent>
    </HoverCard>
  </div>
</template>
