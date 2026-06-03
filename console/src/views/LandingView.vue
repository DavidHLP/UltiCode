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
  Cpu
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
  js: `function twoSum(nums, target) {\n    const map = new Map();\n    for (let i = 0; i < nums.length; i++) {\n        const diff = target - nums[i];\n        if (map.has(diff)) {\n            return [map.get(diff), i];\n        }\n        map.set(nums[i], i);\n    }\n    return [];\n}`
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
    simulationTerminalText.value.push("CASE 1: [2, 7, 11, 15] target=9 -> Expect [0, 1]... [PASS] (0.01ms)");
  }, 1400);

  setTimeout(() => {
    simulationTerminalText.value.push("CASE 2: [3, 2, 4] target=6 -> Expect [1, 2]... [PASS] (0.02ms)");
    simulationTerminalText.value.push("CASE 3: [3, 3] target=6 -> Expect [0, 1]... [PASS] (0.01ms)");
  }, 1800);

  setTimeout(() => {
    simulationTerminalText.value.push("STATUS: All tests passed. Execution trace finalized.");
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
  <div class="min-h-screen bg-background text-foreground selection:bg-[var(--accent-electric)] selection:text-white font-sans">
    
    <!-- Top Landing Header -->
    <header class="border-b border-silver bg-card sticky top-0 z-50">
      <div class="container mx-auto max-w-6xl h-14 px-4 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="h-8 w-8 bg-[var(--accent-electric)] flex items-center justify-center text-white font-data font-bold">
            U
          </div>
          <div>
            <span class="font-data font-bold text-sm tracking-tight">{{ t('landing.brand') }}</span>
            <span class="text-[10px] text-muted-foreground ml-1 font-data">{{ t('landing.version') }}</span>
          </div>
        </div>

        <nav class="hidden md:flex items-center gap-6">
          <RouterLink :to="{ name: 'problemset' }" class="text-xs font-data uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors">
            {{ t('sidebar.problem.problemSet') }}
          </RouterLink>
          <RouterLink :to="{ name: 'forum-home' }" class="text-xs font-data uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors">
            {{ t('sidebar.forum.platform') }}
          </RouterLink>
          <RouterLink :to="{ name: 'contest-list' }" class="text-xs font-data uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors">
            {{ t('sidebar.contest.contestSection') }}
          </RouterLink>
        </nav>

        <div class="flex items-center gap-3">
          <template v-if="authStore.isAuthenticated">
            <RouterLink 
              :to="{ name: 'forum-home' }" 
              class="h-8 px-3 text-xs font-data uppercase tracking-wider border border-silver flex items-center justify-center hover:bg-accent transition-colors"
            >
              {{ t('landing.console') }}
            </RouterLink>
          </template>
          <template v-else>
            <button 
              @click="handleLoginRedirect" 
              class="h-8 px-3 text-xs font-data uppercase tracking-wider hover:text-[var(--accent-electric)] transition-colors"
            >
              {{ t('landing.signIn') }}
            </button>
            <button 
              @click="handleRegisterRedirect" 
              class="h-8 px-3 text-xs font-data uppercase tracking-wider bg-[var(--accent-electric)] text-white hover:bg-[var(--accent-electric)]/90 transition-colors"
            >
              {{ t('landing.initialize') }}
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
          <div class="inline-flex items-center gap-2 px-2.5 py-0.5 border bg-[color-mix(in_oklch,var(--accent-electric)_15%,transparent)] text-[var(--accent-electric)] border-[color-mix(in_oklch,var(--accent-electric)_30%,transparent)] font-data text-[10px] tracking-wider uppercase">
            <Sparkles class="size-3" /> {{ t('landing.coreSessionReady') }}
          </div>
          
          <div class="space-y-3">
            <h1 class="text-4xl md:text-5xl font-extrabold tracking-tight uppercase leading-none">
              {{ t('landing.titlePart1') }} <br/>
              <span class="text-[var(--accent-electric)]">{{ t('landing.titlePart2') }}</span> <br/>
              {{ t('landing.titlePart3') }}
            </h1>
            <p class="text-sm text-muted-foreground font-data leading-relaxed max-w-md">
              {{ t('landing.subtitle') }}
            </p>
          </div>

          <div class="flex flex-col sm:flex-row gap-3 pt-2">
            <button 
              @click="handleRegisterRedirect"
              class="h-10 px-6 font-data text-xs uppercase tracking-wider bg-[var(--accent-electric)] text-white hover:bg-[var(--accent-electric)]/90 transition-all flex items-center justify-center gap-2"
            >
              {{ t('landing.startSession') }} <ArrowRight class="size-4" />
            </button>
            <RouterLink 
              :to="{ name: 'problemset' }"
              class="h-10 px-6 font-data text-xs uppercase tracking-wider border border-silver flex items-center justify-center gap-2 hover:bg-accent transition-colors"
            >
              {{ t('landing.browseProblems') }} <Code2 class="size-4" />
            </RouterLink>
          </div>

          <!-- Hero ASCII progress stats -->
          <div class="pt-4 space-y-2 max-w-sm">
            <div class="flex justify-between items-center text-[10px] font-data text-muted-foreground uppercase">
              <span>{{ t('landing.systemBootStatus') }}</span>
              <span>{{ t('landing.bootOk') }}</span>
            </div>
            <div class="ascii-progress flex items-center">
              <span class="ascii-progress-fill">[■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■]</span>
            </div>
          </div>
        </div>

        <!-- Interactive Hero Terminal (Right) -->
        <div class="lg:col-span-6">
          <div class="terminal-card border border-silver">
            <div class="terminal-card-header flex items-center justify-between">
              <span>ulticode@system:~</span>
              <div class="flex items-center gap-1.5">
                <span class="size-2 bg-red-500/30 border border-red-600/50"></span>
                <span class="size-2 bg-yellow-500/30 border border-yellow-600/50"></span>
                <span class="size-2 bg-green-500/30 border border-green-600/50"></span>
              </div>
            </div>
            
            <div class="p-4 space-y-4 min-h-[300px] bg-card text-xs">
              <div class="space-y-1 font-data">
                <p class="text-muted-foreground">{{ t('landing.shellActive') }}</p>
                <div class="flex items-center">
                  <span class="terminal-prompt"></span>
                  <span class="text-[var(--accent-electric)]">{{ heroCommand }}</span>
                  <span class="terminal-cursor"></span>
                </div>
              </div>

              <!-- Simulator Box -->
              <div class="border border-silver p-3 space-y-2 bg-[var(--surface-sunken)]">
                <div class="flex justify-between items-center border-b border-silver pb-2 mb-2">
                  <div class="flex gap-2">
                    <button 
                      v-for="lang in ['cpp', 'py', 'js']" 
                      :key="lang"
                      @click="selectedLang = lang"
                      :class="['px-2 py-0.5 font-data text-[10px] uppercase border transition-colors', selectedLang === lang ? 'border-[var(--accent-electric)] bg-[color-mix(in_oklch,var(--accent-electric)_15%,transparent)] text-[var(--accent-electric)]' : 'border-transparent text-muted-foreground hover:text-foreground']"
                    >
                      {{ lang }}
                    </button>
                  </div>
                  <button 
                    @click="runSimulation" 
                    :disabled="compiling"
                    class="px-3 py-1 font-data text-[10px] uppercase bg-primary text-primary-foreground hover:bg-primary/95 flex items-center gap-1 disabled:opacity-50"
                  >
                    <Play class="size-3" /> {{ compiling ? t('landing.running') : t('landing.runCode') }}
                  </button>
                </div>
                <pre class="font-data text-[10px] text-foreground leading-tight overflow-x-auto max-h-[140px] max-w-full"><code>{{ codeSnippets[selectedLang as keyof typeof codeSnippets] }}</code></pre>
              </div>

              <!-- Output Logs -->
              <div v-if="simulationTerminalText.length > 0" class="border border-silver p-3 bg-black/5 dark:bg-black/40 space-y-1 max-h-[120px] overflow-y-auto">
                <p 
                  v-for="(log, i) in simulationTerminalText" 
                  :key="i"
                  :class="['font-data text-[10px] leading-tight', log.includes('[PASS]') ? 'text-[var(--terminal-green)]' : log.includes('STATUS') ? 'text-[var(--accent-electric)]' : 'text-muted-foreground']"
                >
                  {{ log }}
                </p>
                <div v-if="showSuccessMsg" class="mt-2 p-2 bg-[color-mix(in_oklch,var(--terminal-green)_10%,transparent)] border border-[color-mix(in_oklch,var(--terminal-green)_25%,transparent)] flex items-center gap-2 text-[var(--terminal-green)] font-data text-[10px]">
                  <CheckCircle2 class="size-3.5 shrink-0" />
                  <span>{{ t('landing.compileSuccess') }}</span>
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
          <h2 class="text-2xl font-bold uppercase tracking-tight">{{ t('landing.modulesTitle') }}</h2>
          <p class="text-xs text-muted-foreground font-data">{{ t('landing.modulesSubtitle') }}</p>
        </div>

        <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          
          <!-- Card 1 -->
          <div class="precision-card border border-silver bg-card p-5 space-y-4">
            <div class="size-10 bg-[color-mix(in_oklch,var(--accent-electric)_15%,transparent)] flex items-center justify-center border border-[color-mix(in_oklch,var(--accent-electric)_30%,transparent)]">
              <Cpu class="size-5 text-[var(--accent-electric)]" />
            </div>
            <div class="space-y-2">
              <h3 class="font-data font-bold text-sm uppercase">{{ t('landing.judgeTitle') }}</h3>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t('landing.judgeDesc') }}
              </p>
            </div>
            <div class="text-[10px] font-data text-[var(--accent-electric)] uppercase tracking-wider flex items-center gap-1">
              {{ t('landing.judgeFooter') }} <span class="animate-pulse-subtle">●</span>
            </div>
          </div>

          <!-- Card 2 -->
          <div class="precision-card border border-silver bg-card p-5 space-y-4">
            <div class="size-10 bg-[color-mix(in_oklch,var(--terminal-green)_15%,transparent)] flex items-center justify-center border border-[color-mix(in_oklch,var(--terminal-green)_30%,transparent)]">
              <Trophy class="size-5 text-[var(--terminal-green)]" />
            </div>
            <div class="space-y-2">
              <h3 class="font-data font-bold text-sm uppercase">{{ t('landing.contestsTitle') }}</h3>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t('landing.contestsDesc') }}
              </p>
            </div>
            <div class="text-[10px] font-data text-[var(--terminal-green)] uppercase tracking-wider flex items-center gap-1">
              {{ t('landing.contestsFooter') }} <CheckCircle2 class="size-3" />
            </div>
          </div>

          <!-- Card 3 -->
          <div class="precision-card border border-silver bg-card p-5 space-y-4">
            <div class="size-10 bg-[color-mix(in_oklch,var(--terminal-amber)_15%,transparent)] flex items-center justify-center border border-[color-mix(in_oklch,var(--terminal-amber)_30%,transparent)]">
              <MessageSquare class="size-5 text-[var(--terminal-amber)]" />
            </div>
            <div class="space-y-2">
              <h3 class="font-data font-bold text-sm uppercase">{{ t('landing.feedTitle') }}</h3>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t('landing.feedDesc') }}
              </p>
            </div>
            <div class="text-[10px] font-data text-[var(--terminal-amber)] uppercase tracking-wider flex items-center gap-1">
              {{ t('landing.feedFooter') }}
            </div>
          </div>

          <!-- Card 4 -->
          <div class="precision-card border border-silver bg-card p-5 space-y-4">
            <div class="size-10 bg-[color-mix(in_oklch,var(--terminal-purple)_15%,transparent)] flex items-center justify-center border border-[color-mix(in_oklch,var(--terminal-purple)_30%,transparent)]">
              <Flame class="size-5 text-[var(--terminal-purple)]" />
            </div>
            <div class="space-y-2">
              <h3 class="font-data font-bold text-sm uppercase">{{ t('landing.badgesTitle') }}</h3>
              <p class="text-xs text-muted-foreground leading-relaxed">
                {{ t('landing.badgesDesc') }}
              </p>
            </div>
            <div class="text-[10px] font-data text-[var(--terminal-purple)] uppercase tracking-wider flex items-center gap-1">
              {{ t('landing.badgesFooter') }}
            </div>
          </div>

        </div>
      </section>

      <div class="terminal-separator"></div>

      <!-- System Telemetry (Live stats) -->
      <section class="grid gap-8 md:grid-cols-12 items-center">
        <div class="md:col-span-5 space-y-4">
          <h2 class="text-2xl font-bold uppercase tracking-tight">{{ t('landing.telemetryTitle') }}</h2>
          <p class="text-xs text-muted-foreground leading-relaxed font-data">
            {{ t('landing.telemetrySubtitle') }}
          </p>
          
          <div class="space-y-3 pt-2">
            <div class="space-y-1">
              <div class="flex justify-between text-[11px] font-data text-muted-foreground">
                <span>{{ t('landing.solverCapacity') }}</span>
                <span class="tabular-nums">84,912 / 100,000</span>
              </div>
              <div class="ascii-progress">
                <span class="ascii-progress-fill">[■■■■■■■■■■■■■■■■■■■■■■■■■□□□□□]</span>
              </div>
            </div>

            <div class="space-y-1">
              <div class="flex justify-between text-[11px] font-data text-muted-foreground">
                <span>{{ t('landing.latencyStability') }}</span>
                <span class="tabular-nums">99.86%</span>
              </div>
              <div class="ascii-progress">
                <span class="ascii-progress-fill">[■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■]</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Telemetry Data Display -->
        <div class="md:col-span-7">
          <div class="terminal-card border border-silver">
            <div class="terminal-card-header font-data flex justify-between">
              <span>{{ t('landing.logHeader') }}</span>
              <span class="text-[var(--terminal-green)] animate-pulse-subtle">LIVE</span>
            </div>
            <div class="p-4 bg-card font-data text-xs space-y-4">
              <div class="grid grid-cols-2 gap-4">
                <div class="border border-silver p-3 bg-[var(--surface-sunken)]">
                  <span class="terminal-kv-key">{{ t('landing.totalSubmissions') }}</span>
                  <p class="terminal-kv-value text-xl font-bold mt-1 text-[var(--accent-electric)]">1,348,902</p>
                </div>
                <div class="border border-silver p-3 bg-[var(--surface-sunken)]">
                  <span class="terminal-kv-key">{{ t('landing.activeSolvers24h') }}</span>
                  <p class="terminal-kv-value text-xl font-bold mt-1 text-[var(--terminal-green)]">42,918</p>
                </div>
                <div class="border border-silver p-3 bg-[var(--surface-sunken)]">
                  <span class="terminal-kv-key">{{ t('landing.compilationAvgMs') }}</span>
                  <p class="terminal-kv-value text-xl font-bold mt-1 text-[var(--terminal-amber)]">14.2 ms</p>
                </div>
                <div class="border border-silver p-3 bg-[var(--surface-sunken)]">
                  <span class="terminal-kv-key">{{ t('landing.contestsCompleted') }}</span>
                  <p class="terminal-kv-value text-xl font-bold mt-1 text-[var(--terminal-purple)]">142 {{ t('landing.eventsUnit') }}</p>
                </div>
              </div>

              <!-- Terminal style code status log -->
              <div class="border border-silver p-2.5 bg-[var(--surface-sunken)] text-[10px] text-muted-foreground space-y-0.5">
                <p><span class="text-[var(--terminal-cyan)]">[INFO]</span> 2026-06-02T23:38:04Z - {{ t('landing.logInfoInit') }}</p>
                <p><span class="text-[var(--terminal-cyan)]">[INFO]</span> 2026-06-02T23:38:05Z - {{ t('landing.logInfoSandbox') }}</p>
                <p><span class="text-[var(--terminal-green)]">[OK]</span> 2026-06-02T23:38:05Z - {{ t('landing.logOkSync') }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <div class="terminal-separator"></div>

      <!-- CTA Session Initialize -->
      <section class="terminal-card border border-silver bg-[var(--surface-sunken)] text-center py-12 px-6 max-w-3xl mx-auto space-y-6">
        <div class="space-y-2">
          <div class="text-[var(--accent-electric)] font-data text-xs uppercase tracking-widest">{{ t('landing.readyToLaunch') }}</div>
          <h2 class="text-3xl font-extrabold uppercase tracking-tight">{{ t('landing.ctaTitle') }}</h2>
          <p class="text-xs text-muted-foreground max-w-md mx-auto font-data">
            {{ t('landing.ctaDesc') }}
          </p>
        </div>

        <div class="flex justify-center gap-3">
          <button 
            @click="handleRegisterRedirect" 
            class="h-10 px-8 font-data text-xs uppercase tracking-wider bg-[var(--accent-electric)] text-white hover:bg-[var(--accent-electric)]/90 transition-all flex items-center gap-2"
          >
            {{ t('landing.createAccount') }} <ArrowRight class="size-4" />
          </button>
        </div>
      </section>

    </main>

    <!-- Footer -->
    <footer class="border-t border-silver bg-card py-8 mt-12 text-xs font-data text-muted-foreground">
      <div class="container mx-auto max-w-6xl px-4 flex flex-col md:flex-row items-center justify-between gap-4">
        <div>
          <span>{{ t('landing.copyright') }}</span>
        </div>
        <div class="flex gap-6">
          <a href="#" class="hover:text-foreground">{{ t('landing.apiDocs') }}</a>
          <a href="#" class="hover:text-foreground">{{ t('landing.repository') }}</a>
          <a href="#" class="hover:text-foreground">{{ t('landing.terms') }}</a>
          <a href="#" class="hover:text-foreground">{{ t('landing.status') }}</a>
        </div>
      </div>
    </footer>

  </div>
</template>

<style scoped>
/* Any custom scoped layout adjustments if needed */
</style>
