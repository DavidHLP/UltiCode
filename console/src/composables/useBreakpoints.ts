import { ref, onMounted, onUnmounted, computed, readonly } from "vue";

export type Breakpoint = "xs" | "sm" | "md" | "lg" | "xl" | "2xl";

export interface Breakpoints {
  xs: number;
  sm: number;
  md: number;
  lg: number;
  xl: number;
  "2xl": number;
}

const defaultBreakpoints: Breakpoints = {
  xs: 0,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  "2xl": 1536,
};

/**
 * Composable for responsive breakpoint detection
 *
 * @example
 * ```ts
 * const { current, isMobile, isTablet, isDesktop } = useBreakpoints()
 *
 * if (isMobile.value) {
 *   // Show mobile layout
 * }
 * ```
 */
export function useBreakpoints(breakpoints: Breakpoints = defaultBreakpoints) {
  const windowWidth = ref<number>(
    typeof window !== "undefined" ? window.innerWidth : 1024,
  );

  const current = computed<Breakpoint>(() => {
    const width = windowWidth.value;
    if (width < breakpoints.sm) return "xs";
    if (width < breakpoints.md) return "sm";
    if (width < breakpoints.lg) return "md";
    if (width < breakpoints.xl) return "lg";
    if (width < breakpoints["2xl"]) return "xl";
    return "2xl";
  });

  const isXs = computed(() => current.value === "xs");
  const isSm = computed(() => current.value === "sm");
  const isMd = computed(() => current.value === "md");
  const isLg = computed(() => current.value === "lg");
  const isXl = computed(() => current.value === "xl");
  const is2xl = computed(() => current.value === "2xl");

  const isMobile = computed(() => windowWidth.value < breakpoints.md);
  const isTablet = computed(
    () =>
      windowWidth.value >= breakpoints.md && windowWidth.value < breakpoints.lg,
  );
  const isDesktop = computed(() => windowWidth.value >= breakpoints.lg);

  const isGreaterOrEqual = (breakpoint: Breakpoint) => {
    return computed(() => windowWidth.value >= breakpoints[breakpoint]);
  };

  const isLessThan = (breakpoint: Breakpoint) => {
    return computed(() => windowWidth.value < breakpoints[breakpoint]);
  };

  const handleResize = () => {
    windowWidth.value = window.innerWidth;
  };

  onMounted(() => {
    if (typeof window !== "undefined") {
      window.addEventListener("resize", handleResize);
      handleResize();
    }
  });

  onUnmounted(() => {
    if (typeof window !== "undefined") {
      window.removeEventListener("resize", handleResize);
    }
  });

  return {
    /** Current breakpoint name */
    current: readonly(current),
    /** Current window width in pixels */
    width: readonly(windowWidth),
    /** True if screen is < 640px */
    isXs,
    /** True if screen is >= 640px and < 768px */
    isSm,
    /** True if screen is >= 768px and < 1024px */
    isMd,
    /** True if screen is >= 1024px and < 1280px */
    isLg,
    /** True if screen is >= 1280px and < 1536px */
    isXl,
    /** True if screen is >= 1536px */
    is2xl,
    /** True if screen is < 768px (xs or sm) */
    isMobile,
    /** True if screen is >= 768px and < 1024px */
    isTablet,
    /** True if screen is >= 1024px */
    isDesktop,
    /** Returns a computed that is true if width >= given breakpoint */
    isGreaterOrEqual,
    /** Returns a computed that is true if width < given breakpoint */
    isLessThan,
  };
}
