import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import { createI18n } from "vue-i18n";
import { describe, expect, it, vi } from "vitest";
import ActivityHeatmap from "./ActivityHeatmap.vue";
import SkillRadarChart from "./SkillRadarChart.vue";
import zhPersonal from "@/i18n/locales/zh-CN/personal";
import enPersonal from "@/i18n/locales/en-US/personal";
import zhCommon from "@/i18n/locales/zh-CN/common";
import enCommon from "@/i18n/locales/en-US/common";

vi.mock("@/utils/datetime", () => ({ formatDate: (date: string) => date }));

describe("dashboard chart language", () => {
  it("updates weekdays, activity hints, legend and empty skills when language changes", async () => {
    const i18n = createI18n({
      legacy: false,
      locale: "en-US",
      messages: {
        "en-US": { personal: enPersonal, common: enCommon },
        "zh-CN": { personal: zhPersonal, common: zhCommon },
      },
    });
    const heatmap = mount(ActivityHeatmap, {
      props: { data: [] },
      global: { plugins: [i18n] },
    });
    const radar = mount(SkillRadarChart, {
      props: { skills: [] },
      global: { plugins: [i18n] },
    });
    try {
      const dates = heatmap
        .findAll("[title]")
        .slice(0, 5)
        .map((cell) => cell.attributes("title")!.split(": ")[0]!);
      await heatmap.setProps({
        data: dates.map((date, level) => ({ date, level })),
      });
      expect(heatmap.text()).toContain("Sun");
      expect(heatmap.text()).toContain("Less");
      for (const [index, label] of [
        "No activity",
        "1–2 submissions",
        "3–5 submissions",
        "6–9 submissions",
        "10+ submissions",
      ].entries()) {
        expect(
          heatmap.findAll("[title]")[index]!.attributes("title"),
        ).toContain(label);
      }
      expect(radar.text()).toContain(enPersonal.skills.noSkills);

      i18n.global.locale.value = "zh-CN";
      await nextTick();
      expect(heatmap.text()).toContain("周日");
      expect(heatmap.text()).toContain("少");
      expect(heatmap.text()).toContain("多");
      expect(heatmap.findAll("[title]")[0]!.attributes("title")).toContain(
        "无活动",
      );
      expect(heatmap.findAll("[title]")[4]!.attributes("title")).toContain(
        "10 次及以上提交",
      );
      expect(radar.text()).toContain(zhPersonal.skills.noSkills);
      expect(radar.text()).not.toContain(enPersonal.skills.noSkills);
    } finally {
      heatmap.unmount();
      radar.unmount();
    }
  });
});
