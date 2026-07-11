<script setup lang="ts">
import { computed, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { useI18n } from "vue-i18n";
import {
  ArrowRight,
  CheckCircle2,
  ChevronDown,
  Code2,
  Menu,
  MessageSquare,
  Play,
  Trophy,
  X,
} from "lucide-vue-next";
import "@/assets/styles/landing.css";

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();

const TWO_SUM_SLUG = "two-sum";
const FINAL_STEP_DELAY = 1050;
const SEED_PROBLEM_STEPS = [
  { delay: 350, key: "outputCaseOne" },
  { delay: 700, key: "outputCaseTwo" },
] as const;

const codeSnippets = {
  cpp: `vector<int> twoSum(vector<int>& nums, int target) {
  unordered_map<int, int> seen;
  for (int i = 0; i < nums.size(); i++) {
    if (seen.count(target - nums[i]))
      return {seen[target - nums[i]], i};
    seen[nums[i]] = i;
  }
  return {};
}`,
  py: `def two_sum(nums, target):
    seen = {}
    for i, value in enumerate(nums):
        if target - value in seen:
            return [seen[target - value], i]
        seen[value] = i
    return []`,
  js: `function twoSum(nums, target) {
  const seen = new Map();
  for (let i = 0; i < nums.length; i++) {
    if (seen.has(target - nums[i]))
      return [seen.get(target - nums[i]), i];
    seen.set(nums[i], i);
  }
  return [];
}`,
} as const;

const mobileMenuOpen = ref(false);
const simulationTerminalText = ref<string[]>([]);
const compiling = ref(false);
const showSuccessMsg = ref(false);
const timers = new Set<ReturnType<typeof setTimeout>>();

const selectedLang = ref<keyof typeof codeSnippets>("cpp");
const code = computed(() => codeSnippets[selectedLang.value]);

const redirectTo = (guestRoute: string) =>
  router.push({ name: authStore.isAuthenticated ? "forum-home" : guestRoute });

const goToSeedProblem = () =>
  router.push(
    authStore.isAuthenticated
      ? { name: "forum-home" }
      : { name: "problem-detail", params: { slug: TWO_SUM_SLUG } },
  );

const prefersReducedMotion = () =>
  typeof window !== "undefined" &&
  typeof window.matchMedia !== "undefined" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const scheduleTimer = (delay: number, action: () => void) => {
  const timer = setTimeout(() => {
    timers.delete(timer);
    action();
  }, delay);
  timers.add(timer);
};

const runSimulation = () => {
  if (compiling.value) return;
  timers.forEach(clearTimeout);
  timers.clear();
  compiling.value = true;
  showSuccessMsg.value = false;

  const finishSynchronously = (lines: string[]) => {
    simulationTerminalText.value = lines;
    compiling.value = false;
    showSuccessMsg.value = true;
  };

  const baseLines = [t("landing.outputCompile")];
  const caseLines = SEED_PROBLEM_STEPS.map((step) => t(`landing.${step.key}`));
  const fullLines = [...baseLines, ...caseLines, t("landing.outputComplete")];

  if (prefersReducedMotion()) {
    finishSynchronously(fullLines);
    return;
  }

  simulationTerminalText.value = baseLines;
  for (const step of SEED_PROBLEM_STEPS) {
    scheduleTimer(step.delay, () =>
      simulationTerminalText.value.push(t(`landing.${step.key}`)),
    );
  }
  scheduleTimer(FINAL_STEP_DELAY, () => {
    simulationTerminalText.value.push(t("landing.outputComplete"));
    compiling.value = false;
    showSuccessMsg.value = true;
  });
};

const closeMobileMenu = () => {
  mobileMenuOpen.value = false;
};

onUnmounted(() => timers.forEach(clearTimeout));
</script>

<template>
  <div
    class="min-h-screen bg-background text-foreground selection:bg-[var(--accent-electric)] selection:text-white"
  >
    <header
      class="sticky top-0 z-50 border-b border-silver bg-card/95 backdrop-blur"
    >
      <div
        class="container mx-auto flex h-16 max-w-6xl items-center justify-between px-4"
      >
        <RouterLink
          :to="{ name: 'landing' }"
          class="flex items-center gap-3"
          @click="closeMobileMenu"
        >
          <span
            class="grid size-9 place-items-center bg-[var(--accent-electric)] font-data font-bold text-white"
            >U</span
          >
          <span class="font-data text-sm font-bold tracking-tight">{{
            t("landing.brand")
          }}</span>
          <span
            class="hidden font-data text-xs text-muted-foreground sm:inline"
            >{{ t("landing.version") }}</span
          >
        </RouterLink>

        <nav
          class="hidden items-center gap-7 md:flex"
          :aria-label="t('landing.primaryNavigation')"
        >
          <RouterLink :to="{ name: 'problemset' }" class="nav-link">{{
            t("sidebar.problem.problemSet")
          }}</RouterLink>
          <RouterLink :to="{ name: 'contest-list' }" class="nav-link">{{
            t("sidebar.contest.contestSection")
          }}</RouterLink>
          <RouterLink :to="{ name: 'forum-home' }" class="nav-link">{{
            t("sidebar.forum.platform")
          }}</RouterLink>
        </nav>

        <div class="flex items-center gap-2">
          <RouterLink
            v-if="authStore.isAuthenticated"
            :to="{ name: 'forum-home' }"
            class="button button--compact"
          >
            {{ t("landing.console") }}
          </RouterLink>
          <template v-else>
            <button
              class="hidden h-9 px-3 text-sm font-medium sm:block"
              @click="redirectTo('login')"
            >
              {{ t("landing.signIn") }}
            </button>
            <button
              class="button button--compact hidden sm:flex"
              @click="goToSeedProblem"
            >
              {{ t("landing.freeStart") }}
            </button>
          </template>
          <button
            class="grid size-10 place-items-center border border-silver md:hidden"
            type="button"
            :aria-expanded="mobileMenuOpen"
            :aria-label="
              mobileMenuOpen ? t('landing.closeMenu') : t('landing.openMenu')
            "
            @click="mobileMenuOpen = !mobileMenuOpen"
          >
            <X v-if="mobileMenuOpen" class="size-5" />
            <Menu v-else class="size-5" />
          </button>
        </div>
      </div>

      <nav
        v-if="mobileMenuOpen"
        class="border-t border-silver bg-card px-4 py-4 md:hidden"
        :aria-label="t('landing.mobileNavigation')"
      >
        <div class="mx-auto grid max-w-6xl gap-1">
          <RouterLink
            :to="{ name: 'problemset' }"
            class="mobile-link"
            @click="closeMobileMenu"
            >{{ t("sidebar.problem.problemSet") }}</RouterLink
          >
          <RouterLink
            :to="{ name: 'contest-list' }"
            class="mobile-link"
            @click="closeMobileMenu"
            >{{ t("sidebar.contest.contestSection") }}</RouterLink
          >
          <RouterLink
            :to="{ name: 'forum-home' }"
            class="mobile-link"
            @click="closeMobileMenu"
            >{{ t("sidebar.forum.platform") }}</RouterLink
          >
          <button class="button mt-3 w-full" @click="goToSeedProblem">
            {{ t("landing.freeStart") }}
          </button>
        </div>
      </nav>
    </header>

    <main>
      <section
        class="hero-grid container mx-auto grid max-w-6xl gap-12 px-4 py-14 lg:grid-cols-[0.9fr_1.1fr] lg:items-center lg:py-24"
      >
        <div class="max-w-xl">
          <p
            class="mb-5 font-data text-xs font-bold uppercase tracking-[0.2em] text-[var(--accent-electric)]"
          >
            {{ t("landing.heroEyebrow") }}
          </p>
          <h1
            class="hero-title text-5xl font-black leading-[0.9] tracking-[-0.055em] sm:text-6xl lg:text-7xl"
          >
            {{ t("landing.titlePart1") }}<br />
            <span class="text-[var(--accent-electric)]">{{
              t("landing.titlePart2")
            }}</span
            ><br />
            {{ t("landing.titlePart3") }}
          </h1>
          <p
            class="mt-7 max-w-lg text-base leading-7 text-muted-foreground sm:text-lg"
          >
            {{ t("landing.subtitle") }}
          </p>
          <div class="mt-8 flex flex-col gap-3 sm:flex-row">
            <button class="button" @click="goToSeedProblem">
              {{ t("landing.freeStart") }} <ArrowRight class="size-4" />
            </button>
            <RouterLink
              :to="{ name: 'problem-detail', params: { slug: TWO_SUM_SLUG } }"
              class="button button--secondary"
              >{{ t("landing.tryProblem") }} <Code2 class="size-4"
            /></RouterLink>
          </div>
          <p class="mt-4 font-data text-xs text-muted-foreground">
            {{ t("landing.noCreditCard") }}
          </p>
        </div>

        <div
          class="workbench border border-silver bg-card shadow-[6px_6px_0_0_var(--border)]"
        >
          <div
            class="flex items-center justify-between border-b border-silver bg-[var(--surface-sunken)] px-4 py-3 font-data text-xs"
          >
            <span>{{ t("landing.workbenchTitle") }}</span>
            <span class="flex items-center gap-2 text-[var(--terminal-green)]"
              ><span class="size-2 bg-[var(--terminal-green)]"></span
              >{{ t("landing.ready") }}</span
            >
          </div>
          <div class="border-b border-silver px-4 py-4">
            <p class="font-data text-xs text-muted-foreground">
              {{ t("landing.sampleProblem") }}
            </p>
            <h2 class="mt-1 text-lg font-bold">
              {{ t("landing.sampleProblemTitle") }}
            </h2>
          </div>
          <div
            class="flex items-center justify-between border-b border-silver px-3 py-2"
          >
            <div
              class="flex gap-1"
              role="group"
              :aria-label="t('landing.languageSelect')"
            >
              <button
                v-for="lang in ['cpp', 'py', 'js'] as const"
                :key="lang"
                class="language-tab"
                :class="{ 'language-tab--active': selectedLang === lang }"
                :aria-pressed="selectedLang === lang"
                @click="selectedLang = lang"
              >
                {{ lang }}
              </button>
            </div>
            <button
              data-testid="run-simulation"
              class="button button--run"
              :disabled="compiling"
              @click="runSimulation"
            >
              <Play class="size-3.5" />{{
                compiling ? t("landing.running") : t("landing.runCode")
              }}
            </button>
          </div>
          <pre
            class="min-h-64 overflow-x-auto p-4 font-data text-xs leading-5 sm:text-sm"
          ><code>{{ code }}</code></pre>
          <div
            class="min-h-32 border-t border-silver bg-[var(--surface-sunken)] p-4 font-data text-xs"
            aria-live="polite"
          >
            <p
              v-if="simulationTerminalText.length === 0"
              class="text-muted-foreground"
            >
              {{ t("landing.outputHint") }}
            </p>
            <p
              v-for="(line, index) in simulationTerminalText"
              :key="index"
              class="mb-1"
            >
              {{ line }}
            </p>
            <p
              v-if="showSuccessMsg"
              class="mt-3 flex items-center gap-2 font-bold text-[var(--terminal-green)]"
            >
              <CheckCircle2 class="size-4" />{{ t("landing.compileSuccess") }}
            </p>
          </div>
        </div>
      </section>

      <section class="border-y border-silver bg-[var(--surface-sunken)]">
        <div class="container mx-auto max-w-6xl px-4 py-16 lg:py-20">
          <div class="max-w-2xl">
            <p
              class="font-data text-xs font-bold uppercase tracking-[0.2em] text-[var(--accent-electric)]"
            >
              {{ t("landing.workflowEyebrow") }}
            </p>
            <h2 class="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
              {{ t("landing.workflowTitle") }}
            </h2>
            <p class="mt-4 text-base leading-7 text-muted-foreground">
              {{ t("landing.workflowSubtitle") }}
            </p>
          </div>
          <div class="mt-10 grid border border-silver bg-card md:grid-cols-3">
            <article class="workflow-card">
              <Code2 class="size-6 text-[var(--accent-electric)]" />
              <p class="workflow-command">problem.open()</p>
              <h3>{{ t("landing.practiceTitle") }}</h3>
              <p>{{ t("landing.practiceDesc") }}</p>
            </article>
            <article class="workflow-card">
              <Trophy class="size-6 text-[var(--terminal-amber)]" />
              <p class="workflow-command">contest.enter()</p>
              <h3>{{ t("landing.competeTitle") }}</h3>
              <p>{{ t("landing.competeDesc") }}</p>
            </article>
            <article class="workflow-card">
              <MessageSquare class="size-6 text-[var(--terminal-green)]" />
              <p class="workflow-command">solution.explain()</p>
              <h3>{{ t("landing.reviewTitle") }}</h3>
              <p>{{ t("landing.reviewDesc") }}</p>
            </article>
          </div>
        </div>
      </section>

      <section
        class="container mx-auto max-w-4xl px-4 py-20 text-center lg:py-28"
      >
        <ChevronDown class="mx-auto size-6 text-[var(--accent-electric)]" />
        <h2 class="mt-5 text-3xl font-black tracking-tight sm:text-5xl">
          {{ t("landing.ctaTitle") }}
        </h2>
        <p
          class="mx-auto mt-4 max-w-xl text-base leading-7 text-muted-foreground"
        >
          {{ t("landing.ctaDesc") }}
        </p>
        <button class="button mx-auto mt-8" @click="goToSeedProblem">
          {{ t("landing.freeStart") }} <ArrowRight class="size-4" />
        </button>
      </section>
    </main>

    <footer
      class="border-t border-silver bg-card py-7 text-sm text-muted-foreground"
    >
      <div
        class="container mx-auto flex max-w-6xl flex-col gap-3 px-4 sm:flex-row sm:items-center sm:justify-between"
      >
        <span>{{ t("landing.copyright") }}</span>
        <RouterLink
          :to="{ name: 'problem-detail', params: { slug: TWO_SUM_SLUG } }"
          class="font-medium text-foreground hover:text-[var(--accent-electric)]"
          >{{ t("landing.tryProblem") }} →</RouterLink
        >
      </div>
    </footer>
  </div>
</template>
