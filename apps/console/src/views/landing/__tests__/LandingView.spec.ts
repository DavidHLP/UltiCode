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

vi.mock("vue-router", () => ({
  RouterLink: {
    name: "RouterLink",
    props: ["to"],
    template:
      "<a :href=\"typeof to === 'string' ? to : to.path || ''\"><slot /></a>",
  },
  useRoute: () => ({ params: {}, query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => key,
    locale: { value: "zh-CN" },
  }),
}));

describe("Landing Page Component Suite", () => {
  beforeEach(() => {
    document.title = "";
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

  it("renders LandingHeader with navigation items, brand mark, and handles mobile drawer & Escape key", async () => {
    const wrapper = mount(LandingHeader, { attachTo: document.body });
    expect(wrapper.find(".brand-name").text()).toBe("UltiCode");
    expect(wrapper.find(".brand-tag").text()).toBe("ARCHIVE");

    const navLinks = wrapper.findAll(".nav-item");
    expect(navLinks.length).toBe(3);

    const menuBtn = wrapper.find(".mobile-menu-btn");
    expect(menuBtn.exists()).toBe(true);
    await menuBtn.trigger("click");
    expect(wrapper.find(".mobile-drawer").isVisible()).toBe(true);

    // Press Escape to close
    window.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape" }));
    await wrapper.vm.$nextTick();
    expect(wrapper.find(".mobile-drawer").isVisible()).toBe(false);
    wrapper.unmount();
  });

  it("renders editorial HeroSection with framed product demo and project CTAs", () => {
    const wrapper = mount(HeroSection);
    expect(wrapper.find(".institutional-badge").exists()).toBe(true);
    expect(wrapper.find(".headline-editorial-wrap").exists()).toBe(true);
    expect(wrapper.find(".hero-headline").exists()).toBe(true);
    expect(wrapper.find(".hero-showcase").exists()).toBe(true);
    expect(wrapper.find(".showcase-atmosphere").exists()).toBe(true);
    expect(wrapper.find(".execution-pipeline-bar").exists()).toBe(true);
    expect(wrapper.find(".product-window-panel").exists()).toBe(true);
    expect(wrapper.find(".window-status-pill").exists()).toBe(true);
    expect(wrapper.findAll(".showcase-actions a").length).toBe(2);
  });

  it("renders four workflow proofs and AI still-life in ProductProofSection", () => {
    const wrapper = mount(ProductProofSection);
    const steps = wrapper.findAll(".proof-steps > li");
    expect(steps.length).toBe(4);
    expect(wrapper.find(".proof-visual img").exists()).toBe(true);
    expect(wrapper.find(".proof-visual figcaption").exists()).toBe(true);
  });

  it("renders all 3 application scenarios with action links in UseCasesSection", () => {
    const wrapper = mount(UseCasesSection);
    const cards = wrapper.findAll(".scenario-card");
    expect(cards.length).toBe(3);
    const actionLinks = wrapper.findAll(".scenario-action-link");
    expect(actionLinks.length).toBe(3);
  });

  it("renders human-control copy around the AI still-life", () => {
    const wrapper = mount(HumanControlSection);
    expect(wrapper.find(".control-emblem").exists()).toBe(true);
    expect(wrapper.findAll(".governance-copy").length).toBe(2);
    expect(wrapper.find(".governance-image img").exists()).toBe(true);
  });

  it("renders closing narrative and primary CTA in FinalStorySection", () => {
    const wrapper = mount(FinalStorySection);
    expect(wrapper.find(".landscape-backdrop").exists()).toBe(true);
    expect(wrapper.find(".final-headline").exists()).toBe(true);
    expect(wrapper.find(".large-cta-btn").exists()).toBe(true);
  });

  it("renders archive, specification, and institutional columns in LandingFooter", () => {
    const wrapper = mount(LandingFooter);
    const cols = wrapper.findAll(".footer-links-col");
    expect(cols.length).toBe(3);
    expect(wrapper.find(".copyright").exists()).toBe(true);
  });
});
