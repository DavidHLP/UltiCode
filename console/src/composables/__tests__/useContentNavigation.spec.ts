import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { computed, defineComponent, h, nextTick, ref } from "vue";
import { mount } from "@vue/test-utils";
import { useContentNavigation, type UseContentNavigationReturn } from "../useContentNavigation";

interface MockIntersectionObserverEntry {
  target: Element;
  isIntersecting: boolean;
}

type IntersectionCallback = (entries: MockIntersectionObserverEntry[]) => void;

class MockIntersectionObserver {
  static instances: MockIntersectionObserver[] = [];

  callback: IntersectionCallback;
  options: IntersectionObserverInit | undefined;
  observed: Element[] = [];
  disconnected = false;

  constructor(callback: IntersectionCallback, options?: IntersectionObserverInit) {
    this.callback = callback;
    this.options = options;
    MockIntersectionObserver.instances.push(this);
  }

  observe(el: Element): void {
    this.observed.push(el);
  }

  unobserve(el: Element): void {
    this.observed = this.observed.filter((e) => e !== el);
  }

  disconnect(): void {
    this.disconnected = true;
    this.observed = [];
  }

  takeRecords(): MockIntersectionObserverEntry[] {
    return [];
  }

  trigger(entries: Partial<MockIntersectionObserverEntry>[]): void {
    this.callback(entries as MockIntersectionObserverEntry[]);
  }
}

class MockResizeObserver {
  static instances: MockResizeObserver[] = [];

  callback: ResizeObserverCallback;
  observed: Element[] = [];
  disconnected = false;

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    MockResizeObserver.instances.push(this);
  }

  observe(el: Element): void {
    this.observed.push(el);
  }

  unobserve(el: Element): void {
    this.observed = this.observed.filter((e) => e !== el);
  }

  disconnect(): void {
    this.disconnected = true;
    this.observed = [];
  }

  trigger(width: number): void {
    const entries = this.observed.map((target) => ({
      target,
      contentRect: { width, height: 0, top: 0, left: 0, bottom: 0, right: 0, x: 0, y: 0, toJSON() {} },
      borderBoxSize: [] as unknown as readonly ResizeObserverSize[],
      contentBoxSize: [] as unknown as readonly ResizeObserverSize[],
      devicePixelContentBoxSize: [] as unknown as readonly ResizeObserverSize[],
    }));
    this.callback(entries, this);
  }
}

interface Harness {
  wrapper: ReturnType<typeof mount>;
  host: HTMLElement;
  nav: UseContentNavigationReturn;
}

function mountNav(headings: Parameters<typeof useContentNavigation>[0]): Harness {
  const host = document.createElement("div");
  host.id = "nav-host";
  document.body.appendChild(host);
  let navRef!: UseContentNavigationReturn;
  let renderedEl: HTMLElement | null = null;
  const Comp = defineComponent({
    setup() {
      const nav = useContentNavigation(headings, { observeDebounceMs: 0 });
      navRef = nav;
      return () =>
        h("div", {
          ref: (el: Element | null) => {
            renderedEl = el as HTMLElement | null;
            nav.wrapperRef.value = el as HTMLElement | null;
          },
        });
    },
  });
  const wrapper = mount(Comp, { attachTo: host });
  return {
    wrapper,
    host: renderedEl ?? host,
    get nav() {
      return navRef;
    },
  };
}

function cleanupHarness(h: Harness, extraElements: Element[] = []): void {
  h.wrapper.unmount();
  for (const el of extraElements) {
    el.parentElement?.removeChild(el);
  }
  h.host.parentElement?.removeChild(h.host);
}

describe("useContentNavigation", () => {
  beforeEach(() => {
    MockIntersectionObserver.instances = [];
    MockResizeObserver.instances = [];
    (globalThis as unknown as { IntersectionObserver: typeof MockIntersectionObserver }).IntersectionObserver =
      MockIntersectionObserver;
    (globalThis as unknown as { ResizeObserver: typeof MockResizeObserver }).ResizeObserver =
      MockResizeObserver;
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns responsive state defaults", () => {
    const headings = ref<{ id: string; text: string; level: number }[]>([]);
    const harness = mountNav(headings);
    expect(harness.nav.wrapperRef.value).toBe(harness.host);
    expect(harness.nav.activeHeadingId.value).toBe("");
    expect(harness.nav.isWideLayout.value).toBe(false);
    expect(harness.nav.containerWidth.value).toBe(0);
    expect(harness.nav.showMobileTOC.value).toBe(false);
    cleanupHarness(harness);
  });

  it("sets up an IntersectionObserver for the current headings once mounted", async () => {
    const el1 = document.createElement("h2");
    el1.id = "one";
    const el2 = document.createElement("h2");
    el2.id = "two";
    document.body.appendChild(el1);
    document.body.appendChild(el2);

    const headings = ref<{ id: string; text: string; level: number }[]>([
      { id: "one", text: "One", level: 2 },
      { id: "two", text: "Two", level: 2 },
    ]);

    const harness = mountNav(headings);
    await nextTick();
    await vi.runAllTimersAsync();

    expect(MockIntersectionObserver.instances).toHaveLength(1);
    const obs = MockIntersectionObserver.instances[0]!;
    expect(obs.observed).toContain(el1);
    expect(obs.observed).toContain(el2);

    cleanupHarness(harness, [el1, el2]);
  });

  it("updates activeHeadingId when an intersection entry fires", async () => {
    const el1 = document.createElement("h2");
    el1.id = "alpha";
    const el2 = document.createElement("h2");
    el2.id = "beta";
    document.body.appendChild(el1);
    document.body.appendChild(el2);

    const headings = ref([
      { id: "alpha", text: "Alpha", level: 2 },
      { id: "beta", text: "Beta", level: 2 },
    ]);

    const harness = mountNav(headings);
    await nextTick();
    await vi.runAllTimersAsync();

    const obs = MockIntersectionObserver.instances[0]!;
    obs.trigger([{ target: el2, isIntersecting: true }]);
    expect(harness.nav.activeHeadingId.value).toBe("beta");

    obs.trigger([{ target: el1, isIntersecting: true }]);
    expect(harness.nav.activeHeadingId.value).toBe("alpha");

    cleanupHarness(harness, [el1, el2]);
  });

  it("rebuilds observers when headings change (does not stack observers)", async () => {
    const el1 = document.createElement("h2");
    el1.id = "first";
    const el2 = document.createElement("h2");
    el2.id = "second";
    document.body.appendChild(el1);
    document.body.appendChild(el2);

    const headings = ref([
      { id: "first", text: "First", level: 2 },
    ]);

    const harness = mountNav(headings);
    await nextTick();
    await vi.runAllTimersAsync();
    expect(MockIntersectionObserver.instances).toHaveLength(1);
    const firstObs = MockIntersectionObserver.instances[0]!;
    expect(firstObs.disconnected).toBe(false);

    headings.value = [
      { id: "first", text: "First", level: 2 },
      { id: "second", text: "Second", level: 2 },
    ];
    await nextTick();
    await vi.runAllTimersAsync();

    expect(MockIntersectionObserver.instances).toHaveLength(2);
    expect(firstObs.disconnected).toBe(true);
    const secondObs = MockIntersectionObserver.instances[1]!;
    expect(secondObs.observed).toContain(el2);

    cleanupHarness(harness, [el1, el2]);
  });

  it("observes the wrapper element with a ResizeObserver and toggles isWideLayout at threshold", async () => {
    const headings = ref<{ id: string; text: string; level: number }[]>([]);
    const harness = mountNav(headings);
    await nextTick();
    await vi.runAllTimersAsync();

    expect(MockResizeObserver.instances).toHaveLength(1);
    const resizeObs = MockResizeObserver.instances[0]!;
    expect(resizeObs.observed).toContain(harness.host);

    resizeObs.trigger(500);
    await nextTick();
    expect(harness.nav.isWideLayout.value).toBe(false);

    resizeObs.trigger(1200);
    await nextTick();
    expect(harness.nav.isWideLayout.value).toBe(true);

    resizeObs.trigger(800);
    await nextTick();
    expect(harness.nav.isWideLayout.value).toBe(false);

    cleanupHarness(harness);
  });

  it("disconnects all observers on unmount (no leaks)", async () => {
    const headings = ref<{ id: string; text: string; level: number }[]>([
      { id: "x", text: "X", level: 2 },
    ]);
    const harness = mountNav(headings);
    await nextTick();
    await vi.runAllTimersAsync();

    const io = MockIntersectionObserver.instances[0]!;
    const ro = MockResizeObserver.instances[0]!;

    cleanupHarness(harness);

    expect(io.disconnected).toBe(true);
    expect(ro.disconnected).toBe(true);
  });

  it("scrollToHeading uses getElementById when the heading element exists", async () => {
    const el = document.createElement("h2");
    el.id = "found";
    const scrollSpy = vi.fn();
    el.scrollIntoView = scrollSpy;
    document.body.appendChild(el);

    const headings = ref([{ id: "found", text: "Found", level: 2 }]);
    const harness = mountNav(headings);
    await nextTick();

    harness.nav.scrollToHeading("found");
    expect(scrollSpy).toHaveBeenCalledWith({ behavior: "smooth", block: "start" });
    expect(harness.nav.activeHeadingId.value).toBe("found");

    cleanupHarness(harness, [el]);
  });

  it("scrollToHeading falls back to the resolver when no DOM id is found", async () => {
    const fallback = document.createElement("strong");
    fallback.textContent = "Lead In:";
    const scrollSpy = vi.fn();
    fallback.scrollIntoView = scrollSpy;
    document.body.appendChild(fallback);

    const headings = ref([
      { id: "lead-in", text: "Lead In", level: 3, exists: false },
    ]);
    const resolver = vi.fn(() => fallback);

    const host = document.createElement("div");
    document.body.appendChild(host);
    let navRef!: UseContentNavigationReturn;
    const Comp = defineComponent({
      setup() {
        const nav = useContentNavigation(headings, {
          observeDebounceMs: 0,
          resolveFallbackElement: resolver,
        });
        navRef = nav;
        (nav.wrapperRef as unknown as { value: HTMLElement | null }).value = host;
        return () => h("div");
      },
    });
    const wrapper = mount(Comp, { attachTo: host });
    await nextTick();

    navRef.scrollToHeading("lead-in");
    expect(resolver).toHaveBeenCalledWith(headings.value[0]);
    expect(scrollSpy).toHaveBeenCalled();
    expect(navRef.activeHeadingId.value).toBe("lead-in");

    wrapper.unmount();
    document.body.removeChild(fallback);
    host.parentElement?.removeChild(host);
  });

  it("scrollToHeading is a no-op when neither getElementById nor the resolver find a target", async () => {
    const headings = ref<{ id: string; text: string; level: number }[]>([
      { id: "ghost", text: "Ghost", level: 2 },
    ]);
    const harness = mountNav(headings);
    await nextTick();

    expect(() => harness.nav.scrollToHeading("ghost")).not.toThrow();
    expect(harness.nav.activeHeadingId.value).toBe("");

    cleanupHarness(harness);
  });

  it("closeMobileTOC resets showMobileTOC", async () => {
    const headings = ref<{ id: string; text: string; level: number }[]>([]);
    const harness = mountNav(headings);
    await nextTick();

    harness.nav.showMobileTOC.value = true;
    harness.nav.closeMobileTOC();
    expect(harness.nav.showMobileTOC.value).toBe(false);

    cleanupHarness(harness);
  });

  it("supports computed headings in addition to plain refs", async () => {
    const el = document.createElement("h2");
    el.id = "comp";
    document.body.appendChild(el);

    const headings = computed(() => [{ id: "comp", text: "Comp", level: 2 }]);
    const harness = mountNav(headings);
    await nextTick();
    await vi.runAllTimersAsync();

    expect(MockIntersectionObserver.instances[0]?.observed).toContain(el);

    cleanupHarness(harness, [el]);
  });
});
