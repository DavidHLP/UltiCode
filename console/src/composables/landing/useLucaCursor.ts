/**
 * Decorative pointer-follower for the aboutluca-style landing.
 *
 * Moves a single fixed dot (`el`) toward the pointer with rAF coalescing and
 * enlarges it while hovering interactive elements. The effect is purely
 * cosmetic: the dot is `aria-hidden`, `pointer-events: none`, and the whole
 * feature is disabled unless the pointer is fine (i.e. a real mouse) and the
 * user has not requested reduced motion. On touch / reduced-motion the hook
 * reports `active: false` so the view skips wiring the system-cursor override.
 */

import { onBeforeUnmount, onMounted, ref, type Ref } from "vue";

const INTERACTIVE_SELECTOR =
  'a, button, [role="button"], [data-luca-cursor-hover]';

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const hasFinePointer = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(hover: hover) and (pointer: fine)").matches;

export interface LucaCursorApi {
  active: Ref<boolean>;
}

export function useLucaCursor(el: Ref<HTMLElement | null>): LucaCursorApi {
  const active = ref(false);

  let rafId = 0;
  let targetX = 0;
  let targetY = 0;
  let currentX = 0;
  let currentY = 0;

  const onMove = (event: PointerEvent) => {
    targetX = event.clientX;
    targetY = event.clientY;
    if (!rafId) {
      rafId = requestAnimationFrame(loop);
    }
  };

  const onOver = (event: PointerEvent) => {
    const node = el.value;
    if (!node) return;
    const interactive = (event.target as HTMLElement | null)?.closest(
      INTERACTIVE_SELECTOR,
    );
    node.classList.toggle("is-hover", Boolean(interactive));
  };

  const loop = () => {
    const node = el.value;
    if (!node) {
      rafId = 0;
      return;
    }
    // Ease toward the target for a soft trailing feel.
    currentX += (targetX - currentX) * 0.22;
    currentY += (targetY - currentY) * 0.22;
    node.style.transform = `translate(${currentX}px, ${currentY}px) translate(-50%, -50%)`;
    if (Math.abs(targetX - currentX) > 0.5 || Math.abs(targetY - currentY) > 0.5) {
      rafId = requestAnimationFrame(loop);
    } else {
      rafId = 0;
    }
  };

  onMounted(() => {
    if (typeof window === "undefined") return;
    if (prefersReducedMotion() || !hasFinePointer()) return;
    const node = el.value;
    if (!node) return;
    active.value = true;
    node.classList.add("is-active");
    window.addEventListener("pointermove", onMove, { passive: true });
    window.addEventListener("pointerover", onOver, { passive: true });
  });

  onBeforeUnmount(() => {
    if (rafId) cancelAnimationFrame(rafId);
    rafId = 0;
    if (typeof window === "undefined") return;
    window.removeEventListener("pointermove", onMove);
    window.removeEventListener("pointerover", onOver);
  });

  return { active };
}
