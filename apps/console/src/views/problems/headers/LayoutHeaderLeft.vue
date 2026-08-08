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
  ArrowLeft,
} from "lucide-vue-next";
import logoIcon from "@/ico/favicon.ico";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import { computed, inject, ref, watch } from "vue";
import { fetchAdjacentProblems, fetchRandomProblem } from "@/api/problem";
import {
  ContestProblemContextKey,
  ToggleSidePanelKey,
} from "../problem-context";
import { useProblemContext } from "../useProblemContext";
import { useI18n } from "vue-i18n";
import { useErrorHandler } from "@/composables/useErrorHandler";
import ContestProblemNotInContest from "../components/ContestProblemNotInContest.vue";

const toggleSidePanel = inject(ToggleSidePanelKey, () => {});
// Contest mode may or may not be active on this page. The provider
// (ProblemDetailView) only sets it when `route.query.contestId` is
// present, so the default no-op keeps non-contest pages compiling and
// behaving exactly as before.
const contestCtx = inject(ContestProblemContextKey, null);

const problemContext = useProblemContext();
const router = useRouter();
const { t } = useI18n();
const { handleError } = useErrorHandler();

// ---------------------------------------------------------------------------
// Adjacency — site-wide by default, contest-scoped when in a contest.
//
// We keep two separate refs and switch the rendered link targets via
// `isContestMode` instead of trying to overload a single ref. The site-wide
// fetch is gated on `!isContestMode` so we don't pay for an HTTP call we'll
// never use.
// ---------------------------------------------------------------------------
const adj = ref<{ prev: string | null; next: string | null }>({
  prev: null,
  next: null,
});

const isContestMode = computed(() => contestCtx?.isInContest.value === true);

// Contest-scoped prev/next slugs come from the inject context; the
// composable re-derives them whenever `problem.id` or the contest's
// problem list changes.
const contestAdj = computed(() => {
  if (!contestCtx) return { prev: null as string | null, next: null as string | null };
  const { prev, next } = contestCtx.contestProblemNav.value;
  return {
    prev: prev?.slug ?? null,
    next: next?.slug ?? null,
  };
});

const prevSlug = computed(() =>
  isContestMode.value ? contestAdj.value.prev : adj.value.prev,
);
const nextSlug = computed(() =>
  isContestMode.value ? contestAdj.value.next : adj.value.next,
);

// Problem-belongs-to-contest guard. `null` while loading; `false` only
// after we've actually fetched the contest's problem list and the
// current problem is not in it.
const showNotInContest = computed(() => {
  if (!contestCtx) return false;
  if (!isContestMode.value) return false;
  return contestCtx.problemBelongsToContest.value === false;
});

const contestForGuard = computed(() => contestCtx?.contest.value ?? null);

// Site-wide adjacent fetch is only needed when we're NOT in contest mode.
watch(
  [() => problemContext.problem.value?.id, isContestMode],
  async ([id, inContest]) => {
    if (inContest) return; // contest path uses local computation
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

    <!--
      Contest mode: replace the "题库" / external problemset block with a
      single "返回比赛" link. Clicking always jumps to the contest's
      detail page (using the URL-stable slug, never the DB id).

      Non-contest mode: keep the existing "expand side panel" button
      and the hover-revealed "open problemset in new tab" link.
    -->
    <template v-if="isContestMode && contestCtx?.contest.value">
      <RouterLink
        :to="`/contest/${contestCtx.contest.value.slug}`"
        class="header-btn px-2.5 gap-1.5"
        :data-testid="'back-to-contest-header-link'"
      >
        <ArrowLeft class="h-4 w-4" />
        <span class="truncate">{{ t("contest.detail.backToContest") }}</span>
      </RouterLink>
    </template>
    <template v-else>
      <div
        class="flex items-center overflow-hidden rounded-none focus:outline-none"
      >
        <div class="relative group/nav-back flex items-center">
          <!-- Main button HoverCard - Expand panel -->
          <HoverCard :open-delay="200">
            <HoverCardTrigger as-child>
              <Button
                class="header-btn px-2.5 gap-1.5"
                role="button"
                data-state="closed"
                @click="toggleSidePanel"
              >
                <Indent class="h-4 w-4" />
                <span class="truncate">{{ t("problem.layout.problemSet") }}</span>
              </Button>
            </HoverCardTrigger>
            <HoverCardContent class="h-auto w-auto p-2 rounded-none">
              <div class="flex items-center gap-1">
                <p class="text-xs leading-none">
                  {{ t("problem.layout.expandPanel") }}
                </p>
                <KbdGroup class="text-xs">
                  <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none">
                    Ctrl
                  </Kbd>
                  <span class="text-xs">+</span>
                  <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none"
                    >]</Kbd
                  >
                </KbdGroup>
              </div>
            </HoverCardContent>
          </HoverCard>

          <!-- External link button HoverCard - Open problem set in new tab -->
          <HoverCard :open-delay="200">
            <HoverCardTrigger as-child>
              <!--
                RouterLink must be the direct child of HoverCardTrigger so the
                hover trigger forwards its handlers to the underlying <a>
                element. Previously a wrapper <div> sat between them, which
                swallowed the click before it reached the link, so the
                "在新标签页中打开题库" button did nothing.
              -->
              <RouterLink
                target="_blank"
                rel="noopener noreferrer"
                class="hidden group-hover/nav-back:flex -translate-x-3 h-6 w-6 flex-none cursor-pointer items-center justify-center rounded-none text-muted-foreground no-underline transition-colors duration-200 hover:bg-[var(--silver-200)]/30 hover:text-[var(--solarized-base02)] focus:outline-none focus:ring-0 focus:ring-offset-0 dark:hover:text-[var(--solarized-base3)]"
                :to="{ name: 'problemset' }"
              >
                <ExternalLink class="h-3 w-3 text-current" />
              </RouterLink>
            </HoverCardTrigger>
            <HoverCardContent class="h-auto w-auto p-2 rounded-none">
              <p class="text-xs leading-none">
                {{ t("problem.layout.openInNewTab") }}
              </p>
            </HoverCardContent>
          </HoverCard>
        </div>
      </div>
    </template>

    <Separator
      orientation="vertical"
      class="h-7 w-px flex-none bg-[var(--border)]"
    />

    <!-- Previous problem button with HoverCard -->
    <HoverCard :open-delay="200">
      <HoverCardTrigger as-child>
        <Button class="header-btn w-8 p-0" :disabled="!prevSlug">
          <RouterLink
            v-if="prevSlug"
            :to="{
              name: 'problem-detail',
              params: { slug: prevSlug, tab: $route.params.tab },
              // Preserve the query string (e.g. ?contestId=...) so that
              // navigating to a sibling problem keeps the contest
              // context intact and the hidden Solutions tab stays
              // hidden. In contest mode the prev/next slugs already
              // belong to the same contest, so the query is the
              // *same* contestId; the guard then succeeds on the
              // next page.
              query: $route.query,
            }"
            class="flex items-center justify-center w-full h-full text-current"
          >
            <ChevronLeft class="h-4 w-4" />
          </RouterLink>
          <ChevronLeft v-else class="h-4 w-4" />
        </Button>
      </HoverCardTrigger>
      <HoverCardContent class="h-auto w-auto p-2 rounded-none">
        <div class="flex items-center gap-1">
          <p class="text-xs leading-none">
            {{ t("problem.layout.previousProblem") }}
          </p>
          <KbdGroup class="text-xs">
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none">
              Ctrl
            </Kbd>
            <span class="text-xs">+</span>
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none">←</Kbd>
          </KbdGroup>
        </div>
      </HoverCardContent>
    </HoverCard>

    <Separator
      orientation="vertical"
      class="h-7 w-px flex-none bg-[var(--border)]"
    />

    <!-- Next problem button with HoverCard -->
    <HoverCard :open-delay="200">
      <HoverCardTrigger as-child>
        <Button class="header-btn w-8 p-0" :disabled="!nextSlug">
          <RouterLink
            v-if="nextSlug"
            :to="{
              name: 'problem-detail',
              params: { slug: nextSlug, tab: $route.params.tab },
              // Preserve the query string (e.g. ?contestId=...) so that
              // navigating to a sibling problem keeps the contest
              // context intact and the hidden Solutions tab stays
              // hidden.
              query: $route.query,
            }"
            class="flex items-center justify-center w-full h-full text-current"
          >
            <ChevronRight class="h-4 w-4" />
          </RouterLink>
          <ChevronRight v-else class="h-4 w-4" />
        </Button>
      </HoverCardTrigger>
      <HoverCardContent class="h-auto w-auto p-2 rounded-none">
        <div class="flex items-center gap-1">
          <p class="text-xs leading-none">
            {{ t("problem.layout.nextProblem") }}
          </p>
          <KbdGroup class="text-xs">
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none">
              Ctrl
            </Kbd>
            <span class="text-xs">+</span>
            <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none">→</Kbd>
          </KbdGroup>
        </div>
      </HoverCardContent>
    </HoverCard>

    <!--
      Random problem button: only rendered outside contest mode.

      Rationale: in a running contest, "random" can land on a problem
      outside the contest set, which the user might then mistakenly
      submit to the contest (or vice versa). The header is the most
      discoverable place for the contest page; the user has the
      "返回比赛" button to recover. Hiding the icon (vs. disabling
      it) keeps the bar visually consistent.
    -->
    <template v-if="!isContestMode">
      <Separator
        orientation="vertical"
        class="h-7 w-px flex-none bg-[var(--border)]"
      />
      <HoverCard :open-delay="200">
        <HoverCardTrigger as-child>
          <Button class="header-btn w-8 p-0" @click="handleRandom">
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
    </template>

    <!--
      Inline contest error banner. Shown in the *header* area (this is
      the most consistent place across breakpoints) when the user
      lands on a problem that doesn't belong to the URL's contest.
      Clicking the back button returns to the contest's detail page.
    -->
    <ContestProblemNotInContest
      v-if="showNotInContest && contestForGuard"
      :contest="contestForGuard"
      class="ml-2"
    />
  </div>
</template>

