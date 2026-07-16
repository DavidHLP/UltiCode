/**
 * Lightweight scroll-progress hook.
 *
 * Tracks how far the user has scrolled down a scrollable container
 * (defaults to `window`) on a passive scroll listener with `requestAnimationFrame`
 * coalescing. Emits a value in `[0, 1]`.
 *
 * The reducer intentionally uses `scrollHeight - viewport` so a short page
 * (no vertical scroll) reports `1` instead of `NaN`.
 */

import { onBeforeUnmount, onMounted, ref, type Ref } from "vue";

export function useScrollProgress(
  target: Ref<HTMLElement | Window | null> = ref<HTMLElement | null>(null),
): Ref<number> {
  const progress = ref(0);
  let rafId = 0;

  const compute = () => {
    const node = target.value ?? (typeof window === "undefined" ? null : window);
    if (!node) {
      progress.value = 1;
      return;
    }
    if (node === window && typeof window === "undefined") {
      progress.value = 1;
      return;
    }
    const isWindow = node === window;
    const scrollTop = isWindow
      ? window.scrollY || document.documentElement.scrollTop
      : (node as HTMLElement).scrollTop;
    const scrollHeight = isWindow
      ? document.documentElement.scrollHeight
      : (node as HTMLElement).scrollHeight;
    const viewport = isWindow ? window.innerHeight : (node as HTMLElement).clientHeight;
    const denom = Math.max(scrollHeight - viewport, 1);
    const ratio = scrollTop / denom;
    progress.value = ratio <= 0 ? 0 : ratio >= 1 ? 1 : ratio;
  };

  const onScroll = () => {
    if (rafId) return;
    rafId = requestAnimationFrame(() => {
      rafId = 0;
      compute();
    });
  };

  onMounted(() => {
    if (typeof window === "undefined") return;
    compute();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll, { passive: true });
  });

  onBeforeUnmount(() => {
    if (typeof window === "undefined") return;
    if (rafId) cancelAnimationFrame(rafId);
    window.removeEventListener("scroll", onScroll);
    window.removeEventListener("resize", onScroll);
  });

  return progress;
}
