import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import LandingLucaView from "../LandingLucaView.vue";

const push = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: false }),
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

const routeHref = (to: { name?: string; params?: Record<string, string> }) => {
  const params = to.params ? "/" + Object.values(to.params).join("/") : "";
  return `/${to.name ?? ""}${params}`;
};

const mountView = () =>
  mount(LandingLucaView, {
    global: {
      stubs: {
        RouterLink: {
          props: ["to"],
          template: '<a :href="routeHref(to)"><slot /></a>',
          methods: { routeHref },
        },
      },
    },
  });

const BEAT_STATES = [
  "squashed",
  "cracked",
  "snapped",
  "axed",
  "opened",
  "quarteted",
  "timed",
  "still",
  "broken",
] as const;

describe("LandingLucaView", () => {
  beforeEach(() => {
    push.mockClear();
    window.sessionStorage.clear();
    try {
      window.sessionStorage.removeItem("luca-entered");
    } catch {
      /* ignore */
    }
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows the loading portal counter and ENTER control", () => {
    const wrapper = mountView();
    const counter = wrapper.find(".luca-portal-counter");

    expect(counter.text()).toBe("000 / 024");
    expect(wrapper.find(".luca-portal-enter").exists()).toBe(true);
  });

  it("reveals ENTER and fills the counter after skipping", async () => {
    const wrapper = mountView();
    const enter = wrapper.find(".luca-portal-enter");

    expect(enter.attributes("tabindex")).toBe("-1");

    await wrapper.find(".luca-portal-skip").trigger("click");
    expect(wrapper.find(".luca-portal-counter").text()).toBe("024 / 024");
    expect(wrapper.find(".luca-portal-enter").attributes("tabindex")).toBe("0");
  });

  it("dismisses the portal when the skip button is clicked", async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mountView();
      expect(wrapper.find(".luca-portal").exists()).toBe(true);

      await wrapper.find(".luca-portal-skip").trigger("click");
      // The skip composable fast-forwards progress synchronously…
      expect(wrapper.find(".luca-portal-counter").text()).toBe("024 / 024");
      // …and the view should schedule an unmount through the same leave timer
      // ENTER uses. Before the timer fires the leaving class is present.
      expect(wrapper.find(".luca-portal.is-leaving").exists()).toBe(true);

      vi.advanceTimersByTime(600);
      await wrapper.vm.$nextTick();
      expect(wrapper.find(".luca-portal").exists()).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it("marks the portal as leaving once ENTER is activated", async () => {
    const wrapper = mountView();
    await wrapper.find(".luca-portal-skip").trigger("click");
    await wrapper.find(".luca-portal-enter").trigger("click");

    expect(wrapper.find(".luca-portal.is-leaving").exists()).toBe(true);
  });

  it("routes the nav talk button into the seeded problem", async () => {
    const wrapper = mountView();
    await wrapper.find(".luca-talk").trigger("click");

    expect(push).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "two-sum" },
    });
  });

  it("opens the mobile menu with the four primary destinations", async () => {
    const wrapper = mountView();
    expect(wrapper.find(".luca-menu").exists()).toBe(false);

    await wrapper.find(".luca-burger").trigger("click");
    const menuLinks = wrapper.findAll(".luca-menu-link");

    expect(menuLinks).toHaveLength(4);
  });

  it("exposes a skip link to main content for keyboard users", () => {
    const wrapper = mountView();

    expect(wrapper.find(".luca-skip").attributes("href")).toBe("#luca-main");
    expect(wrapper.find("#luca-main").exists()).toBe(true);
  });

  it("mounts the 3D stage as a page-wide layer inside the root", () => {
    const wrapper = mountView();
    const stage = wrapper.find(".luca-stage");
    expect(stage.exists()).toBe(true);
    expect(stage.element.closest(".luca-root")).not.toBeNull();
  });

  it("renders all nine narrative beats with a 0N / 09 counter", () => {
    const wrapper = mountView();
    const beats = wrapper.findAll(".luca-beat");
    expect(beats).toHaveLength(9);

    const counters = wrapper.findAll(".luca-beat-counter").map((e) => e.text());
    expect(counters).toEqual([
      "01 / 09",
      "02 / 09",
      "03 / 09",
      "04 / 09",
      "05 / 09",
      "06 / 09",
      "07 / 09",
      "08 / 09",
      "09 / 09",
    ]);
  });

  it("renders each beat's headline + sub-line in scroll order", () => {
    const wrapper = mountView();
    const titles = wrapper.findAll(".luca-beat-title").map((e) => e.text());
    expect(titles).toEqual(BEAT_STATES.map((s) => `landingLuca.beats.${s}.title`));

    const sublines = wrapper.findAll(".luca-beat-subline").map((e) => e.text());
    expect(sublines).toEqual(BEAT_STATES.map((s) => `landingLuca.beats.${s}.subline`));
  });

  it("tags every beat with its polyhedron-state class", () => {
    const wrapper = mountView();
    BEAT_STATES.forEach((state) => {
      expect(wrapper.find(`.luca-beat-${state}`).exists()).toBe(true);
    });
  });
});
