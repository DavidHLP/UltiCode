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

test("keeps the canonical Solarized palette", () => {
  const light = tokensFor(css, "solarized-base03");

  assert.deepEqual(
    Object.fromEntries(
      Object.entries(light).filter(([name]) => name.startsWith("solarized-")),
    ),
    {
      "solarized-base03": "#002b36",
      "solarized-base02": "#073642",
      "solarized-base01": "#586e75",
      "solarized-base00": "#657b83",
      "solarized-base0": "#839496",
      "solarized-base1": "#93a1a1",
      "solarized-base2": "#eee8d5",
      "solarized-base3": "#fdf6e3",
      "solarized-yellow": "#b58900",
      "solarized-orange": "#cb4b16",
      "solarized-red": "#dc322f",
      "solarized-magenta": "#d33682",
      "solarized-violet": "#6c71c4",
      "solarized-blue": "#268bd2",
      "solarized-cyan": "#2aa198",
      "solarized-green": "#859900",
    },
  );
});

test("publishes accessible Light and Dark product mappings", () => {
  const light = tokensFor(css, "solarized-base03");
  const darkSource = css.slice(css.indexOf("Solarized Design System - Dark"));
  const dark = tokensFor(darkSource, "background");

  assert.equal(light.foreground, "var(--solarized-base01)");
  assert.equal(light["foreground-strong"], "var(--solarized-base03)");
  assert.equal(light["foreground-muted"], "var(--solarized-base01)");
  assert.equal(light.primary, "var(--solarized-blue)");
  assert.equal(light["primary-foreground"], "var(--solarized-base3)");
  assert.equal(light["primary-control"], "var(--solarized-base03)");
  assert.equal(light["primary-control-foreground"], "var(--solarized-base3)");
  assert.equal(light["secondary-foreground"], "var(--solarized-base03)");
  assert.equal(light["muted-foreground"], "var(--solarized-base03)");
  assert.equal(light["accent-foreground"], "var(--solarized-base03)");
  assert.equal(light["sidebar-foreground"], "var(--solarized-base03)");
  assert.equal(light["sidebar-accent-foreground"], "var(--solarized-base01)");
  assert.equal(light["border-control"], "var(--solarized-base00)");
  assert.equal(dark.foreground, "var(--solarized-base1)");
  assert.equal(dark.primary, "var(--solarized-blue)");
  assert.equal(dark["primary-foreground"], "var(--solarized-base3)");
  assert.equal(dark["primary-control"], "var(--solarized-base3)");
  assert.equal(dark["primary-control-foreground"], "var(--solarized-base03)");
  assert.equal(dark["foreground-strong"], "var(--solarized-base1)");
  assert.equal(dark["muted-foreground"], "var(--solarized-base1)");
  assert.equal(dark["sidebar-accent-foreground"], "var(--solarized-base1)");
  assert.equal(dark["border-control"], "var(--solarized-base0)");

  const textPairs = [
    ["Light foreground", "#586e75", "#fdf6e3"],
    ["Light strong/highlight", "#002b36", "#eee8d5"],
    ["Light secondary", "#002b36", "#eee8d5"],
    ["Light sidebar", "#002b36", "#eee8d5"],
    ["Light sidebar accent", "#586e75", "#fdf6e3"],
    ["Dark foreground", "#93a1a1", "#002b36"],
    ["Dark elevated", "#93a1a1", "#073642"],
    ["Dark sidebar accent", "#93a1a1", "#002b36"],
  ];
  for (const [label, foreground, background] of textPairs) {
    assert.ok(
      contrast(foreground, background) >= 4.5,
      `${label} must reach 4.5:1`,
    );
  }
  assert.ok(contrast("#657b83", "#fdf6e3") >= 3);
  assert.ok(contrast("#fdf6e3", "#002b36") >= 4.5);
  assert.ok(contrast("#002b36", "#fdf6e3") >= 4.5);

  assert.ok(contrast("#839496", "#002b36") >= 3);
  assert.ok(contrast("#fdf6e3", "#268bd2") >= 3, "primary control text must reach 3:1");

  const statusSurfaces = [
    ["#859900", 0.14, 0.16],
    ["#b58900", 0.14, 0.16],
    ["#dc322f", 0.12, 0.16],
    ["#2aa198", 0.14, 0.14],
    ["#6c71c4", 0.12, 0.16],
  ];
  for (const [accent, lightRatio, darkRatio] of statusSurfaces) {
    assert.ok(
      contrast("#002b36", mixSrgb(accent, "#fdf6e3", lightRatio)) >= 4.5,
    );
    assert.ok(
      contrast("#93a1a1", mixSrgb(accent, "#002b36", darkRatio)) >= 4.5,
    );
  }

  const lightMarkAccents = [
    ["#859900", 0.6],
    ["#b58900", 0.65],
    ["#dc322f", 0.7],
    ["#2aa198", 0.6],
    ["#6c71c4", 0.7],
  ];
  for (const [accent, ratio] of lightMarkAccents) {
    assert.ok(contrast(mixSrgb(accent, "#002b36", ratio), "#fdf6e3") >= 4.5);
  }

  const darkMarkAccents = [
    ["#859900", 0.7],
    ["#b58900", 0.7],
    ["#dc322f", 0.65],
    ["#2aa198", 0.7],
    ["#6c71c4", 0.7],
  ];
  for (const [accent, ratio] of darkMarkAccents) {
    assert.ok(contrast(mixSrgb(accent, "#fdf6e3", ratio), "#073642") >= 4.5);
  }
});

test("keeps adaptive control foregrounds readable on dark status marks", () => {
  const darkWarningMark = mixSrgb("#b58900", "#fdf6e3", 0.7);
  const darkSuccessMark = mixSrgb("#859900", "#fdf6e3", 0.7);

  assert.ok(contrast("#002b36", darkWarningMark) >= 3);
  assert.ok(contrast("#002b36", darkSuccessMark) >= 3);
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
  assert.match(BADGE_VARIANT_CLASSES.destructive, /text-foreground-strong/);
  assert.match(MENU_ITEM_VARIANT_CLASSES.destructive, /text-foreground-strong/);
  assert.match(getDifficultyBadgeClass("HARD"), /status-error-surface/);
  assert.match(getDifficultyBadgeClass("medium"), /status-warning/);
  assert.match(getDifficultyBadgeClass("unknown"), /border-control/);
  assert.doesNotMatch(classes, /--solarized-|terminal-|silver-/);
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

test("does not introduce colors outside the canonical palette", () => {
  const canonical = new Set([
    "#002b36",
    "#073642",
    "#586e75",
    "#657b83",
    "#839496",
    "#93a1a1",
    "#eee8d5",
    "#fdf6e3",
    "#b58900",
    "#cb4b16",
    "#dc322f",
    "#d33682",
    "#6c71c4",
    "#268bd2",
    "#2aa198",
    "#859900",
  ]);
  const literals = [...css.matchAll(/#[0-9a-f]{6}/gi)].map(([value]) =>
    value.toLowerCase(),
  );

  assert.deepEqual(new Set(literals), canonical);
});
