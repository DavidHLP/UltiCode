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

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();

const mobileMenuOpen = ref(false);
const simulationTerminalText = ref<string[]>([]);
const compiling = ref(false);
const showSuccessMsg = ref(false);
const timers = new Set<ReturnType<typeof setTimeout>>();

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

const selectedLang = ref<keyof typeof codeSnippets>("cpp");
const code = computed(() => codeSnippets[selectedLang.value]);

const later = (delay: number, action: () => void) => {
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
  simulationTerminalText.value = [t("landing.outputCompile")];

  later(350, () =>
    simulationTerminalText.value.push(t("landing.outputCaseOne")),
  );
  later(700, () =>
    simulationTerminalText.value.push(t("landing.outputCaseTwo")),
  );
  later(1050, () => {
    simulationTerminalText.value.push(t("landing.outputComplete"));
    compiling.value = false;
    showSuccessMsg.value = true;
  });
};

onUnmounted(() => timers.forEach(clearTimeout));

const handleRegisterRedirect = () =>
  router.push({ name: authStore.isAuthenticated ? "forum-home" : "register" });

const handleLoginRedirect = () =>
  router.push({ name: authStore.isAuthenticated ? "forum-home" : "login" });

const closeMobileMenu = () => {
  mobileMenuOpen.value = false;
};
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
              @click="handleLoginRedirect"
            >
              {{ t("landing.signIn") }}
            </button>
            <button
              class="button button--compact hidden sm:flex"
              @click="handleRegisterRedirect"
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
          <button
            v-if="!authStore.isAuthenticated"
            class="button mt-3 w-full"
            @click="handleRegisterRedirect"
          >
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
            <button class="button" @click="handleRegisterRedirect">
              {{ t("landing.freeStart") }} <ArrowRight class="size-4" />
            </button>
            <RouterLink
              :to="{ name: 'problem-detail', params: { slug: 'two-sum' } }"
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
        <button class="button mx-auto mt-8" @click="handleRegisterRedirect">
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
          :to="{ name: 'problem-detail', params: { slug: 'two-sum' } }"
          class="font-medium text-foreground hover:text-[var(--accent-electric)]"
          >{{ t("landing.tryProblem") }} →</RouterLink
        >
      </div>
    </footer>
  </div>
</template>

<style scoped>
.hero-grid {
  background-image:
    linear-gradient(var(--border) 1px, transparent 1px),
    linear-gradient(90deg, var(--border) 1px, transparent 1px);
  background-size: 40px 40px;
  background-position: -1px -1px;
}
.hero-title {
  text-wrap: balance;
}
.nav-link {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--muted-foreground);
  transition: color var(--transition-fast);
}
.nav-link:hover,
.nav-link:focus-visible {
  color: var(--foreground);
}
.mobile-link {
  padding: 0.75rem;
  font-size: 1rem;
  font-weight: 600;
}
.button {
  display: inline-flex;
  min-height: 2.75rem;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border: 1px solid var(--accent-electric);
  background: var(--accent-electric);
  padding: 0 1.25rem;
  font-size: 0.875rem;
  font-weight: 700;
  color: white;
  box-shadow: 3px 3px 0 var(--border);
  transition:
    transform var(--transition-fast),
    box-shadow var(--transition-fast);
}
.button:hover {
  transform: translate(-2px, -2px);
  box-shadow: 5px 5px 0 var(--border);
}
.button:active {
  transform: translate(1px, 1px);
  box-shadow: 1px 1px 0 var(--border);
}
.button:disabled {
  cursor: wait;
  opacity: 0.6;
  transform: none;
}
.button--compact {
  min-height: 2.25rem;
  padding: 0 0.875rem;
}
.button--secondary {
  border-color: var(--border);
  background: var(--card);
  color: var(--foreground);
}
.button--run {
  min-height: 2rem;
  padding: 0 0.75rem;
  font-family: var(--font-data);
  font-size: 0.75rem;
  box-shadow: 2px 2px 0 var(--border);
}
.language-tab {
  border: 1px solid transparent;
  padding: 0.35rem 0.6rem;
  font-family: var(--font-data);
  font-size: 0.75rem;
  color: var(--muted-foreground);
}
.language-tab--active {
  border-color: var(--accent-electric);
  background: color-mix(in oklch, var(--accent-electric) 12%, transparent);
  color: var(--accent-electric);
}
.workflow-card {
  padding: 1.75rem;
}
.workflow-card + .workflow-card {
  border-top: 1px solid var(--border);
}
.workflow-card h3 {
  margin-top: 0.75rem;
  font-size: 1.125rem;
  font-weight: 700;
}
.workflow-card > p:last-child {
  margin-top: 0.5rem;
  font-size: 0.9375rem;
  line-height: 1.6;
  color: var(--muted-foreground);
}
.workflow-command {
  margin-top: 2rem;
  font-family: var(--font-data);
  font-size: 0.75rem;
  color: var(--muted-foreground);
}
:is(a, button):focus-visible {
  outline: 3px solid var(--accent-electric);
  outline-offset: 3px;
}
@media (min-width: 768px) {
  .workflow-card + .workflow-card {
    border-top: 0;
    border-left: 1px solid var(--border);
  }
}
@media (prefers-reduced-motion: reduce) {
  .button,
  .nav-link {
    transition: none;
  }
  .button:hover,
  .button:active {
    transform: none;
  }
}
</style>
