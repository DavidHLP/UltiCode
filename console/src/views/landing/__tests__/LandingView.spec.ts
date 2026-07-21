import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount } from "@vue/test-utils";
import type { LandingSceneHandle } from "../composables/useLandingScene";
import type { LandingFaderHandle } from "../composables/useLandingFader";

// Mock the WebGL composables so no Three.js/WebGL runs under jsdom.
vi.mock("../composables/useLandingScene", () => ({
  createLandingScene: vi.fn(),
}));
vi.mock("../composables/useLandingFader", () => ({
  createLandingFader: vi.fn(),
}));

import LandingView from "../LandingView.vue";
import { createLandingScene } from "../composables/useLandingScene";
import { createLandingFader } from "../composables/useLandingFader";

const sceneDispose = vi.fn();
const renderOnce = vi.fn();
const sceneHandle = {
  desert: { uniforms: {} },
  fog: {},
  camera: {},
  yaw: {},
  pitch: {},
  renderOnce,
  dispose: sceneDispose,
} as unknown as LandingSceneHandle;
const faderHandle = { dispose: vi.fn() } as unknown as LandingFaderHandle;

const scene = vi.mocked(createLandingScene);
const fader = vi.mocked(createLandingFader);

function stubMatchMedia(matches: (query: string) => boolean) {
  window.matchMedia = ((query: string): MediaQueryList => ({
    matches: matches(query),
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
}

beforeEach(() => {
  vi.clearAllMocks();
  scene.mockReturnValue(sceneHandle);
  fader.mockReturnValue(faderHandle);
  // Default: reduced-motion preferred → exercises the a11y-critical branch.
  stubMatchMedia((q) => /reduce/i.test(q));
});

describe("LandingView", () => {
  it("mounts the canvas and wires scene + fader with reduced-motion detected", () => {
    const wrapper = mount(LandingView, { attachTo: document.body });

    expect(wrapper.find("canvas.landing__canvas").exists()).toBe(true);
    expect(scene).toHaveBeenCalledOnce();
    expect(fader).toHaveBeenCalledOnce();
    // Scene must be created before the fader (fader calls renderOnce at init).
    expect(scene.mock.invocationCallOrder[0]).toBeLessThan(
      fader.mock.invocationCallOrder[0],
    );
    const opts = (scene.mock.calls[0] as unknown[])[1] as {
      reducedMotion: boolean;
    };
    expect(opts.reducedMotion).toBe(true);

    wrapper.unmount();
    expect(sceneDispose).toHaveBeenCalledOnce();
  });

  it("renders the three act labels in the overlay", () => {
    const wrapper = mount(LandingView, { attachTo: document.body });
    const acts = wrapper.findAll(".landing__act");
    expect(acts).toHaveLength(3);
    expect(acts.map((a) => a.text())).toEqual(["VISION", "CRAFT", "EXPERIENCE"]);
    wrapper.unmount();
  });

  it("passes reducedMotion=false when the user prefers motion", () => {
    stubMatchMedia(() => false);
    const wrapper = mount(LandingView, { attachTo: document.body });
    const opts = (scene.mock.calls[0] as unknown[])[1] as {
      reducedMotion: boolean;
    };
    expect(opts.reducedMotion).toBe(false);
    wrapper.unmount();
  });
});
