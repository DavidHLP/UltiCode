/**
 * Scroll-reveal wiring for the aboutluca-style landing.
 *
 * Observes every descendant of `root` that opts into the reveal animation
 * (any element carrying `[data-luca-reveal]`, `.luca-stagger`, or `.luca-line`)
 * and adds `.is-revealed` the first time it crosses the viewport. The CSS in
 * `landing-luca.css` drives the actual transform/opacity; this hook only
 * toggles the class so the animation stays declarative and theme-agnostic.
 *
 * Under `prefers-reduced-motion: reduce` the observer is not wired — the CSS
 * forces every reveal element visible, so the page is never blank.
 */

import { onBeforeUnmount, onMounted, type Ref } from "vue";

const REVEAL_SELECTOR = "[data-luca-reveal], .luca-stagger, .luca-line";

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const supportsIntersectionObserver = (): boolean =>
  typeof window !== "undefined" &&
  typeof (window as { IntersectionObserver?: unknown }).IntersectionObserver ===
    "function";

export function useLucaReveal(root: Ref<HTMLElement | null>): void {
  let observer: IntersectionObserver | null = null;

  onMounted(() => {
    if (typeof window === "undefined") return;
    if (prefersReducedMotion() || !supportsIntersectionObserver()) return;
    const host = root.value;
    if (!host) return;

    const Ctor = (window as unknown as { IntersectionObserver: typeof IntersectionObserver })
      .IntersectionObserver;
    observer = new Ctor(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          entry.target.classList.add("is-revealed");
          observer?.unobserve(entry.target);
        }
      },
      { threshold: 0.18, rootMargin: "0px 0px -8% 0px" },
    );

    host.querySelectorAll<HTMLElement>(REVEAL_SELECTOR).forEach((el) => {
      observer?.observe(el);
    });
  });

  onBeforeUnmount(() => {
    observer?.disconnect();
    observer = null;
  });
}
