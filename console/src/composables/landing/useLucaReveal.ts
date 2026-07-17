import { onBeforeUnmount, onMounted, type Ref } from "vue";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

// Static import is safe under vite.config.ts `resolve.dedupe` (single vue copy).

const REVEAL_SELECTOR = "[data-luca-reveal], .luca-stagger, .luca-line";

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

export function useLucaReveal(root: Ref<HTMLElement | null>): void {
  let batch: ScrollTrigger[] | undefined;

  onMounted(() => {
    if (typeof window === "undefined") return;
    if (typeof window.matchMedia !== "function" || prefersReducedMotion()) return;
    // registerPlugin runs here (not at module top): ScrollTrigger.register
    // eagerly calls matchMedia, which jsdom lacks.
    gsap.registerPlugin(ScrollTrigger);
    const host = root.value;
    if (!host) return;
    const targets = host.querySelectorAll<HTMLElement>(REVEAL_SELECTOR);
    if (!targets.length) return;
    batch = ScrollTrigger.batch(targets, {
      start: "top 90%",
      once: true,
      onEnter: (els) => els.forEach((el) => el.classList.add("is-revealed")),
    });
  });

  onBeforeUnmount(() => {
    batch?.forEach((trigger) => trigger.kill());
  });
}
