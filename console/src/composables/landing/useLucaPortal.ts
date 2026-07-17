/**
 * Landing-page intro portal state.
 *
 * Drives the aboutluca-style "Loading → Enter" sequence:
 *
 * - `progress` counts 0→100 on a rAF loop (~{@link DURATION_MS}). When the user
 *   has `prefers-reduced-motion: reduce`, it jumps straight to 100 so the
 *   ENTER control appears without a forced wait.
 * - `ready` flips true once progress reaches 100; the ENTER control is then
 *   focusable.
 * - `entered` flips true on `enter()`. The choice is persisted to
 *   `sessionStorage` so a repeat visit in the same tab skips the loader
 *   entirely instead of replaying the intro on every navigation.
 *
 * Body scroll is locked while the portal is visible and restored on exit,
 * including the unmount path, so a route change mid-intro cannot leave the
 * page scrolled/locked.
 *
 * All DOM access is guarded for SSR/jsdom so the composable is safe in unit
 * tests and during prerender.
 */

import { onBeforeUnmount, ref, type Ref } from "vue";

const STORAGE_KEY = "luca-entered";
const DURATION_MS = 1800;
const MIN_STEP_MS = 16;

export interface LucaPortalApi {
  progress: Ref<number>;
  ready: Ref<boolean>;
  entered: Ref<boolean>;
  enter: () => void;
  skip: () => void;
}

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const alreadyEntered = (): boolean => {
  if (typeof window === "undefined") return false;
  try {
    return window.sessionStorage.getItem(STORAGE_KEY) === "1";
  } catch {
    return false;
  }
};

const lockScroll = (lock: boolean) => {
  if (typeof document === "undefined") return;
  document.documentElement.style.overflow = lock ? "hidden" : "";
};

export function useLucaPortal(): LucaPortalApi {
  const progress = ref(0);
  const ready = ref(false);
  const entered = ref(false);

  let rafId = 0;
  let startTime = 0;
  let lastTick = 0;
  let cancelled = false;

  const finishProgress = () => {
    progress.value = 100;
    ready.value = true;
  };

  const persistEntered = () => {
    try {
      if (typeof window !== "undefined") {
        window.sessionStorage.setItem(STORAGE_KEY, "1");
      }
    } catch {
      /* sessionStorage unavailable (private mode) — non-fatal */
    }
  };

  const enter = () => {
    if (entered.value) return;
    cancelLoop();
    persistEntered();
    entered.value = true;
    lockScroll(false);
  };

  /** Keyboard / reduced-motion bypass: reveal ENTER immediately. */
  const skip = () => {
    cancelLoop();
    finishProgress();
  };

  const cancelLoop = () => {
    cancelled = true;
    if (rafId) {
      cancelAnimationFrame(rafId);
      rafId = 0;
    }
  };

  const tick = (now: number) => {
    if (cancelled) return;
    if (!startTime) {
      startTime = now;
      lastTick = now;
    }
    // Throttle DOM writes to one per frame, but only emit a value when enough
    // time has passed to keep the counter legible (avoids a blurry flicker).
    if (now - lastTick >= MIN_STEP_MS) {
      lastTick = now;
      const elapsed = now - startTime;
      const ratio = Math.min(elapsed / DURATION_MS, 1);
      // Ease-out so the counter decelerates toward 100 like a real loader.
      const eased = 1 - Math.pow(1 - ratio, 2);
      progress.value = Math.round(eased * 100);
    }
    if (now - (startTime || now) >= DURATION_MS) {
      finishProgress();
      return;
    }
    rafId = requestAnimationFrame(tick);
  };

  // SSR / test guard: with no window there is nothing to animate.
  if (typeof window === "undefined") {
    ready.value = true;
    progress.value = 100;
  } else if (alreadyEntered() || prefersReducedMotion()) {
    // Repeat visit or reduced motion: skip the timed loader. Repeat visits
    // also skip ENTER (they have already seen the intro this session).
    progress.value = 100;
    ready.value = true;
    entered.value = alreadyEntered();
  } else {
    lockScroll(true);
    rafId = requestAnimationFrame(tick);
  }

  onBeforeUnmount(() => {
    cancelLoop();
    lockScroll(false);
  });

  return { progress, ready, entered, enter, skip };
}
