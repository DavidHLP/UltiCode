import { describe, it, expect, vi } from "vitest";
import { mount } from "@vue/test-utils";
import LandingView from "../LandingView.vue";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}));

describe("LandingView", () => {
  it("embeds the static landing mirror full-viewport", () => {
    const wrapper = mount(LandingView);

    const frame = wrapper.find("iframe.landing-frame");
    expect(frame.exists()).toBe(true);
    expect(frame.attributes("src")).toBe("/landing/index.html");
    expect(frame.attributes("title")).toBe("common.landingFrameTitle");
    expect(frame.attributes("allow")).toContain("autoplay");
  });
});
