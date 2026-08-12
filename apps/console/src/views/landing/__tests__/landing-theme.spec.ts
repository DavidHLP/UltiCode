import { describe, expect, it, vi } from "vitest";
import { resolve } from "node:path";
import { readFileSync } from "node:fs";
import * as THREE from "three";
import { SOLARIZED_PALETTE } from "@ulticode/design-system";
import {
  LANDING_THEMES,
  getLandingTheme,
  getLandingThemeName,
} from "../experience/theme.js";
import {
  applyThemeToScene,
  tickSceneTime,
} from "../experience/MainScene.js";

// ---------------------------------------------------------------------------
// TASK-007:Landing WebGL dual theme + renderer defaults + reduced motion.
// The palette bridge (SOLARIZED_PALETTE) is provided by @ulticode/design-system;
// these tests pin the mapping, the seam application and the motion gate.
// ---------------------------------------------------------------------------

const PALETTE_KEYS = [
  "base03", "base02", "base01", "base00",
  "base0", "base1", "base2", "base3",
  "yellow", "orange", "red", "magenta",
  "violet", "blue", "cyan", "green",
];

describe("landing theme.js palette bridge", () => {
  it("consumes the 16 canonical SOLARIZED_PALETTE values from design-system", () => {
    expect(Object.keys(SOLARIZED_PALETTE).sort()).toEqual([...PALETTE_KEYS].sort());
    for (const key of PALETTE_KEYS) {
      expect(SOLARIZED_PALETTE[key]).toMatch(/^#[0-9a-fA-F]{6}$/);
    }
  });

  it("maps dark/light LANDING_THEMES to canonical palette values", () => {
    expect(LANDING_THEMES.dark.clearColor).toBe(SOLARIZED_PALETTE.base03);
    expect(LANDING_THEMES.dark.fog).toEqual({
      dark: SOLARIZED_PALETTE.base03,
      light: SOLARIZED_PALETTE.base1,
    });
    expect(LANDING_THEMES.dark.fogFade).toBe(SOLARIZED_PALETTE.base03);
    expect(LANDING_THEMES.dark.desert).toEqual({
      colorA: SOLARIZED_PALETTE.base01,
      colorB: SOLARIZED_PALETTE.cyan,
      glow: 2.5,
    });
    expect(LANDING_THEMES.dark.light).toEqual({
      colorA: SOLARIZED_PALETTE.cyan,
      colorB: SOLARIZED_PALETTE.base2,
      glow: 1.0,
    });
    expect(LANDING_THEMES.dark.text).toBe(SOLARIZED_PALETTE.base2);
    expect(LANDING_THEMES.dark.textStroke).toBe(SOLARIZED_PALETTE.base03);
    expect(LANDING_THEMES.dark.mouse).toBe(SOLARIZED_PALETTE.blue);

    expect(LANDING_THEMES.light.clearColor).toBe(SOLARIZED_PALETTE.base3);
    expect(LANDING_THEMES.light.fog).toEqual({
      dark: SOLARIZED_PALETTE.base2,
      light: SOLARIZED_PALETTE.base1,
    });
    expect(LANDING_THEMES.light.fogFade).toBe(SOLARIZED_PALETTE.base3);
    expect(LANDING_THEMES.light.desert).toEqual({
      colorA: SOLARIZED_PALETTE.base00,
      colorB: SOLARIZED_PALETTE.cyan,
      glow: 1.1,
    });
    expect(LANDING_THEMES.light.light).toEqual({
      colorA: SOLARIZED_PALETTE.cyan,
      colorB: SOLARIZED_PALETTE.base1,
      glow: 0.8,
    });
    expect(LANDING_THEMES.light.text).toBe(SOLARIZED_PALETTE.base01);
    expect(LANDING_THEMES.light.textStroke).toBe(SOLARIZED_PALETTE.base0);
    expect(LANDING_THEMES.light.mouse).toBe(SOLARIZED_PALETTE.blue);
  });

  it("reads the theme name/theme from the <html> dark class (unchanged behavior)", () => {
    document.documentElement.classList.remove("dark");
    expect(getLandingThemeName()).toBe("light");
    expect(getLandingTheme()).toBe(LANDING_THEMES.light);

    document.documentElement.classList.add("dark");
    expect(getLandingThemeName()).toBe("dark");
    expect(getLandingTheme()).toBe(LANDING_THEMES.dark);

    document.documentElement.classList.remove("dark");
  });
});

describe("MainScene.applyThemeToScene seam", () => {
  const color = () => ({ value: new THREE.Color() });

  const makeMockScene = () => ({
    renderer: { setClearColor: vi.fn() },
    fog: {
      params: {},
      frontMaterial: { uniforms: { uColorDark: color(), uColorLight: color() } },
      backMaterial: { uniforms: { uColorDark: color(), uColorLight: color() } },
    },
    desert: {
      particles: {
        colorA: "",
        colorB: "",
        material: {
          uniforms: { uColorA: color(), uColorB: color(), uGlow: { value: 0 } },
        },
      },
    },
    light: {
      particles: {
        colorA: "",
        colorB: "",
        material: {
          uniforms: { uColorA: color(), uColorB: color(), uGlow: { value: 0 } },
        },
      },
    },
    scrollTo: { setColor: vi.fn(), material: { uniforms: { uStrokeColor: color() } } },
    vision: { setColor: vi.fn(), material: { uniforms: { uStrokeColor: color() } } },
    aboutR: [],
    craft: { setColor: vi.fn(), material: { uniforms: { uStrokeColor: color() } } },
    agencyR: [],
    clientsR: [],
    experience: { setColor: vi.fn(), material: { uniforms: { uStrokeColor: color() } } },
    recognitionR: [],
    finalClaim: { setColor: vi.fn(), material: { uniforms: { uStrokeColor: color() } } },
    mousePointer: { material: { uniforms: { uColor: color() } } },
  });

  const hex = (threeColor) => threeColor.getHexString();

  it("applies the theme to renderer, fog, particles, texts and mouse pointer", () => {
    const scene = makeMockScene();
    const theme = LANDING_THEMES.dark;

    applyThemeToScene(scene, theme);

    expect(scene.renderer.setClearColor).toHaveBeenCalledWith(
      new THREE.Color(theme.clearColor),
      0,
    );

    expect(scene.fog.params).toEqual({
      colorDark: theme.fog.dark,
      colorLight: theme.fog.light,
    });
    for (const material of [scene.fog.frontMaterial, scene.fog.backMaterial]) {
      expect(hex(material.uniforms.uColorDark.value)).toBe(hex(new THREE.Color(theme.fog.dark)));
      expect(hex(material.uniforms.uColorLight.value)).toBe(hex(new THREE.Color(theme.fog.light)));
    }

    expect(scene.desert.particles.colorA).toBe(theme.desert.colorA);
    expect(scene.desert.particles.colorB).toBe(theme.desert.colorB);
    expect(hex(scene.desert.particles.material.uniforms.uColorA.value)).toBe(
      hex(new THREE.Color(theme.desert.colorA)),
    );
    expect(hex(scene.desert.particles.material.uniforms.uColorB.value)).toBe(
      hex(new THREE.Color(theme.desert.colorB)),
    );
    expect(scene.desert.particles.material.uniforms.uGlow.value).toBe(theme.desert.glow);

    expect(scene.light.particles.colorA).toBe(theme.light.colorA);
    expect(hex(scene.light.particles.material.uniforms.uColorB.value)).toBe(
      hex(new THREE.Color(theme.light.colorB)),
    );
    expect(scene.light.particles.material.uniforms.uGlow.value).toBe(theme.light.glow);

    for (const text of [scene.scrollTo, scene.vision, scene.craft, scene.experience, scene.finalClaim]) {
      expect(text.setColor).toHaveBeenCalledWith(theme.text);
      expect(hex(text.material.uniforms.uStrokeColor.value)).toBe(
        hex(new THREE.Color(theme.textStroke)),
      );
    }

    expect(hex(scene.mousePointer.material.uniforms.uColor.value)).toBe(
      hex(new THREE.Color(theme.mouse)),
    );
  });

  it("keeps the light theme mapping applied through the same seam", () => {
    const scene = makeMockScene();
    const theme = LANDING_THEMES.light;

    applyThemeToScene(scene, theme);

    expect(scene.renderer.setClearColor).toHaveBeenCalledWith(
      new THREE.Color(theme.clearColor),
      0,
    );
    expect(scene.fog.params.colorDark).toBe(theme.fog.dark);
    expect(scene.desert.particles.colorA).toBe(theme.desert.colorA);
    expect(hex(scene.mousePointer.material.uniforms.uColor.value)).toBe(
      hex(new THREE.Color(theme.mouse)),
    );
  });
});

describe("MainScene reduced motion clock", () => {
  it("freezes the continuous scene clock under prefers-reduced-motion", () => {
    // advancing: now * 0.01
    expect(tickSceneTime(1000, false, 5)).toBe(10);
    expect(tickSceneTime(2500, false, 0)).toBe(25);
    // reduced: keeps the previous value, ignores wall clock
    expect(tickSceneTime(1000, true, 7.5)).toBe(7.5);
    expect(tickSceneTime(999999, true, 0)).toBe(0);
  });
  it("keeps Lenis and scene controllers live when the preference changes", () => {
    const source = readFileSync(
      resolve(process.cwd(), "src/views/landing/experience/main.js"),
      "utf8",
    );

    expect(source).toContain("lenis.raf(time)");
    expect(source).not.toContain("destroyed || reducedMotion");
    expect(source).toContain("applyReducedMotion(reducedMotion)");
    expect(source).toContain("setTimelineMotion = (reduce) =>");
    expect(source).toContain("ScrollTrigger.update()");
  });
});


describe("landing renderer default colors", () => {
  const MIGRATED_FILES = [
    "experience/theme.js",
    "experience/CustomFog.js",
    "experience/MousePointer.js",
    "experience/ParticlesDesert.js",
    "experience/ParticlesLight.js",
    "experience/MSDFText.js",
    "experience/MainScene.js",
  ];

  const BANNED_RAW_COLORS = [
    "#ffffff",
    "#fff",
    "#000000",
    "#000",
    "#757575",
    "#8f8f8f",
    "0x101010",
    "setHSL(",
  ];

  it("uses canonical palette values, no raw visible colors", () => {
    const landingDir = resolve(process.cwd(), "src/views/landing");
    for (const file of MIGRATED_FILES) {
      const source = readFileSync(resolve(landingDir, file), "utf8");

      for (const banned of BANNED_RAW_COLORS) {
        expect(source).not.toContain(banned);
      }
    }
  });
});
