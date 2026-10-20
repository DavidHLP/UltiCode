import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { expect, it, vi } from "vitest";
import SubmissionHistoryChart from "../SubmissionHistoryChart.vue";

const { init, setOption, dispose } = vi.hoisted(() => ({
  init: vi.fn(),
  setOption: vi.fn(),
  dispose: vi.fn(),
}));
vi.mock("echarts", () => ({ init }));
vi.mock("@ulticode/design-system", () => ({
  useChartPalette: () => ref({}),
}));
vi.mock("../chartOptions", () => ({
  createAcceptedAreaGradient: () => ({}),
  withSafeChartAnimation: (option: unknown) => option,
}));
vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("@/api/submission", () => ({
  fetchSubmissionHistory: vi.fn().mockResolvedValue({
    monthly: [{ month: "2026-09", count: 1, accepted: 1 }],
    languages: [{ language: "java", count: 1 }],
  }),
}));

it("reserves two legend rows below both plots and uses integer count ticks", async () => {
  init.mockReturnValue({ setOption, dispose, resize: vi.fn() });
  const wrapper = mount(SubmissionHistoryChart);
  try {
    await flushPromises();
    await vi.waitFor(() => expect(setOption).toHaveBeenCalledOnce());
    const option = setOption.mock.calls[0]![0];
    expect(option.grid).toHaveLength(2);
    for (const grid of option.grid) expect(grid.bottom).toBe(64);
    expect(option.xAxis[1].minInterval).toBe(1);
    expect(option.yAxis[0].minInterval).toBe(1);
    expect(option.legend.data).toHaveLength(3);
  } finally {
    wrapper.unmount();
  }
  expect(dispose).toHaveBeenCalled();
});
