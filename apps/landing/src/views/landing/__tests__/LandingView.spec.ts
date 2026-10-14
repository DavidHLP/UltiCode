import { ref } from "vue";
import { mount, shallowMount } from "@vue/test-utils";
import { describe, expect, it, vi, beforeEach } from "vitest";
import LandingView from "../LandingView.vue";
import LandingHeader from "../components/LandingHeader.vue";
import HeroSection from "../components/HeroSection.vue";
import ProductProofSection from "../components/ProductProofSection.vue";
import UseCasesSection from "../components/UseCasesSection.vue";
import HumanControlSection from "../components/HumanControlSection.vue";
import FinalStorySection from "../components/FinalStorySection.vue";
import LandingFooter from "../components/LandingFooter.vue";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: ref("zh-CN"),
  }),
}));

const mockToggleLocale = vi.hoisted(() => vi.fn());

vi.mock("@/composables/useLocale", () => ({
  useLocale: () => ({
    t: (key: string) => key,
    locale: ref("zh-CN"),
    toggleLocale: mockToggleLocale,
  }),
}));

describe("Landing Page Component Suite", () => {
  beforeEach(() => {
    mockToggleLocale.mockClear();
    document.title = "";
    Object.defineProperty(window, "scrollY", {
      configurable: true,
      value: 0,
    });
  });

  it("mounts LandingView, manages dynamic SEO and OpenGraph tags, and cleans them up on unmount", () => {
    document.title = "Original App Title";
    // Ensure no pre-existing tags
    document.head
      .querySelectorAll(
        'meta[name="description"], meta[property^="og:"], link[rel="canonical"]',
      )
      .forEach((el) => el.remove());

    const wrapper = shallowMount(LandingView);
    expect(wrapper.find(".ulticode-landing-root").exists()).toBe(true);
    expect(document.title).toBe("landing.seoTitle");

    const metaDesc = document.querySelector('meta[name="description"]');
    expect(metaDesc?.getAttribute("content")).toBe("landing.seoDescription");

    const ogTitle = document.querySelector('meta[property="og:title"]');
    expect(ogTitle?.getAttribute("content")).toBe("landing.seoTitle");

    const ogDesc = document.querySelector('meta[property="og:description"]');
    expect(ogDesc?.getAttribute("content")).toBe("landing.seoDescription");

    const ogImage = document.querySelector('meta[property="og:image"]');
    expect(ogImage?.getAttribute("content")).toContain("algorithmic-garden");

    const canonical = document.querySelector('link[rel="canonical"]');
    expect(canonical).not.toBeNull();

    wrapper.unmount();
    expect(document.title).toBe("Original App Title");
    expect(document.querySelector('meta[name="description"]')).toBeNull();
    expect(document.querySelector('meta[property="og:title"]')).toBeNull();
    expect(
      document.querySelector('meta[property="og:description"]'),
    ).toBeNull();
    expect(document.querySelector('meta[property="og:image"]')).toBeNull();
    expect(document.querySelector('link[rel="canonical"]')).toBeNull();
  });

  it("restores pre-existing meta tags when unmounting", () => {
    document.title = "App Title";
    const existingMetaDesc = document.createElement("meta");
    existingMetaDesc.setAttribute("name", "description");
    existingMetaDesc.setAttribute("content", "Pre-existing description");
    document.head.appendChild(existingMetaDesc);

    const existingOgTitle = document.createElement("meta");
    existingOgTitle.setAttribute("property", "og:title");
    existingOgTitle.setAttribute("content", "Pre-existing OG Title");
    document.head.appendChild(existingOgTitle);

    const wrapper = shallowMount(LandingView);
    expect(existingMetaDesc.getAttribute("content")).toBe(
      "landing.seoDescription",
    );
    expect(existingOgTitle.getAttribute("content")).toBe("landing.seoTitle");

    wrapper.unmount();
    expect(existingMetaDesc.getAttribute("content")).toBe(
      "Pre-existing description",
    );
    expect(existingOgTitle.getAttribute("content")).toBe(
      "Pre-existing OG Title",
    );
    existingMetaDesc.remove();
    existingOgTitle.remove();
  });

  it("renders navigation, locale switching and an accessible mobile Sheet", async () => {
    const wrapper = mount(LandingHeader, { attachTo: document.body });
    expect(wrapper.get("header > div > a").text()).toContain("UltiCode");
    expect(wrapper.findAll("nav a")).toHaveLength(3);
    const localeButton = wrapper
      .findAll("button")
      .find((button) => button.text() === "EN");
    await localeButton!.trigger("click");
    expect(mockToggleLocale).toHaveBeenCalledOnce();
    await wrapper.get('button[aria-haspopup="dialog"]').trigger("click");
    await new Promise((resolve) => setTimeout(resolve, 20));
    const dialog = document.body.querySelector('[role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog?.getAttribute("aria-labelledby")).toBeTruthy();
    expect(dialog?.querySelectorAll("nav a")).toHaveLength(4);
    dialog!.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
    );
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(document.body.querySelector('[role="dialog"]')).toBeNull();
    wrapper.unmount();
  });

  it("preserves hero content, platform CTAs and explicitly labeled sample telemetry", async () => {
    const wrapper = mount(HeroSection);
    expect(wrapper.get("h1").text()).toBe(
      "landing.hero.title landing.hero.titleItalic",
    );
    expect(wrapper.get("code").text()).toContain("class LRUCache");
    expect(wrapper.text()).toContain("landing.hero.sampleFootnote");
    const links = wrapper.findAll("a").map((link) => link.attributes("href"));
    expect(links.some((link) => link?.endsWith("/problemset"))).toBe(true);
    expect(links.some((link) => link?.endsWith("/contest"))).toBe(true);
    await wrapper
      .findAll('[role="tab"]')[1]
      .trigger("keydown", { key: "Enter" });
    expect(wrapper.text()).toContain("3,840 KiB");
    expect(wrapper.text()).toContain("48 / 48");
    wrapper.unmount();
  });

  it("preserves all four workflows and their disclosure", () => {
    const wrapper = mount(ProductProofSection);
    expect(wrapper.findAll("ol > li")).toHaveLength(4);
    for (let step = 1; step <= 4; step++) {
      expect(wrapper.text()).toContain(`landing.workflow.step${step}Title`);
      expect(wrapper.text()).toContain(`landing.workflow.step${step}Bullet2`);
    }
    expect(wrapper.text()).toContain("landing.proof.disclosure");
  });

  it("preserves three use cases, metrics and platform links", () => {
    const wrapper = mount(UseCasesSection);
    expect(wrapper.findAll('[data-slot="card"]')).toHaveLength(3);
    expect(wrapper.findAll("a")).toHaveLength(3);
    for (let index = 1; index <= 3; index++) {
      expect(wrapper.text()).toContain(`landing.useCases.case${index}Title`);
      expect(wrapper.text()).toContain(`landing.useCases.case${index}Metric2`);
    }
  });

  it("preserves both human-control commitments", () => {
    const wrapper = mount(HumanControlSection);
    expect(wrapper.findAll('[data-slot="card"]')).toHaveLength(2);
    expect(wrapper.text()).toContain("landing.governance.item1Text");
    expect(wrapper.text()).toContain("landing.governance.item2Text");
  });

  it("preserves the closing narrative and both CTA destinations", () => {
    const wrapper = mount(FinalStorySection);
    expect(wrapper.get("h2").text()).toContain("landing.finalStory.headline");
    expect(wrapper.findAll("a").map((link) => link.attributes("href"))).toEqual(
      expect.arrayContaining([
        expect.stringMatching(/\/problemset$/),
        expect.stringMatching(/\/login$/),
      ]),
    );
  });

  it("preserves all footer columns and their twelve links", () => {
    const wrapper = mount(LandingFooter);
    expect(wrapper.findAll("nav")).toHaveLength(3);
    expect(wrapper.findAll("nav a")).toHaveLength(12);
    expect(wrapper.text()).toContain("landing.footer.copyright");
    expect(wrapper.text()).toContain("landing.footer.manifesto");
  });
});
