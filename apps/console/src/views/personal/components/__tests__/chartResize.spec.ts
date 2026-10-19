import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { expect, it, vi } from "vitest";
import SkillRadarChart from "../SkillRadarChart.vue";
import SubmissionHistoryChart from "../SubmissionHistoryChart.vue";
import LearningProgressChart from "../LearningProgressChart.vue";

const { init, resize, dispose, setOption } = vi.hoisted(() => ({
  init: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
  setOption: vi.fn(),
}));
vi.mock("echarts", () => ({ init }));
vi.mock("@ulticode/design-system", () => ({ useChartPalette: () => ref({}) }));
vi.mock("../chartOptions", () => ({
  createAcceptedAreaGradient: () => ({}),
  withSafeChartAnimation: (option: unknown) => option,
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
vi.mock("@/api/submission", () => ({
  fetchSubmissionHistory: vi.fn().mockResolvedValue({
    monthly: [{ month: "2026-09", count: 1, accepted: 1 }],
    languages: [{ language: "java", count: 1 }],
  }),
  fetchLearningProgress: vi.fn().mockResolvedValue({
    weeklyProgress: [{ week: "2026-09-01", solved: 1, timeSpent: 0 }],
    difficultyProgress: [],
  }),
}));

it.each([
  {
    name: "skills",
    render: () =>
      mount(SkillRadarChart, {
        props: { skills: [{ tagName: "Arrays", tagSlug: "arrays", count: 1 }] },
      }),
  },
  { name: "submissions", render: () => mount(SubmissionHistoryChart) },
  { name: "learning", render: () => mount(LearningProgressChart) },
])(
  "keeps $name tooltip and canvas bounded on container changes",
  async ({ name, render }) => {
    vi.clearAllMocks();
    const observers: {
      callback: ResizeObserverCallback;
      disconnect: ReturnType<typeof vi.fn>;
    }[] = [];
    class Observer {
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = vi.fn();
      constructor(public callback: ResizeObserverCallback) {
        observers.push(this);
      }
    }
    vi.stubGlobal("ResizeObserver", Observer);
    init.mockReturnValue({ resize, dispose, setOption });
    const wrapper = render();
    try {
      await flushPromises();
      await vi.waitFor(() => expect(init).toHaveBeenCalled());
      expect(setOption.mock.calls.at(-1)![0].tooltip.confine).toBe(true);
      if (name === "learning") {
        expect(setOption.mock.calls.at(-1)![0].grid.top).toBe(32);
        const axes = setOption.mock.calls.at(-1)![0].yAxis;
        expect(axes[0].minInterval).toBe(1);
        expect(
          axes.map(
            (axis: { nameTextStyle: { align: string } }) =>
              axis.nameTextStyle.align,
          ),
        ).toEqual(["left", "right"]);
      }
      expect(observers.length).toBeGreaterThan(0);
      observers
        .at(-1)!
        .callback([], observers.at(-1)! as unknown as ResizeObserver);
      expect(resize).toHaveBeenCalledOnce();
    } finally {
      wrapper.unmount();
      vi.unstubAllGlobals();
    }
    expect(observers.at(-1)!.disconnect).toHaveBeenCalled();
    expect(dispose).toHaveBeenCalled();
  },
);
