<script setup lang="ts">
import { ref, onMounted, onUnmounted } from "vue";
import { Trophy, X, Sparkles } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const props = defineProps<{
  badgeName: string;
  badgeDescription: string;
  points: number;
  onClose?: () => void;
}>();

const visible = ref(false);
const showConfetti = ref(false);
let autoCloseTimer: ReturnType<typeof setTimeout> | null = null;
let fadeOutTimer: ReturnType<typeof setTimeout> | null = null;

onMounted(() => {
  visible.value = true;
  showConfetti.value = true;

  autoCloseTimer = setTimeout(() => {
    close();
  }, 5000);
});

onUnmounted(() => {
  if (autoCloseTimer !== null) clearTimeout(autoCloseTimer);
  if (fadeOutTimer !== null) clearTimeout(fadeOutTimer);
});

function close() {
  visible.value = false;
  fadeOutTimer = setTimeout(() => {
    props.onClose?.();
  }, 300);
}

const confettiPieces = Array.from({ length: 20 }, (_, i) => ({
  id: i,
  left: Math.random() * 100,
  delay: Math.random() * 0.5,
  duration: 1 + Math.random() * 0.5,
  color: [
    "var(--status-warning-mark)",
    "var(--status-error-mark)",
    "var(--status-info-mark)",
    "var(--accent-primary)",
    "var(--status-success-mark)",
  ][Math.floor(Math.random() * 5)],
}));
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-300"
      enter-from-class="translate-y-full opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition-all duration-300"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="translate-y-full opacity-0"
    >
      <div
        v-if="visible"
        class="fixed bottom-4 right-4 z-50 max-w-sm overflow-hidden rounded-none border border-[var(--status-warning-mark)] bg-surface-elevated shadow-float"
      >
        <!-- Confetti -->
        <div
          v-if="showConfetti"
          class="absolute inset-0 overflow-hidden pointer-events-none"
        >
          <div
            v-for="piece in confettiPieces"
            :key="piece.id"
            :class="cn('absolute h-2 w-2 animate-bounce')"
            :style="{
              left: `${piece.left}%`,
              top: '-10px',
              backgroundColor: piece.color,
              borderRadius: Math.random() > 0.5 ? '50%' : '0',
              animationDelay: `${piece.delay}s`,
              animationDuration: `${piece.duration}s`,
            }"
          />
        </div>

        <!-- Content -->
        <div class="relative p-4">
          <div class="flex items-start gap-3">
            <!-- Icon -->
            <div
              class="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-[var(--status-warning-mark)] bg-status-warning-surface shadow-float"
            >
              <Trophy class="h-6 w-6 text-status-warning-mark" />
            </div>

            <!-- Text -->
            <div class="flex-1 space-y-1">
              <div class="flex items-center gap-2">
                <Sparkles class="h-4 w-4 text-status-warning-mark" />
                <span class="text-sm font-semibold text-foreground">
                  Achievement Unlocked!
                </span>
              </div>
              <h4 class="font-bold text-foreground-strong">
                {{ badgeName }}
              </h4>
              <p class="text-sm text-muted-foreground">
                {{ badgeDescription }}
              </p>
              <span
                class="inline-block rounded-full border border-status-warning-mark bg-status-warning-surface px-2 py-0.5 text-xs font-medium text-foreground-strong"
              >
                +{{ points }} points
              </span>
            </div>

            <!-- Close button -->
            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8 shrink-0"
              @click="close"
            >
              <X class="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
