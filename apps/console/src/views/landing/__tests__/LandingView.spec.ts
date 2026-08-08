import { describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { i18n } from "../../../i18n";

// jsdom cannot run the WebGL experience — mock the entry module.
vi.mock("../../experience/main.js", () => ({
  initLandingExperience: vi.fn(() => () => {}),
}));

import LandingView from "../LandingView.vue";

const RouterLinkStub = {
  props: ["to"],
  template: '<a :href="to"><slot /></a>',
};

describe("LandingView", () => {
  it("renders the UltiCode landing DOM skeleton", () => {
    i18n.global.locale.value = "zh-CN";

    const wrapper = mount(LandingView, {
      global: {
        plugins: [i18n],
        stubs: { RouterLink: RouterLinkStub },
      },
    });

    // loader
    const loader = wrapper.find(".loader");
    expect(loader.exists()).toBe(true);
    expect(loader.find("i").text()).toContain("加载中");
    expect(loader.find("a").text()).toContain("进入");
    expect(loader.find("span").exists()).toBe(true);

    // canvas mount point
    expect(wrapper.find(".canvas").exists()).toBe(true);

    // header
    expect(wrapper.find("header.header .header__lnk").text()).toBe("UltiCode");
    const cta = wrapper.find("header.header .header__lnk--2");
    expect(cta.text()).toBe("开始刷题");
    expect(cta.attributes("href")).toBe("/problemset");

    // scroll track: 24 steps
    expect(wrapper.find("#scroller").exists()).toBe(true);
    expect(wrapper.find("#content").exists()).toBe(true);
    expect(wrapper.findAll(".steps .step")).toHaveLength(24);

    // awards: 6 feature cards with data-title 1-6
    const awards = wrapper.findAll("#awards img");
    expect(awards).toHaveLength(6);
    awards.forEach((img, i) => {
      expect(img.attributes("data-title")).toBe(String(i + 1));
    });

    // footer: quick links + audio toggle
    const links = wrapper.findAll(".footer__social-lnk");
    expect(links).toHaveLength(3);
    expect(links.map((l) => l.attributes("href"))).toEqual([
      "/problemset",
      "/contest",
      "/forum",
    ]);
    const audioToggle = wrapper.find(".footer__audio-toggle.js-audio-toggle");
    expect(audioToggle.exists()).toBe(true);
    expect(audioToggle.attributes("aria-label")).toBe("关闭声音");
    expect(wrapper.find(".js-audio-wave").exists()).toBe(true);

    wrapper.unmount();
  });
});
