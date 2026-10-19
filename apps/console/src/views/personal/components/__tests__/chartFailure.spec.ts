import { flushPromises, mount } from "@vue/test-utils";
import { ref } from "vue";
import { expect, it, vi } from "vitest";
import {
  fetchLearningProgress,
  fetchSubmissionHistory,
} from "@/api/submission";
import SubmissionHistoryChart from "../SubmissionHistoryChart.vue";
import LearningProgressChart from "../LearningProgressChart.vue";

const { init, setOption } = vi.hoisted(() => ({
  init: vi.fn(),
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
  fetchSubmissionHistory: vi.fn(),
  fetchLearningProgress: vi.fn(),
}));

it.each([
  {
    component: SubmissionHistoryChart,
    fetch: fetchSubmissionHistory,
    section: "history",
    empty: "noHistory",
    data: {
      monthly: [{ month: "2026-09", count: 1, accepted: 1 }],
      languages: [],
    },
    emptyData: { monthly: [], languages: [] },
  },
  {
    component: LearningProgressChart,
    fetch: fetchLearningProgress,
    section: "learning",
    empty: "noProgress",
    data: {
      weeklyProgress: [{ week: "2026-09-01", solved: 1, timeSpent: 0 }],
      difficultyProgress: [],
    },
    emptyData: { weeklyProgress: [], difficultyProgress: [] },
  },
])(
  "distinguishes $section failure, retry and valid empty results",
  async ({ component, fetch, section, empty, data, emptyData }) => {
    vi.clearAllMocks();
    const request = vi.mocked(fetch);
    request.mockRejectedValueOnce(new Error("unavailable"));
    init.mockReturnValue({ setOption, resize: vi.fn(), dispose: vi.fn() });
    const wrapper = mount(component);
    try {
      await flushPromises();
      expect(wrapper.get('[role="alert"]').text()).toContain(
        `personal.${section}.loadError`,
      );
      expect(wrapper.text()).not.toContain(`personal.${section}.${empty}`);
      request.mockRejectedValueOnce(new Error("still unavailable"));
      await wrapper.get("button").trigger("click");
      await flushPromises();
      expect(wrapper.find('[role="alert"]').exists()).toBe(true);
      request.mockResolvedValueOnce(data as never);
      await wrapper.get("button").trigger("click");
      await flushPromises();
      await vi.waitFor(() => expect(setOption).toHaveBeenCalled());
      expect(wrapper.find('[role="alert"]').exists()).toBe(false);
      expect(request).toHaveBeenCalledTimes(3);
    } finally {
      wrapper.unmount();
    }
    request.mockResolvedValueOnce(emptyData as never);
    const emptyWrapper = mount(component);
    try {
      await flushPromises();
      expect(emptyWrapper.text()).toContain(`personal.${section}.${empty}`);
      expect(emptyWrapper.find('[role="alert"]').exists()).toBe(false);
    } finally {
      emptyWrapper.unmount();
    }
  },
);
