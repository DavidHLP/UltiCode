import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

const root = process.cwd();
const css = readFileSync(resolve(root, "style.css"), "utf8");
const manifest = JSON.parse(
  readFileSync(resolve(root, "package.json"), "utf8"),
);
const themeManifest = JSON.parse(
  readFileSync(resolve(root, "../theme/package.json"), "utf8"),
);

function tokensFor(source, marker) {
  const markerIndex = source.indexOf("--" + marker + ":");
  assert.notEqual(markerIndex, -1, "missing --" + marker);
  const start = source.lastIndexOf("{", markerIndex);
  const end = source.indexOf("}", markerIndex);

  return Object.fromEntries(
    [...source.slice(start + 1, end).matchAll(/--([\w-]+):\s*([^;]+);/g)].map(
      ([, name, value]) => [name, value.trim()],
    ),
  );
}

function luminance(hex) {
  const values = [1, 3, 5]
    .map((offset) => Number.parseInt(hex.slice(offset, offset + 2), 16) / 255)
    .map((value) =>
      value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4,
    );

  return 0.2126 * values[0] + 0.7152 * values[1] + 0.0722 * values[2];
}

function contrast(first, second) {
  const high = Math.max(luminance(first), luminance(second));
  const low = Math.min(luminance(first), luminance(second));
  return (high + 0.05) / (low + 0.05);
}

function mixSrgb(accent, surface, ratio) {
  return (
    "#" +
    [1, 3, 5]
      .map((offset) => {
        const accentChannel = Number.parseInt(
          accent.slice(offset, offset + 2),
          16,
        );
        const surfaceChannel = Number.parseInt(
          surface.slice(offset, offset + 2),
          16,
        );
        return Math.round(accentChannel * ratio + surfaceChannel * (1 - ratio))
          .toString(16)
          .padStart(2, "0");
      })
      .join("")
  );
}

function ruleFor(source, selector) {
  const start = source.indexOf(`${selector} {`);
  assert.notEqual(start, -1, `missing ${selector}`);
  return source.slice(start, source.indexOf("}", start) + 1);
}

test("exports the public stylesheet", () => {
  assert.equal(manifest.exports["./style.css"], "./style.css");
  assert.match(css, /@source "\.\/src";/);
  assert.match(css, /--tw-ring-color: var\(--ring\) !important/);
  assert.match(css, /--tw-outline-color: var\(--ring\) !important/);
  assert.match(css, /outline-color: var\(--ring\) !important/);
  assert.equal(
    themeManifest.exports["./typography.css"],
    "./src/typography.css",
  );
  assert.equal(manifest.exports["./palette"].import, "./src/palette.ts");
  assert.equal(manifest.dependencies["@ulticode/theme"], "workspace:*");
  for (const dependency of ["katex", "tailwindcss", "tw-animate-css"]) {
    assert.ok(manifest.dependencies[dependency]);
  }
});

test("publishes the shared rounded geometry contract", () => {
  assert.match(css, /--radius:\s*0\.5rem;/);
  assert.match(
    css,
    /\.rounded-none\s*\{[\s\S]*border-radius:\s*var\(--radius-md\)\s*!important;/,
  );
  assert.match(
    css,
    /\.terminal-card\s*\{[\s\S]*border-radius:\s*var\(--uc-component-card-radius\);/,
  );
  assert.doesNotMatch(css, /--radius:\s*0;/);
});

test("publishes zh-CN and en-US layout profiles", () => {
  assert.match(
    css,
    /:root\[lang="zh-CN"\][\s\S]*--uc-layout-control-height:\s*2\.5rem;/,
  );
  assert.match(
    css,
    /:root\[lang="en-US"\][\s\S]*--uc-layout-control-height:\s*2\.25rem;/,
  );
  assert.match(css, /\.uc-page-main\s*\{/);
  assert.match(css, /\.uc-page-container\s*\{/);
  assert.match(css, /\.uc-page-stack\s*\{/);
});

test("keeps the canonical Garden palette", () => {
  const light = tokensFor(css, "solarized-base03");

  assert.deepEqual(
    Object.fromEntries(
      Object.entries(light).filter(([name]) => name.startsWith("solarized-")),
    ),
    {
      // Historical Solarized key names carry the Garden values (see
      // docs/GARDEN_DESIGN_SPEC.md).
      "solarized-base03": "#1c2412",
      "solarized-base02": "#26301b",
      "solarized-base01": "#545c45",
      "solarized-base00": "#6a7259",
      "solarized-base0": "#838f81",
      "solarized-base1": "#a2afa9",
      "solarized-base2": "#eae8d8",
      "solarized-base3": "#e3e1d1",
      "solarized-yellow": "#9c7a14",
      "solarized-orange": "#b4622d",
      "solarized-red": "#8f4822",
      "solarized-magenta": "#a05c74",
      "solarized-violet": "#6c71c4",
      "solarized-blue": "#46769b",
      "solarized-cyan": "#4e7d64",
      "solarized-green": "#588e67",
    },
  );
});

test("publishes accessible Light and Dark product mappings", () => {
  const light = tokensFor(css, "solarized-base03");
  const darkSource = css.slice(css.indexOf("Garden Design System - Dark"));
  const dark = tokensFor(darkSource, "background");

  assert.equal(light.foreground, "#19220e");
  assert.equal(light["foreground-strong"], "#19220e");
  assert.equal(light["foreground-muted"], "var(--solarized-base01)");
  assert.equal(light.card, "var(--garden-card)");
  assert.equal(light.primary, "var(--solarized-base01)");
  assert.equal(light["primary-foreground"], "var(--garden-card)");
  assert.equal(light["primary-control"], "var(--solarized-base01)");
  assert.equal(light["primary-control-foreground"], "var(--garden-card)");
  assert.equal(light["secondary-foreground"], "#1c2412");
  assert.equal(light["muted-foreground"], "var(--solarized-base01)");
  assert.equal(light["accent-foreground"], "#1c2412");
  assert.equal(light["sidebar-foreground"], "var(--solarized-base03)");
  assert.equal(light["sidebar-accent-foreground"], "var(--solarized-base01)");
  assert.equal(light["border-control"], "var(--solarized-base00)");
  assert.equal(light.ring, "var(--solarized-base00)");
  assert.equal(light["sidebar-ring"], "var(--solarized-base00)");
  assert.match(light["accent-glow"], /var\(--ring\)/);
  assert.equal(dark.foreground, "var(--solarized-base2)");
  assert.equal(dark.primary, "var(--solarized-base3)");
  assert.equal(dark["primary-foreground"], "#1c2412");
  assert.equal(dark["primary-control"], "var(--solarized-base3)");
  assert.equal(dark["primary-control-foreground"], "#1c2412");
  assert.equal(dark["foreground-strong"], "var(--solarized-base3)");
  assert.equal(dark["muted-foreground"], "var(--solarized-base1)");
  assert.equal(dark["sidebar-accent-foreground"], "var(--solarized-base1)");
  assert.equal(dark["border-control"], "var(--solarized-base0)");
  assert.equal(dark.ring, "var(--solarized-base0)");
  assert.equal(dark["sidebar-ring"], "var(--solarized-base0)");
  assert.match(dark["accent-glow"], /var\(--ring\)/);

  const textPairs = [
    ["Light foreground on card", "#19220e", "#f7f6f0"],
    ["Light foreground on canvas", "#19220e", "#e3e1d1"],
    ["Light strong/highlight", "#19220e", "#eae8d8"],
    ["Light secondary", "#1c2412", "#eae8d8"],
    ["Light sidebar", "#1c2412", "#eae8d8"],
    ["Light sidebar accent", "#545c45", "#e3e1d1"],
    ["Dark foreground", "#eae8d8", "#1c2412"],
    ["Dark elevated", "#eae8d8", "#26301b"],
    ["Dark sidebar accent", "#eae8d8", "#1c2412"],
  ];
  for (const [label, foreground, background] of textPairs) {
    assert.ok(
      contrast(foreground, background) >= 4.5,
      `${label} must reach 4.5:1`,
    );
  }
  assert.ok(contrast("#6a7259", "#e3e1d1") >= 3);
  assert.ok(contrast("#f7f6f0", "#1c2412") >= 4.5);
  assert.ok(contrast("#1c2412", "#f7f6f0") >= 4.5);

  assert.ok(contrast("#838f81", "#1c2412") >= 3);
  assert.ok(contrast("#f7f6f0", "#545c45") >= 3, "Light primary control text must reach 3:1");
  assert.ok(contrast("#1c2412", "#e3e1d1") >= 3, "Dark primary control text must reach 3:1");

  const statusSurfaces = [
    ["#588e67", 0.14, 0.16],
    ["#9c7a14", 0.14, 0.16],
    ["#8f4822", 0.12, 0.16],
    ["#4e7d64", 0.14, 0.14],
    ["#6c71c4", 0.12, 0.16],
  ];
  for (const [accent, lightRatio, darkRatio] of statusSurfaces) {
    assert.ok(
      contrast("#1c2412", mixSrgb(accent, "#e3e1d1", lightRatio)) >= 4.5,
    );
    assert.ok(
      contrast("#eae8d8", mixSrgb(accent, "#1c2412", darkRatio)) >= 4.5,
    );
  }

  const lightMarkAccents = [
    ["#588e67", 0.6],
    ["#9c7a14", 0.65],
    ["#8f4822", 0.7],
    ["#4e7d64", 0.6],
    ["#6c71c4", 0.7],
  ];
  for (const [accent, ratio] of lightMarkAccents) {
    assert.ok(contrast(mixSrgb(accent, "#1c2412", ratio), "#e3e1d1") >= 4.5);
  }

  const darkMarkAccents = [
    ["#588e67", 0.7],
    ["#9c7a14", 0.7],
    ["#8f4822", 0.55],
    ["#4e7d64", 0.7],
    ["#6c71c4", 0.7],
  ];
  for (const [accent, ratio] of darkMarkAccents) {
    assert.ok(contrast(mixSrgb(accent, "#eae8d8", ratio), "#26301b") >= 4.5);
  }
});

test("keeps adaptive control foregrounds readable on dark status marks", () => {
  const darkWarningMark = mixSrgb("#9c7a14", "#eae8d8", 0.7);
  const darkSuccessMark = mixSrgb("#588e67", "#eae8d8", 0.7);

  assert.ok(contrast("#1c2412", darkWarningMark) >= 3);
  assert.ok(contrast("#1c2412", darkSuccessMark) >= 3);
});

test("owns shared status and chart semantics", () => {
  const light = tokensFor(css, "solarized-base03");

  assert.equal(light["status-success"], "var(--solarized-green)");
  assert.equal(light["status-info"], "var(--solarized-cyan)");
  assert.equal(light["status-special"], "var(--solarized-violet)");
  assert.equal(light["chart-series-1"], "var(--solarized-blue)");
  assert.equal(light["chart-series-4"], "var(--solarized-magenta)");
  assert.equal(light["chart-series-8"], "var(--status-error-mark)");
  assert.equal(light["chart-grid-color"], "var(--border-subtle)");
  assert.equal(light["rank-first"], "var(--solarized-yellow)");
  assert.equal(light["rank-third"], "var(--solarized-orange)");
});

test("publishes shared component states without palette primitives", async () => {
  const {
    BADGE_VARIANT_CLASSES,
    BUTTON_VARIANT_CLASSES,
    getDifficultyBadgeClass,
    MENU_ITEM_VARIANT_CLASSES,
  } = await import("../src/variants.ts");
  const classes = JSON.stringify({
    BADGE_VARIANT_CLASSES,
    BUTTON_VARIANT_CLASSES,
    MENU_ITEM_VARIANT_CLASSES,
  });

  assert.match(BUTTON_VARIANT_CLASSES.default, /bg-primary/);
  assert.match(BUTTON_VARIANT_CLASSES.default, /border-primary/);
  assert.match(BUTTON_VARIANT_CLASSES.destructive, /status-error-surface/);
  assert.match(BUTTON_VARIANT_CLASSES.link, /decoration-link-decoration/);
  assert.match(BUTTON_VARIANT_CLASSES.outline, /hover:border-primary/);
  assert.match(BUTTON_VARIANT_CLASSES.secondary, /hover:border-border-control/);
  assert.doesNotMatch(classes, /bg-accent|dark:bg-input/);
  assert.match(BADGE_VARIANT_CLASSES.destructive, /text-foreground-strong/);
  assert.match(MENU_ITEM_VARIANT_CLASSES.destructive, /text-foreground-strong/);
  assert.match(getDifficultyBadgeClass("HARD"), /status-error-surface/);
  assert.match(getDifficultyBadgeClass("medium"), /status-warning/);
  assert.match(getDifficultyBadgeClass("unknown"), /border-control/);
  assert.doesNotMatch(classes, /--solarized-|terminal-|silver-/);

  const light = tokensFor(css, "solarized-base03");
  const dark = tokensFor(
    css.slice(css.indexOf("Garden Design System - Dark")),
    "background",
  );
  assert.notEqual(light["border-control"], light.primary);
  assert.notEqual(dark["border-control"], dark.primary);
  assert.notEqual(light["border-control"], light["surface-highlight"]);
  assert.notEqual(dark["border-control"], dark["surface-highlight"]);
});

test("keeps terminal badges readable with a semantic marker", () => {
  const semantics = {
    success: "success",
    warning: "warning",
    error: "error",
    info: "info",
    purple: "special",
  };

  for (const [badge, semantic] of Object.entries(semantics)) {
    const rule = ruleFor(css, `.terminal-badge-${badge}`);
    assert.match(
      rule,
      new RegExp(`background: var\\(--status-${semantic}-surface\\)`),
    );
    assert.match(rule, /color: var\(--foreground-strong\)/);
    assert.match(
      rule,
      new RegExp(`border: 1px solid var\\(--status-${semantic}-mark\\)`),
    );
  }

  assert.match(
    ruleFor(css, ".terminal-badge-neutral"),
    /var\(--muted-foreground\)/,
  );
  assert.match(
    ruleFor(css, ".terminal-badge-primary"),
    /var\(--primary-foreground\)/,
  );
});

test("does not introduce colors outside the canonical garden palette", () => {
  // 16 raw bridge values + derived semantic literals owned by style.css
  // (ink text, olive hover, card paper, sky fill). See GARDEN_DESIGN_SPEC.md.
  const canonical = new Set([
    "#1c2412",
    "#26301b",
    "#545c45",
    "#6a7259",
    "#838f81",
    "#a2afa9",
    "#eae8d8",
    "#e3e1d1",
    "#9c7a14",
    "#b4622d",
    "#8f4822",
    "#a05c74",
    "#6c71c4",
    "#46769b",
    "#4e7d64",
    "#588e67",
    "#19220e",
    "#3e4433",
    "#f7f6f0",
    "#92b3cf",
    "#3f683b",
    "#6e5b28",
  ]);
  const literals = [...css.matchAll(/#[0-9a-f]{6}/gi)].map(([value]) =>
    value.toLowerCase(),
  );

  assert.deepEqual(new Set(literals), canonical);
});
