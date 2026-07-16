import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
  type ComputedRef,
  type Ref,
} from "vue";

export interface ContentHeading {
  id: string;
  text: string;
  level: number;
  /**
   * Whether the heading maps to a real DOM heading element. When `false`,
   * the consumer expects `resolveFallbackElement` to provide an alternate
   * scroll target (for example a forum bold lead-in mapped to a `<strong>`).
   */
  exists?: boolean;
}

export interface UseContentNavigationOptions {
  /** Pixel threshold above which the wide (sticky sidebar) layout applies. */
  wideThreshold?: number;
  /** Debounce window (ms) applied before re-observing after heading changes. */
  observeDebounceMs?: number;
  /**
   * Resolves a fallback DOM target for headings that do not have a real
   * heading element (e.g. forum bold lead-ins). Called only when
   * `getElementById` does not find a match.
   */
  resolveFallbackElement?: (heading: ContentHeading) => HTMLElement | null;
  /**
   * Optional hook fired after `scrollToHeading` scrolls the resolved
   * element. Receives the resolved element so callers can apply
   * view-specific decorations (e.g. a transient highlight ring).
   */
  onAfterScroll?: (el: HTMLElement, heading: ContentHeading | null) => void;
  /** Optional `scrollIntoView` override, primarily for tests. */
  scrollIntoView?: (el: HTMLElement, id: string) => void;
}

export interface UseContentNavigationReturn {
  wrapperRef: Ref<HTMLElement | null>;
  activeHeadingId: Ref<string>;
  containerWidth: Ref<number>;
  isWideLayout: ComputedRef<boolean>;
  showMobileTOC: Ref<boolean>;
  scrollToHeading: (id: string) => void;
  closeMobileTOC: () => void;
}

type HeadingsSource =
  | ComputedRef<readonly ContentHeading[]>
  | Ref<readonly ContentHeading[]>;

function readHeadings(source: HeadingsSource): readonly ContentHeading[] {
  const value = source.value;
  return Array.isArray(value) ? value : [];
}

export function useContentNavigation(
  headings: HeadingsSource,
  options: UseContentNavigationOptions = {},
): UseContentNavigationReturn {
  const wideThreshold = options.wideThreshold ?? 900;
  const observeDebounceMs = options.observeDebounceMs ?? 250;

  const wrapperRef = ref<HTMLElement | null>(null);
  const activeHeadingId = ref<string>("");
  const containerWidth = ref(0);
  const isWideLayout = computed(() => containerWidth.value >= wideThreshold);
  const showMobileTOC = ref(false);

  let intersectionObserver: IntersectionObserver | null = null;
  let resizeObserver: ResizeObserver | null = null;
  let pendingObserveHandle: ReturnType<typeof setTimeout> | null = null;
  let mounted = false;

  const teardownIntersectionObserver = (): void => {
    if (intersectionObserver) {
      intersectionObserver.disconnect();
      intersectionObserver = null;
    }
  };

  const teardownResizeObserver = (): void => {
    if (resizeObserver) {
      resizeObserver.disconnect();
      resizeObserver = null;
    }
  };

  const setupIntersectionObserver = (): void => {
    teardownIntersectionObserver();
    if (!mounted || typeof IntersectionObserver === "undefined") return;

    intersectionObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && entry.target instanceof HTMLElement) {
            activeHeadingId.value = entry.target.id;
          }
        }
      },
      {
        root: null,
        rootMargin: "-80px 0px -50% 0px",
        threshold: 0.1,
      },
    );

    const list = readHeadings(headings);
    const ids = new Set(list.map((heading) => heading.id));
    if (activeHeadingId.value && !ids.has(activeHeadingId.value)) {
      activeHeadingId.value = '';
    }
    for (const heading of list) {
      if (!heading.id) continue;
      const el = document.getElementById(heading.id);
      if (el) intersectionObserver?.observe(el);
    }
  };

  const scheduleObserve = (): void => {
    if (pendingObserveHandle !== null) {
      clearTimeout(pendingObserveHandle);
    }
    pendingObserveHandle = setTimeout(() => {
      pendingObserveHandle = null;
      setupIntersectionObserver();
    }, observeDebounceMs);
  };

  const setupResizeObserver = (): void => {
    teardownResizeObserver()
    if (!mounted || typeof ResizeObserver === "undefined" || !wrapperRef.value) return

    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const width = entry.contentRect?.width ?? 0
        containerWidth.value = width
      }
    })
    resizeObserver.observe(wrapperRef.value)
  }

  onMounted(() => {
    mounted = true
    scheduleObserve()
    setupResizeObserver()
  })

  onBeforeUnmount(() => {
    mounted = false;
    if (pendingObserveHandle !== null) {
      clearTimeout(pendingObserveHandle);
      pendingObserveHandle = null;
    }
    teardownIntersectionObserver();
    teardownResizeObserver();
  });

  watch(wrapperRef, () => {
    if (mounted) setupResizeObserver()
  }, { flush: 'post' })

  watch(
    () => readHeadings(headings).map((h) => `${h.id}:${h.text}:${h.level}`).join('|'),
    () => {
      if (mounted) scheduleObserve()
    },
  )

  const defaultScrollIntoView = (el: HTMLElement): void => {
    el.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const scrollToHeading = (id: string): void => {
    if (!id) return;

    const heading = readHeadings(headings).find((h) => h.id === id) ?? null;
    let target: HTMLElement | null = document.getElementById(id);
    if (!target && options.resolveFallbackElement && heading) {
      target = options.resolveFallbackElement(heading);
    }
    if (!target) return;

    if (options.scrollIntoView) {
      options.scrollIntoView(target, id);
    } else {
      defaultScrollIntoView(target);
    }

    activeHeadingId.value = id;
    options.onAfterScroll?.(target, heading);
  };

  const closeMobileTOC = (): void => {
    showMobileTOC.value = false;
  };

  return {
    wrapperRef,
    activeHeadingId,
    containerWidth,
    isWideLayout,
    showMobileTOC,
    scrollToHeading,
    closeMobileTOC,
  };
}
