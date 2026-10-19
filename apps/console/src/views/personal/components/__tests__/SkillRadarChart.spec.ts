import { mount } from "@vue/test-utils";
import { nextTick, ref } from "vue";
import { createI18n } from "vue-i18n";
import { describe, expect, it, vi } from "vitest";
import enPersonal from "@/i18n/locales/en-US/personal";
import enProblem from "@/i18n/locales/en-US/problem";
import zhPersonal from "@/i18n/locales/zh-CN/personal";
import zhProblem from "@/i18n/locales/zh-CN/problem";
import SkillRadarChart from "../SkillRadarChart.vue";

const createTestI18n = () =>
  createI18n({
    legacy: false,
    locale: "en-US",
    messages: {
      "en-US": { personal: enPersonal, problem: enProblem },
      "zh-CN": { personal: zhPersonal, problem: zhProblem },
    },
  });

const { init, dispose, setOption } = vi.hoisted(() => ({
  init: vi.fn(),
  dispose: vi.fn(),
  setOption: vi.fn(),
}));

vi.mock("echarts", () => ({ init }));
vi.mock("@ulticode/design-system", () => ({
  useChartPalette: () => ref({}),
}));

describe("SkillRadarChart", () => {
  it("shows translated radar values without HTML interpretation or callback indicators", async () => {
    vi.clearAllMocks();
    init.mockReturnValue({ dispose, setOption, resize: vi.fn() });
    const i18n = createTestI18n();
    const wrapper = mount(SkillRadarChart, {
      props: {
        skills: [
          { tagName: "分治", tagSlug: "divide-and-conquer", count: 2 },
          {
            tagName: '<img src=x onerror="alert(1)"> & <script>bad()</script>',
            tagSlug: "custom-untrusted",
            count: 1,
          },
        ],
      },
      global: { plugins: [i18n] },
    });
    try {
      const option = setOption.mock.calls[0]![0];
      expect(
        option.radar.indicator.map(({ name }: { name: string }) => name),
      ).toEqual([
        "Divide and Conquer",
        '<img src=x onerror="alert(1)"> & <script>bad()</script>',
      ]);
      const tooltip = option.tooltip.formatter({
        componentType: "series",
        seriesType: "radar",
        value: [2, 1],
      });
      expect(tooltip).toBeInstanceOf(HTMLElement);
      expect(tooltip.textContent).toBe(
        'Divide and Conquer: 2<img src=x onerror="alert(1)"> & <script>bad()</script>: 1',
      );
      expect(tooltip.children).toHaveLength(2);
      expect(tooltip.querySelector("img, script")).toBeNull();
      expect(option.tooltip.confine).toBe(true);
      i18n.global.locale.value = "zh-CN";
      await nextTick();
      const localizedOption = setOption.mock.calls.at(-1)![0];
      expect(
        localizedOption.radar.indicator.map(
          ({ name }: { name: string }) => name,
        ),
      ).toEqual([
        "分治",
        '<img src=x onerror="alert(1)"> & <script>bad()</script>',
      ]);
    } finally {
      wrapper.unmount();
      vi.clearAllMocks();
    }
  });

  it("initializes when skills arrive before the loading view closes", async () => {
    vi.clearAllMocks();
    init.mockReturnValue({ dispose, setOption, resize: vi.fn() });
    const wrapper = mount(SkillRadarChart, {
      props: { skills: [], loading: true },
      global: { plugins: [createTestI18n()] },
    });
    try {
      await wrapper.setProps({
        skills: [{ tagName: "Arrays", tagSlug: "arrays", count: 2 }],
      });
      expect(init).not.toHaveBeenCalled();
      await wrapper.setProps({ loading: false });
      expect(init).toHaveBeenCalledOnce();
      expect(setOption).toHaveBeenCalledOnce();
    } finally {
      wrapper.unmount();
    }
  });
});
