import { describe, expect, it, vi } from "vitest";
import { MainScene } from "../MainScene";

describe("MainScene text reveal", () => {
  it("updates only the requested line", () => {
    const lines = Array.from({ length: 3 }, () => ({ setRevealProgress: vi.fn() }));
    const scene = {
      getTextSection: () => lines,
    } as Pick<MainScene, "getTextSection" | "setTextLineReveal">;

    MainScene.prototype.setTextLineReveal.call(scene, "aboutR", 1, 0.5);

    expect(lines[0].setRevealProgress).not.toHaveBeenCalled();
    expect(lines[1].setRevealProgress).toHaveBeenCalledWith(0.5);
    expect(lines[2].setRevealProgress).not.toHaveBeenCalled();
  });
});
