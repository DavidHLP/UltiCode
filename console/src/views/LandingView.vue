<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";
import { useI18n } from "vue-i18n";
import {
  Code2,
  Trophy,
  MessageSquare,
  Flame,
  ArrowRight,
  Play,
  CheckCircle2,
  Sparkles,
  Cpu,
} from "lucide-vue-next";

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();

// Code Simulation State
const selectedLang = ref("cpp");
const simulationTerminalText = ref<string[]>([]);
const compiling = ref(false);
const showSuccessMsg = ref(false);

const codeSnippets = {
  cpp: `#include <iostream>\n#include <vector>\nusing namespace std;\n\nint main() {\n    vector<int> nums = {2, 7, 11, 15};\n    int target = 9;\n    cout << "Initializing solver..." << endl;\n    return 0;\n}`,
  py: `def two_sum(nums, target):\n    # Initialize hash map\n    prev_map = {}\n    for i, n in enumerate(nums):\n        diff = target - n\n        if diff in prev_map:\n            return [prev_map[diff], i]\n        prev_map[n] = i\n    return []`,
  js: `function twoSum(nums, target) {\n    const map = new Map();\n    for (let i = 0; i < nums.length; i++) {\n        const diff = target - nums[i];\n        if (map.has(diff)) {\n            return [map.get(diff), i];\n        }\n        map.set(nums[i], i);\n    }\n    return [];\n}`,
};

// Simulated compile output
const runSimulation = () => {
  if (compiling.value) return;
  compiling.value = true;
  showSuccessMsg.value = false;
  simulationTerminalText.value = ["> ulticode-compiler --target solve.bin"];

  setTimeout(() => {
    simulationTerminalText.value.push("Compiling source files... [OK]");
  }, 400);

  setTimeout(() => {
    simulationTerminalText.value.push("Linking binary objects... [OK]");
    simulationTerminalText.value.push("> ./solve.bin --test-suite=all");
  }, 900);

  setTimeout(() => {
    simulationTerminalText.value.push(
      "CASE 1: [2, 7, 11, 15] target=9 -> Expect [0, 1]... [PASS] (0.01ms)",
    );
  }, 1400);

  setTimeout(() => {
    simulationTerminalText.value.push(
      "CASE 2: [3, 2, 4] target=6 -> Expect [1, 2]... [PASS] (0.02ms)",
    );
    simulationTerminalText.value.push(
      "CASE 3: [3, 3] target=6 -> Expect [0, 1]... [PASS] (0.01ms)",
    );
  }, 1800);

  setTimeout(() => {
    simulationTerminalText.value.push(
      "STATUS: All tests passed. Execution trace finalized.",
    );
    compiling.value = false;
    showSuccessMsg.value = true;
  }, 2200);
};

// Automatic typing effect for hero terminal
const heroCommand = ref("");
const targetCommand = "ulticode --init-console --verbose";
let typeInterval: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  let index = 0;
  typeInterval = setInterval(() => {
    if (index < targetCommand.length) {
      heroCommand.value += targetCommand[index];
      index++;
    } else {
      if (typeInterval) clearInterval(typeInterval);
    }
  }, 80);
});

onUnmounted(() => {
  if (typeInterval) clearInterval(typeInterval);
});

const handleRegisterRedirect = () => {
  if (authStore.isAuthenticated) {
    router.push({ name: "forum-home" });
  } else {
    router.push({ name: "register" });
  }
};

const handleLoginRedirect = () => {
  if (authStore.isAuthenticated) {
    router.push({ name: "forum-home" });
  } else {
    router.push({ name: "login" });
  }
};
</script>

<template>
  <div
    class="min-h-screen bg-background text-foreground selection:bg-[var(--accent-electric)] selection:text-white font-sans"
  >
    <!-- Top Landing Header -->
    <header class="border-b border-silver bg-card sticky top-0 z-50">
      <div
        class="container mx-auto max-w-6xl h-14 px-4 flex items-center justify-between"
      >
        <div class="flex items-center gap-3">
          <div
            class="h-8 w-8 bg-[var(--accent-electric)] flex items-center justify-center text-white font-data font-bold"
          >
            U
          </div>
          <div>
            <span class="font-data font-bold text-sm tracking-tight">{{
              t("landing.brand")
            }}</span>
            <span class="text-2xs text-muted-foreground ml-1 font-data">{{
              t("landing.version")
            }}</span>
          </div>
        </div>

        <nav class="hidden md:flex items-center gap-6">
          <RouterLink
            :to="{ name: 'problemset' }"
            class="text-xs font-data uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors"
          >
            {{ t("sidebar.problem.problemSet") }}
          </RouterLink>
          <RouterLink
            :to="{ name: 'forum-home' }"
            class="text-xs font-data uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors"
          >
            {{ t("sidebar.forum.platform") }}
          </RouterLink>
          <RouterLink
            :to="{ name: 'contest-list' }"
            class="text-xs font-data uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors"
          >
            {{ t("sidebar.contest.contestSection") }}
          </RouterLink>
        </nav>

        <div class="flex items-center gap-3">
          <template v-if="authStore.isAuthenticated">
            <RouterLink
              :to="{ name: 'forum-home' }"
              class="h-8 px-3 text-xs font-data uppercase tracking-wider border border-silver flex items-center justify-center hover:bg-accent transition-colors text-foreground"
            >
              {{ t("landing.console") }}
            </RouterLink>
          </template>
          <template v-else>
            <button
              @click="handleLoginRedirect"
              class="h-8 px-3 text-xs font-data uppercase tracking-wider hover:text-[var(--accent-electric)] cursor-pointer transition-colors border border-transparent"
            >
              {{ t("landing.signIn") }}
            </button>
            <button
              @click="handleRegisterRedirect"
              class="h-8 px-3 text-xs font-data uppercase tracking-wider bg-[var(--accent-electric)] text-white border border-[var(--accent-electric)] shadow-[2px_2px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 transition-all cursor-pointer"
            >
              {{ t("landing.initialize") }}
            </button>
          </template>
        </div>
      </div>
    </header>

    <!-- Main Container -->
    <main class="container mx-auto max-w-5xl px-4 py-8 md:py-16 space-y-20">
      <!-- Hero Section -->
      <section class="grid gap-12 lg:grid-cols-12 items-center">
        <div class="lg:col-span-6 space-y-6">
          <div
            class="inline-flex items-center gap-2 px-2.5 py-0.5 border bg-[color-mix(in_oklch,var(--accent-electric)_15%,transparent)] text-[var(--accent-electric)] border-[color-mix(in_oklch,var(--accent-electric)_30%,transparent)] font-data text-2xs tracking-wider uppercase"
          >
            <Sparkles class="size-3" /> {{ t("landing.coreSessionReady") }}
          </div>

          <div class="space-y-3">
            <h1
              class="text-4xl md:text-5xl font-extrabold tracking-tight uppercase leading-none text-[var(--solarized-base03)] dark:text-[var(--silver-900)]"
            >
              {{ t("landing.titlePart1") }} <br />
              <span class="text-[var(--accent-electric)]">{{
                t("landing.titlePart2")
              }}</span>
              <br />
              {{ t("landing.titlePart3") }}
            </h1>
            <p
              class="text-xs text-muted-foreground font-data leading-relaxed max-w-md bg-[color-mix(in_oklch,var(--silver-300)_5%,transparent)] border-l-2 border-silver p-2.5"
            >
              {{ t("landing.subtitle") }}
            </p>
          </div>

          <div class="flex flex-col sm:flex-row gap-3 pt-2">
            <button
              @click="handleRegisterRedirect"
              class="h-10 px-6 font-data text-xs uppercase tracking-wider bg-[var(--accent-electric)] text-white border border-[var(--accent-electric)] shadow-[3px_3px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 transition-all flex items-center justify-center gap-2 cursor-pointer"
            >
              {{ t("landing.startSession") }} <ArrowRight class="size-4" />
            </button>
            <RouterLink
              :to="{ name: 'problemset' }"
              class="h-10 px-6 font-data text-xs uppercase tracking-wider border border-silver bg-card shadow-[3px_3px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 text-foreground transition-all flex items-center justify-center gap-2 cursor-pointer"
            >
              {{ t("landing.browseProblems") }} <Code2 class="size-4" />
            </RouterLink>
          </div>

          <!-- Hero ASCII progress stats -->
          <div class="pt-4 space-y-2 max-w-sm">
            <div
              class="flex justify-between items-center text-2xs font-data text-muted-foreground uppercase"
            >
              <span>{{ t("landing.systemBootStatus") }}</span>
              <span>{{ t("landing.bootOk") }}</span>
            </div>
            <div class="ascii-progress flex items-center">
              <span class="ascii-progress-fill"
                >[■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■]</span
              >
            </div>
          </div>
        </div>

        <!-- Interactive Hero Terminal (Right) -->
        <div class="lg:col-span-6">
          <div class="terminal-card border border-silver">
            <div class="terminal-card-header flex items-center justify-between">
              <span>ulticode@system:~</span>
              <div class="flex items-center gap-1.5">
                <span
                  class="size-2 bg-red-500/30 border border-red-600/50"
                ></span>
                <span
                  class="size-2 bg-yellow-500/30 border border-yellow-600/50"
                ></span>
                <span
                  class="size-2 bg-green-500/30 border border-green-600/50"
                ></span>
              </div>
            </div>

            <div class="p-4 space-y-4 min-h-[300px] bg-card text-xs">
              <div class="space-y-1 font-data">
                <p class="text-muted-foreground">
                  {{ t("landing.shellActive") }}
                </p>
                <div class="flex items-center">
                  <span class="terminal-prompt"></span>
                  <span class="text-[var(--accent-electric)]">{{
                    heroCommand
                  }}</span>
                  <span class="terminal-cursor"></span>
                </div>
              </div>

              <!-- Simulator Box -->
              <div
                class="border border-silver p-3 space-y-2 bg-[var(--surface-sunken)]"
              >
                <div
                  class="flex justify-between items-center border-b border-silver pb-2 mb-2"
                >
                  <div class="flex gap-2">
                    <button
                      v-for="lang in ['cpp', 'py', 'js']"
                      :key="lang"
                      @click="selectedLang = lang"
                      :class="[
                        'px-2 py-0.5 font-data text-2xs uppercase border transition-colors',
                        selectedLang === lang
                          ? 'border-[var(--accent-electric)] bg-[color-mix(in_oklch,var(--accent-electric)_15%,transparent)] text-[var(--accent-electric)]'
                          : 'border-transparent text-muted-foreground hover:text-foreground',
                      ]"
                    >
                      {{ lang }}
                    </button>
                  </div>
                  <button
                    @click="runSimulation"
                    :disabled="compiling"
                    class="px-3 py-1 font-data text-2xs uppercase bg-primary text-primary-foreground hover:bg-primary/95 flex items-center gap-1 disabled:opacity-50"
                  >
                    <Play class="size-3" />
                    {{
                      compiling ? t("landing.running") : t("landing.runCode")
                    }}
                  </button>
                </div>
                <pre
                  class="font-data text-2xs text-foreground leading-tight overflow-x-auto max-h-[140px] max-w-full"
                ><code>{{ codeSnippets[selectedLang as keyof typeof codeSnippets] }}</code></pre>
              </div>

              <!-- Output Logs -->
              <div
                v-if="simulationTerminalText.length > 0"
                class="border border-silver p-3 bg-black/5 dark:bg-black/40 space-y-1 max-h-[120px] overflow-y-auto"
              >
                <p
                  v-for="(log, i) in simulationTerminalText"
                  :key="i"
                  :class="[
                    'font-data text-2xs leading-tight',
                    log.includes('[PASS]')
                      ? 'text-[var(--terminal-green)]'
                      : log.includes('STATUS')
                        ? 'text-[var(--accent-electric)]'
                        : 'text-muted-foreground',
                  ]"
                >
                  {{ log }}
                </p>
                <div
                  v-if="showSuccessMsg"
                  class="mt-2 p-2 bg-[color-mix(in_oklch,var(--terminal-green)_10%,transparent)] border border-[color-mix(in_oklch,var(--terminal-green)_25%,transparent)] flex items-center gap-2 text-[var(--terminal-green)] font-data text-2xs"
                >
                  <CheckCircle2 class="size-3.5 shrink-0" />
                  <span>{{ t("landing.compileSuccess") }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="terminal-separator"></div>

      <!-- Feature Matrix Section -->
      <section class="space-y-8">
        <div class="text-center space-y-2">
          <h2
            class="text-2xl font-bold uppercase tracking-tight text-[var(--solarized-base03)] dark:text-[var(--silver-900)]"
          >
            {{ t("landing.modulesTitle") }}
          </h2>
          <p class="text-xs text-muted-foreground font-data">
            {{ t("landing.modulesSubtitle") }}
          </p>
        </div>

        <div class="grid gap-6 md:grid-cols-3">
          <!-- Card 1 - Core Featured Module (精密评测机) -->
          <div
            class="precision-card precision-card--featured lg:col-span-3 border border-[var(--border)] bg-card p-6 shadow-[3px_3px_0px_0px_var(--border)] grid gap-6 md:grid-cols-12 relative overflow-hidden"
          >
            <div
              class="absolute right-0 top-0 bg-[var(--accent-electric)] text-white font-data text-2xs uppercase tracking-widest px-3 py-1 font-bold"
            >
              PRIMARY ENGINE
            </div>

            <div class="md:col-span-7 space-y-4">
              <div class="flex items-center gap-3">
                <div
                  class="size-11 bg-[color-mix(in_oklch,var(--accent-electric)_15%,transparent)] flex items-center justify-center border border-[var(--accent-electric)]"
                >
                  <Cpu
                    class="size-6 text-[var(--accent-electric)] animate-pulse"
                  />
                </div>
                <div>
                  <span
                    class="text-2xs font-data text-[var(--accent-electric)] uppercase tracking-wider font-bold"
                    >HIGH PERFORMANCE</span
                  >
                  <h3 class="font-data font-bold text-base uppercase mt-0.5">
                    {{ t("landing.judgeTitle") }}
                  </h3>
                </div>
              </div>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t("landing.judgeDesc") }}
              </p>
              <div
                class="grid grid-cols-3 gap-4 pt-4 border-t border-[var(--border)]"
              >
                <div class="space-y-0.5">
                  <span
                    class="text-2xs text-muted-foreground uppercase font-data"
                    >EXECUTION SPEED</span
                  >
                  <p
                    class="text-xs font-bold font-mono text-[var(--accent-electric)]"
                  >
                    &lt; 12ms avg
                  </p>
                </div>
                <div class="space-y-0.5">
                  <span
                    class="text-2xs text-muted-foreground uppercase font-data"
                    >CONTAINERIZATION</span
                  >
                  <p
                    class="text-xs font-bold font-mono text-[var(--terminal-green)]"
                  >
                    DOCKER SECURE
                  </p>
                </div>
                <div class="space-y-0.5">
                  <span
                    class="text-2xs text-muted-foreground uppercase font-data"
                    >SUPPORTED LANGS</span
                  >
                  <p
                    class="text-xs font-bold font-mono text-[var(--terminal-amber)]"
                  >
                    C++ / PY / JS
                  </p>
                </div>
              </div>
            </div>

            <div
              class="md:col-span-5 border border-[var(--border)] bg-[var(--surface-sunken)] p-3 flex flex-col justify-between font-mono text-2xs text-muted-foreground h-full min-h-[140px]"
            >
              <div
                class="flex items-center justify-between border-b border-[var(--border)] pb-1.5 mb-1.5"
              >
                <span class="text-2xs text-[var(--accent-electric)] font-bold"
                  >// SANDBOX RUNNER ACTIVE</span
                >
                <span
                  class="size-2 bg-[var(--terminal-green)] rounded-full animate-ping"
                ></span>
              </div>
              <div class="space-y-1 select-none">
                <p>
                  <span class="text-[var(--terminal-cyan)]">[OK]</span> Init
                  jail-root sandbox environment...
                </p>
                <p>
                  <span class="text-[var(--terminal-cyan)]">[OK]</span> Mount
                  read-only headers (glibc-2.39)
                </p>
                <p>
                  <span class="text-[var(--terminal-amber)]">[RUN]</span>
                  Evaluate user solution (PID: 18402)
                </p>
                <p>
                  <span class="text-[var(--terminal-green)]">[PASS]</span> Test
                  case 01-15 validated in 4.2ms
                </p>
                <p class="text-foreground font-bold">
                  &gt; Process terminated with status code 0
                </p>
              </div>
            </div>
          </div>

          <!-- Card 2 -->
          <div
            class="precision-card border border-silver bg-card p-5 space-y-4 lg:col-span-1 shadow-[3px_3px_0px_0px_var(--border)]"
          >
            <div
              class="size-10 bg-[color-mix(in_oklch,var(--terminal-green)_15%,transparent)] flex items-center justify-center border border-[color-mix(in_oklch,var(--terminal-green)_30%,transparent)]"
            >
              <Trophy class="size-5 text-[var(--terminal-green)]" />
            </div>
            <div class="space-y-2">
              <h3 class="font-data font-bold text-sm uppercase">
                {{ t("landing.contestsTitle") }}
              </h3>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t("landing.contestsDesc") }}
              </p>
            </div>
            <div
              class="text-2xs font-data text-[var(--terminal-green)] uppercase tracking-wider flex items-center gap-1"
            >
              {{ t("landing.contestsFooter") }} <CheckCircle2 class="size-3" />
            </div>
          </div>

          <!-- Card 3 -->
          <div
            class="precision-card border border-silver bg-card p-5 space-y-4 lg:col-span-1 shadow-[3px_3px_0px_0px_var(--border)]"
          >
            <div
              class="size-10 bg-[color-mix(in_oklch,var(--terminal-amber)_15%,transparent)] flex items-center justify-center border border-[color-mix(in_oklch,var(--terminal-amber)_30%,transparent)]"
            >
              <MessageSquare class="size-5 text-[var(--terminal-amber)]" />
            </div>
            <div class="space-y-2">
              <h3 class="font-data font-bold text-sm uppercase">
                {{ t("landing.feedTitle") }}
              </h3>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t("landing.feedDesc") }}
              </p>
            </div>
            <div
              class="text-2xs font-data text-[var(--terminal-amber)] uppercase tracking-wider flex items-center gap-1"
            >
              {{ t("landing.feedFooter") }}
            </div>
          </div>

          <!-- Card 4 -->
          <div
            class="precision-card border border-silver bg-card p-5 space-y-4 lg:col-span-1 shadow-[3px_3px_0px_0px_var(--border)]"
          >
            <div
              class="size-10 bg-[color-mix(in_oklch,var(--terminal-purple)_15%,transparent)] flex items-center justify-center border border-[color-mix(in_oklch,var(--terminal-purple)_30%,transparent)]"
            >
              <Flame class="size-5 text-[var(--terminal-purple)]" />
            </div>
            <div class="space-y-2">
              <h3 class="font-data font-bold text-sm uppercase">
                {{ t("landing.badgesTitle") }}
              </h3>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t("landing.badgesDesc") }}
              </p>
            </div>
            <div
              class="text-2xs font-data text-[var(--terminal-purple)] uppercase tracking-wider flex items-center gap-1"
            >
              {{ t("landing.badgesFooter") }}
            </div>
          </div>
        </div>
      </section>

      <div class="terminal-separator"></div>

      <!-- System Telemetry (Live stats) -->
      <section class="grid gap-8 md:grid-cols-12 items-center">
        <div class="md:col-span-5 space-y-4">
          <h2 class="text-2xl font-bold uppercase tracking-tight">
            {{ t("landing.telemetryTitle") }}
          </h2>
          <p class="text-xs text-muted-foreground leading-relaxed font-data">
            {{ t("landing.telemetrySubtitle") }}
          </p>

          <div class="space-y-3 pt-2">
            <div class="space-y-1">
              <div
                class="flex justify-between text-xxs font-data text-muted-foreground"
              >
                <span>{{ t("landing.solverCapacity") }}</span>
                <span class="tabular-nums">84,912 / 100,000</span>
              </div>
              <div class="ascii-progress">
                <span class="ascii-progress-fill"
                  >[■■■■■■■■■■■■■■■■■■■■■■■■■□□□□□]</span
                >
              </div>
            </div>

            <div class="space-y-1">
              <div
                class="flex justify-between text-xxs font-data text-muted-foreground"
              >
                <span>{{ t("landing.latencyStability") }}</span>
                <span class="tabular-nums">99.86%</span>
              </div>
              <div class="ascii-progress">
                <span class="ascii-progress-fill"
                  >[■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■]</span
                >
              </div>
            </div>
          </div>
        </div>

        <!-- Telemetry Data Display -->
        <div class="md:col-span-7">
          <div class="terminal-card border border-silver">
            <div class="terminal-card-header font-data flex justify-between">
              <span>{{ t("landing.logHeader") }}</span>
              <span class="text-[var(--terminal-green)] animate-pulse-subtle"
                >LIVE</span
              >
            </div>
            <div class="p-4 bg-card font-data text-xs space-y-4">
              <div class="grid grid-cols-2 gap-4">
                <div
                  class="border border-silver p-3 bg-[var(--surface-sunken)] shadow-[2px_2px_0px_0px_var(--border)]"
                >
                  <span class="terminal-kv-key text-muted-foreground">{{
                    t("landing.totalSubmissions")
                  }}</span>
                  <p
                    class="terminal-kv-value text-xl font-extrabold mt-1 text-[var(--accent-electric)]"
                  >
                    1,348,902
                  </p>
                  <p
                    class="text-2xs text-muted-foreground/80 mt-1 border-t border-[var(--border)]/30 pt-1.5 leading-normal"
                  >
                    {{ t("landing.totalSubmissionsDesc") }}
                  </p>
                </div>
                <div
                  class="border border-silver p-3 bg-[var(--surface-sunken)] shadow-[2px_2px_0px_0px_var(--border)]"
                >
                  <span class="terminal-kv-key text-muted-foreground">{{
                    t("landing.activeSolvers24h")
                  }}</span>
                  <p
                    class="terminal-kv-value text-xl font-extrabold mt-1 text-[var(--terminal-green)]"
                  >
                    42,918
                  </p>
                  <p
                    class="text-2xs text-muted-foreground/80 mt-1 border-t border-[var(--border)]/30 pt-1.5 leading-normal"
                  >
                    {{ t("landing.activeSolversDesc") }}
                  </p>
                </div>
                <div
                  class="border border-silver p-3 bg-[var(--surface-sunken)] shadow-[2px_2px_0px_0px_var(--border)]"
                >
                  <span class="terminal-kv-key text-muted-foreground">{{
                    t("landing.compilationAvgMs")
                  }}</span>
                  <p
                    class="terminal-kv-value text-xl font-extrabold mt-1 text-[var(--terminal-amber)]"
                  >
                    14.2 ms
                  </p>
                  <p
                    class="text-2xs text-muted-foreground/80 mt-1 border-t border-[var(--border)]/30 pt-1.5 leading-normal"
                  >
                    {{ t("landing.compilationAvgDesc") }}
                  </p>
                </div>
                <div
                  class="border border-silver p-3 bg-[var(--surface-sunken)] shadow-[2px_2px_0px_0px_var(--border)]"
                >
                  <span class="terminal-kv-key text-muted-foreground">{{
                    t("landing.contestsCompleted")
                  }}</span>
                  <p
                    class="terminal-kv-value text-xl font-extrabold mt-1 text-[var(--terminal-purple)]"
                  >
                    142 {{ t("landing.eventsUnit") }}
                  </p>
                  <p
                    class="text-2xs text-muted-foreground/80 mt-1 border-t border-[var(--border)]/30 pt-1.5 leading-normal"
                  >
                    {{ t("landing.contestsCompletedDesc") }}
                  </p>
                </div>
              </div>

              <!-- Terminal style code status log -->
              <div
                class="border border-silver p-2.5 bg-[var(--surface-sunken)] text-2xs text-muted-foreground space-y-0.5"
              >
                <p>
                  <span class="text-[var(--terminal-cyan)]">[INFO]</span>
                  2026-06-02T23:38:04Z - {{ t("landing.logInfoInit") }}
                </p>
                <p>
                  <span class="text-[var(--terminal-cyan)]">[INFO]</span>
                  2026-06-02T23:38:05Z - {{ t("landing.logInfoSandbox") }}
                </p>
                <p>
                  <span class="text-[var(--terminal-green)]">[OK]</span>
                  2026-06-02T23:38:05Z - {{ t("landing.logOkSync") }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="terminal-separator"></div>

      <!-- CTA Session Initialize -->
      <section
        class="terminal-card border border-silver bg-[var(--surface-sunken)] max-w-3xl mx-auto overflow-hidden shadow-[4px_4px_0px_0px_var(--border)]"
      >
        <div
          class="flex items-center justify-between border-b border-silver px-4 py-2 bg-[var(--surface-sunken)] font-data text-xs text-muted-foreground"
        >
          <span>session_initialize.sh</span>
          <span
            class="text-[var(--terminal-green)] flex items-center gap-1.5 font-bold"
          >
            <span
              class="size-1.5 bg-[var(--terminal-green)] rounded-full animate-pulse"
            ></span>
            EXECUTE_READY
          </span>
        </div>
        <div class="py-10 px-6 text-center bg-card space-y-6">
          <div class="space-y-2">
            <div
              class="text-[var(--accent-electric)] font-data text-xs uppercase tracking-widest font-bold animate-pulse-subtle"
            >
              {{ t("landing.readyToLaunch") }}
            </div>
            <h2
              class="text-2xl font-extrabold uppercase tracking-tight text-[var(--solarized-base03)] dark:text-[var(--silver-900)]"
            >
              {{ t("landing.ctaTitle") }}
            </h2>
            <p class="text-xs text-muted-foreground max-w-md mx-auto font-data">
              {{ t("landing.ctaDesc") }}
            </p>
          </div>

          <div class="flex justify-center gap-3">
            <button
              @click="handleRegisterRedirect"
              class="h-10 px-8 font-data text-xs uppercase tracking-wider bg-[var(--accent-electric)] text-white border border-[var(--accent-electric)] shadow-[3px_3px_0px_0px_var(--border)] active:translate-x-0.5 active:translate-y-0.5 hover:-translate-x-0.5 hover:-translate-y-0.5 transition-all flex items-center gap-2 cursor-pointer"
            >
              {{ t("landing.createAccount") }} <ArrowRight class="size-4" />
            </button>
          </div>
        </div>
      </section>
    </main>

    <!-- Footer -->
    <footer
      class="border-t border-silver bg-card py-8 mt-12 text-xs font-data text-muted-foreground"
    >
      <div
        class="container mx-auto max-w-6xl px-4 flex flex-col md:flex-row items-center justify-between gap-4"
      >
        <div>
          <span>{{ t("landing.copyright") }}</span>
        </div>
        <div class="flex gap-6">
          <a href="#" class="hover:text-foreground">{{
            t("landing.apiDocs")
          }}</a>
          <a href="#" class="hover:text-foreground">{{
            t("landing.repository")
          }}</a>
          <a href="#" class="hover:text-foreground">{{ t("landing.terms") }}</a>
          <a href="#" class="hover:text-foreground">{{
            t("landing.status")
          }}</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.terminal-card {
  box-shadow: 4px 4px 0px 0px var(--border);
  transition: all var(--transition-normal);
}

.terminal-card:hover {
  border-color: var(--accent-electric);
  box-shadow: 6px 6px 0px 0px var(--accent-electric-glow);
  transform: translate(-2px, -2px);
}

.precision-card {
  box-shadow: 3px 3px 0px 0px var(--border);
  transition: all var(--transition-normal);
}

.precision-card:hover {
  border-color: var(--accent-electric);
  box-shadow: 5px 5px 0px 0px var(--accent-electric-glow);
  transform: translate(-2px, -2px);
}

.precision-card--featured:hover {
  border-color: var(--accent-electric);
  box-shadow: 6px 6px 0px 0px var(--accent-electric-glow);
  transform: translate(-2px, -2px);
}
</style>
