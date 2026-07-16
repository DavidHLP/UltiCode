/**
 * Intersection-driven section tracker for the landing page.
 *
 * Two outputs:
 *
 * - `activeId`: the id of the section whose entry is currently closest to the
 *   viewport center. Updates synchronously on scroll so the on-page step rail
 *   can paint without a one-frame delay.
 *
 * - `revealed`: a reactive Set of section ids that have crossed the threshold
 *   at least once. Sections stay revealed for the rest of the page lifetime so
 *   users who scroll fast still see the entrance transition.
 *
 * Honours `prefers-reduced-motion`: when set, the observer is never wired and
 * every requested id is treated as already revealed. The step rail still
 * updates, but no fade-in animation runs.
 */

import { nextTick, onBeforeUnmount, onMounted, ref, type Ref } from "vue";

export interface SectionObserverOptions {
  root?: Ref<HTMLElement | null>;
  threshold?: number;
  rootMargin?: string;
}

export interface SectionObserverApi {
  activeId: Ref<string | null>;
  revealed: Ref<ReadonlySet<string>>;
  register: (id: string, el: Element | null) => void;
}

const prefersReducedMotion = (): boolean =>
  typeof window !== "undefined" &&
  typeof window.matchMedia !== "undefined" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const supportsIntersectionObserver = (): boolean =>
  typeof window !== "undefined" &&
  typeof (window as { IntersectionObserver?: unknown }).IntersectionObserver ===
    "function";

export function useSectionObserver(
  options: SectionObserverOptions = {},
): SectionObserverApi {
  const { root, threshold = 0.35, rootMargin = "-20% 0px -40% 0px" } = options;

  const activeId = ref<string | null>(null);
  const revealed = ref<Set<string>>(new Set());
  const targets = new Map<string, HTMLElement>();
  const visibility = new Map<string, number>();

  let observer: IntersectionObserver | null = null;
  let rafId = 0;

  const pickActive = () => {
    let best: { id: string; ratio: number } | null = null;
    for (const [id, ratio] of visibility) {
      if (ratio <= 0) continue;
      if (!best || ratio > best.ratio) {
        best = { id, ratio };
      }
    }
    activeId.value = best?.id ?? null;
  };

  const ensureObserver = () => {
    if (observer || typeof window === "undefined") return;
    if (!supportsIntersectionObserver()) return;
    const Ctor = (window as unknown as { IntersectionObserver: typeof IntersectionObserver })
      .IntersectionObserver;
    observer = new Ctor(
      (entries) => {
        for (const entry of entries) {
          const id = (entry.target as HTMLElement).dataset.sectionId;
          if (!id) continue;
          visibility.set(id, entry.intersectionRatio);
          if (entry.isIntersecting) {
            revealed.value = new Set([...revealed.value, id]);
          }
        }
        if (rafId) cancelAnimationFrame(rafId);
        rafId = requestAnimationFrame(pickActive);
      },
      {
        threshold,
        rootMargin,
        root: root?.value ?? null,
      },
    );
  };

  const register = (id: string, el: Element | null) => {
    if (!(el instanceof HTMLElement)) {
      if (observer && targets.has(id)) {
        const existing = targets.get(id);
        if (existing) observer.unobserve(existing);
        targets.delete(id);
        visibility.delete(id);
      }
      return;
    }
    if (targets.has(id)) return;
    if (prefersReducedMotion() || !supportsIntersectionObserver()) {
      // Mutation must happen on the next tick. Register runs during ref
      // binding in render commit; mutating a reactive value there is a
      // guaranteed infinite render loop.
      void nextTick(() => {
        revealed.value = new Set([...revealed.value, id]);
      });
      return;
    }
    ensureObserver();
    targets.set(id, el);
    visibility.set(id, 0);
    observer?.observe(el);
  };

  onMounted(() => {
    ensureObserver();
  });

  onBeforeUnmount(() => {
    if (rafId) cancelAnimationFrame(rafId);
    observer?.disconnect();
    observer = null;
    targets.clear();
    visibility.clear();
  });

  return { activeId, revealed, register };
}
