import {
  onBeforeUnmount,
  onMounted,
  shallowRef,
  watch,
  type Ref,
  type ShallowRef,
} from "vue";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import Lenis from "lenis";

// Static imports are safe because vite.config.ts `resolve.dedupe` forces a
// single vue copy across the graph (otherwise the lockfile's transitive
// vue@3.5.38 would split from the app's 3.5.34 and corrupt slot rendering).

export interface UseLucaScrollOptions {
  locked?: Ref<boolean>;
  world?: Ref<HTMLElement | null>;
  worldProgress?: Ref<number>;
}

export interface UseLucaScrollApi {
  // ShallowRef: a Lenis instance is a third-party class with private fields;
  // Vue's deep UnwrapRef would mangle its type, so we hold it shallowly.
  lenis: ShallowRef<Lenis | null>;
  refresh: () => void;
  scrollTo: (target: string | HTMLElement) => void;
}

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

export function useLucaScroll(opts: UseLucaScrollOptions = {}): UseLucaScrollApi {
  const lenis = shallowRef<Lenis | null>(null);
  let worldST: ScrollTrigger | null = null;
  let stopWatcher: ReturnType<typeof watch> | undefined;
  let tickerFn: ((time: number) => void) | undefined;

  onMounted(() => {
    if (typeof window === "undefined") return;
    if (typeof window.matchMedia !== "function") return;
    if (prefersReducedMotion()) return;

    // registerPlugin runs here (not at module top): ScrollTrigger.register
    // eagerly calls matchMedia, which jsdom lacks — a top-level register would
    // throw at import time and break the whole test module.
    gsap.registerPlugin(ScrollTrigger);

    const l = new Lenis({ lerp: 0.1, smoothWheel: true, anchors: false });
    lenis.value = l;
    l.on("scroll", ScrollTrigger.update);
    tickerFn = (time: number) => l.raf(time * 1000);
    gsap.ticker.add(tickerFn);
    gsap.ticker.lagSmoothing(0);

    if (opts.locked?.value) l.stop();
    stopWatcher = watch(
      () => opts.locked?.value ?? false,
      (v) => (v ? l.stop() : l.start()),
    );

    if (opts.world?.value && opts.worldProgress) {
      worldST = ScrollTrigger.create({
        trigger: opts.world.value,
        start: "top top",
        end: "bottom bottom",
        scrub: 0.4,
        onUpdate: (self) => {
          opts.worldProgress!.value = self.progress;
        },
      });
    }
    requestAnimationFrame(() => ScrollTrigger.refresh());
  });

  onBeforeUnmount(() => {
    worldST?.kill();
    stopWatcher?.();
    const l = lenis.value;
    if (l && tickerFn) {
      gsap.ticker.remove(tickerFn);
      l.destroy();
    }
    lenis.value = null;
  });

  return {
    lenis,
    refresh: () => ScrollTrigger.refresh(),
    scrollTo: (target) => lenis.value?.scrollTo(target),
  };
}
