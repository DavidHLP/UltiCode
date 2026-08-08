import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount, flushPromises } from "@vue/test-utils";
import { nextTick } from "vue";
import SubmissionPerformancePanel from "../SubmissionPerformancePanel.vue";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (!params) return key;
      return Object.entries(params).reduce(
        (acc, [name, value]) => acc.replace(`{${name}}`, String(value)),
        key,
      );
    },
  }),
}));

interface ChartHandle {
  setOption: ReturnType<typeof vi.fn>;
  resize: ReturnType<typeof vi.fn>;
  dispose: ReturnType<typeof vi.fn>;
  isDisposed: () => boolean;
}

const charts: ChartHandle[] = [];
const initMock = vi.fn((el: HTMLElement) => {
  const handle: ChartHandle = {
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(() => {
      handle.isDisposed = () => true;
    }),
    isDisposed: () => false,
  };
  (el as HTMLElement & { __chart?: ChartHandle }).__chart = handle;
  charts.push(handle);
  return handle as unknown as ReturnType<typeof Object>;
});

vi.mock("echarts", () => {
  return {
    color: {
      modifyAlpha: (_input: string, alpha: number) => `muted:${alpha}`,
    },
    init: (el: HTMLElement) => initMock(el),
  };
});

const mountedWrappers: ReturnType<typeof mount>[] = [];

function mountPanel(props: Record<string, unknown> = {}) {
  const wrapper = mount(SubmissionPerformancePanel, {
    props: {
      runtimePoints: [],
      totalRuntimeCount: 0,
      runtimeHighlightIndex: -1,
      memoryPoints: [],
      totalMemoryCount: 0,
      memoryHighlightIndex: -1,
      runtime: undefined,
      memory: undefined,
      runtimePercentile: undefined,
      memoryPercentile: undefined,
      avatarUrl:
        "https://assets.leetcode.cn/aliyun-lc-upload/default_avatar.png",
      ...props,
    },
    attachTo: document.body,
  });
  mountedWrappers.push(wrapper);
  return wrapper;
}

class FakeResizeObserver {
  cb: ResizeObserverCallback;
  observed = new Set<Element>();
  disconnected = false;
  constructor(cb: ResizeObserverCallback) {
    this.cb = cb;
  }
  observe(target: Element) {
    this.observed.add(target);
  }
  unobserve(target: Element) {
    this.observed.delete(target);
  }
  disconnect() {
    this.observed.clear();
    this.disconnected = true;
  }
  trigger(entry: Partial<ResizeObserverEntry>) {
    if (this.disconnected) return;
    const target = entry.target ?? (this.observed.values().next().value as Element);
    this.cb(
      [
        {
          target,
          contentRect: { width: 100, height: 100, top: 0, left: 0, bottom: 100, right: 100, x: 0, y: 0 } as DOMRectReadOnly,
          borderBoxSize: [] as unknown as ReadonlyArray<ResizeObserverSize>,
          contentBoxSize: [] as unknown as ReadonlyArray<ResizeObserverSize>,
          devicePixelContentBoxSize: [] as unknown as ReadonlyArray<ResizeObserverSize>,
        } as ResizeObserverEntry,
      ],
      this as unknown as ResizeObserver,
    );
  }
}

let observerInstances: FakeResizeObserver[] = [];

beforeEach(() => {
  charts.length = 0;
  initMock.mockClear();
  observerInstances = [];
  (globalThis as unknown as { ResizeObserver: typeof FakeResizeObserver }).ResizeObserver =
    class {
      cb: ResizeObserverCallback;
      constructor(cb: ResizeObserverCallback) {
        this.cb = cb;
        const inst = new FakeResizeObserver(cb);
        observerInstances.push(inst);
        return inst;
      }
    } as unknown as typeof FakeResizeObserver;
  (globalThis as unknown as { Image: typeof Image }).Image = class {
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;
    crossOrigin = "";
    src = "";
    set srcValue(value: string) {
      this.src = value;
      queueMicrotask(() => {
        if (this.onload) this.onload();
      });
    }
  } as unknown as typeof Image;
});

afterEach(() => {
  while (mountedWrappers.length) {
    const w = mountedWrappers.pop();
    w?.unmount();
  }
});

describe("SubmissionPerformancePanel — chart lifecycle", () => {
  it("initializes both runtime and memory ECharts after mount", async () => {
    mountPanel();

    await nextTick();
    await flushPromises();

    expect(initMock).toHaveBeenCalledTimes(2);
    expect(charts).toHaveLength(2);
  });

  it("disposes every ECharts instance and observer when unmounted", async () => {
    const wrapper = mountPanel();

    await nextTick();
    await flushPromises();

    const runtimeDiv = wrapper.find('[data-testid="runtime-chart"]').element as HTMLElement & {
      __chart?: ChartHandle;
    };
    const memoryDiv = wrapper.find('[data-testid="memory-chart"]').element as HTMLElement & {
      __chart?: ChartHandle;
    };

    const beforeDispose = charts.length;
    expect(beforeDispose).toBe(2);
    expect(runtimeDiv.__chart).toBeDefined();
    expect(memoryDiv.__chart).toBeDefined();
    expect(observerInstances).toHaveLength(2);
    expect(observerInstances[0].disconnected).toBe(false);

    wrapper.unmount();

    expect(runtimeDiv.__chart?.isDisposed()).toBe(true);
    expect(memoryDiv.__chart?.isDisposed()).toBe(true);
    charts.forEach((chart) => expect(chart.dispose).toHaveBeenCalled());
    expect(observerInstances[0].disconnected).toBe(true);
    expect(observerInstances[1].disconnected).toBe(true);
  });

  it("clears ECharts instances before reinitializing on runtime toggle", async () => {
    const wrapper = mountPanel();

    await nextTick();
    await flushPromises();

    const runtimeDiv = wrapper.find('[data-testid="runtime-chart"]').element as HTMLElement & {
      __chart?: ChartHandle;
    };

    const initialChart = runtimeDiv.__chart;
    expect(initialChart).toBeDefined();

    const tabs = wrapper.findAll('[role="button"]');
    const memoryTab = tabs.find((tab) =>
      tab.text().toLowerCase().includes("memory"),
    );
    expect(memoryTab).toBeDefined();
    await memoryTab!.trigger("click");

    await nextTick();
    await flushPromises();

    expect(initialChart!.dispose).toHaveBeenCalled();
  });

  it("forwards ResizeObserver events to the active chart's resize()", async () => {
    const wrapper = mountPanel();
    await nextTick();
    await flushPromises();

    const runtimeChart = (
      wrapper.find('[data-testid="runtime-chart"]').element as HTMLElement & {
        __chart?: ChartHandle;
      }
    ).__chart!;
    const memoryChart = (
      wrapper.find('[data-testid="memory-chart"]').element as HTMLElement & {
        __chart?: ChartHandle;
      }
    ).__chart!;

    const runtimeObserver = observerInstances[0];
    runtimeObserver.trigger({
      target: wrapper.find('[data-testid="runtime-chart"]').element,
    });

    expect(runtimeChart.resize).toHaveBeenCalled();
    expect(memoryChart.resize).not.toHaveBeenCalled();
  });

  it("stale avatar callbacks cannot mutate disposed charts after unmount", async () => {
    const wrapper = mountPanel();
    await nextTick();
    await flushPromises();

    const runtimeChart = (
      wrapper.find('[data-testid="runtime-chart"]').element as HTMLElement & {
        __chart?: ChartHandle;
      }
    ).__chart!;
    const setOptionCallsBefore = runtimeChart.setOption.mock.calls.length;

    const pendingImage = {
      src: "",
      onload: null as null | (() => void),
    };
    queueMicrotask(() => {
      pendingImage.onload?.();
    });

    const originalSetOption = runtimeChart.setOption;
    runtimeChart.setOption = vi.fn(() => {
      throw new Error("setOption called on disposed chart");
    });

    wrapper.unmount();

    await flushPromises();

    runtimeChart.setOption = originalSetOption;
    expect(runtimeChart.setOption.mock.calls.length).toBe(setOptionCallsBefore);
  });
});

describe("SubmissionPerformancePanel — presentation formatting", () => {
  it("formats runtime and memory values for the header strip", () => {
    const wrapper = mountPanel({
      runtime: 42.6,
      memory: 99.4,
      runtimePercentile: 87.4,
      memoryPercentile: 53.2,
    });

    const text = wrapper.text();

    expect(text).toContain("43 ms");
    expect(text).toContain("99.4 MB");
  });

  it("renders placeholder values when runtime / memory are missing", () => {
    const wrapper = mountPanel();

    const text = wrapper.text();

    expect(text).toContain("-- ms");
    expect(text).toContain("-- MB");
  });

  it("formats values at the >=100MB boundary as integers", () => {
    const wrapper = mountPanel({
      runtime: 0,
      memory: 100.4,
    });

    expect(wrapper.text()).toContain("100 MB");
  });
});