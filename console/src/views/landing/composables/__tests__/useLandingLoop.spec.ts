import { describe, it, expect, vi, beforeEach } from "vitest";

const { tweenCalls } = vi.hoisted(() => ({
  tweenCalls: [] as { onComplete?: () => void }[],
}));

vi.mock("gsap", () => ({
  default: {
    to: vi.fn((_t: unknown, opts: { onComplete?: () => void }) => {
      tweenCalls.push(opts);
      return opts;
    }),
    killTweensOf: vi.fn(),
  },
}));

import gsap from "gsap";
import { createLoopController } from "../useLandingLoop";

function makeController() {
  const reset = vi.fn();
  const ctrl = createLoopController({
    getCanvas: () => ({}) as HTMLCanvasElement,
    reset,
  });
  return { ctrl, reset };
}

beforeEach(() => {
  vi.clearAllMocks();
  tweenCalls.length = 0;
});

describe("createLoopController", () => {
  it("does not trigger below the end threshold or on reverse scroll", () => {
    const { ctrl } = makeController();
    ctrl.maybeTrigger(0.5, 1);
    ctrl.maybeTrigger(0.999, -1); // wrong direction
    expect(tweenCalls).toHaveLength(0);
    expect(ctrl.isLooping).toBe(false);
  });

  it("triggers a fade→reset at the end while scrolling forward", () => {
    const { ctrl, reset } = makeController();
    ctrl.maybeTrigger(0.9995, 1);
    expect(tweenCalls).toHaveLength(1); // fade-out started
    expect(ctrl.isLooping).toBe(true);
    // complete the fade-out -> reset runs, fade-in starts
    tweenCalls[0].onComplete?.();
    expect(reset).toHaveBeenCalledOnce();
    expect(tweenCalls).toHaveLength(2); // fade-in started
  });

  it("guards against re-entry mid-reset", () => {
    const { ctrl } = makeController();
    ctrl.maybeTrigger(0.9995, 1);
    ctrl.maybeTrigger(0.9995, 1); // blocked
    expect(tweenCalls).toHaveLength(1);
  });

  it("rearms after the reset animation completes", () => {
    const { ctrl } = makeController();
    ctrl.maybeTrigger(0.9995, 1);
    tweenCalls[0].onComplete?.(); // fade-out done -> reset + fade-in
    expect(ctrl.isLooping).toBe(true);
    tweenCalls[1].onComplete?.(); // fade-in done -> rearm
    expect(ctrl.isLooping).toBe(false);
    // can trigger again now
    ctrl.maybeTrigger(0.9995, 1);
    expect(tweenCalls).toHaveLength(3);
  });

  it("stops triggering and kills the tween after dispose", () => {
    const { ctrl } = makeController();
    ctrl.maybeTrigger(0.9995, 1);
    ctrl.dispose();
    expect(gsap.killTweensOf).toHaveBeenCalled();
    ctrl.maybeTrigger(0.9995, 1); // no-op after dispose
    expect(tweenCalls).toHaveLength(1);
  });

  it("no-ops when there is no canvas", () => {
    const ctrl = createLoopController({
      getCanvas: () => null,
      reset: vi.fn(),
    });
    ctrl.maybeTrigger(0.9995, 1);
    expect(tweenCalls).toHaveLength(0);
  });
});
