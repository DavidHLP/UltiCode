import { onBeforeUnmount, onMounted, type Ref } from "vue";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

// Pins each ".luca-beat" section for one viewport of scroll so its content
// holds while the world canvas (driven by the same worldProgress) plays the
// matching 3D beat. Mirrors useLucaReveal: parent-driven, query-based, guarded.

export interface UseLucaBeatOptions {
  end?: string;
}

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const isMobile = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(max-width: 768px)").matches;

export function useLucaBeat(root: Ref<HTMLElement | null>, opts: UseLucaBeatOptions = {}): void {
  let triggers: ScrollTrigger[] = [];

  onMounted(() => {
    if (typeof window === "undefined") return;
    if (typeof window.matchMedia !== "function") return;
    if (prefersReducedMotion()) return;
    if (isMobile()) return;
    // registerPlugin inside the guard: ScrollTrigger.register eagerly calls
    // matchMedia, which jsdom lacks.
    gsap.registerPlugin(ScrollTrigger);
    const host = root.value;
    if (!host) return;
    const beats = host.querySelectorAll<HTMLElement>(".luca-beat");
    if (!beats.length) return;
    triggers = Array.from(beats).map((el) =>
      ScrollTrigger.create({
        trigger: el,
        start: "top top",
        end: opts.end ?? "+=100%",
        pin: true,
        pinSpacing: true,
        scrub: true,
        invalidateOnRefresh: true,
      }),
    );
    requestAnimationFrame(() => ScrollTrigger.refresh());
  });

  onBeforeUnmount(() => {
    triggers.forEach((t) => t.kill());
    triggers = [];
  });
}
