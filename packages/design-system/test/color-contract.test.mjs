import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import test from "node:test";

const packageRoot = process.cwd();
const repoRoot = resolve(packageRoot, "../..");

// Garden palette: the 16 raw bridge values plus the derived semantic
// literals used by packages/design-system/style.css (see
// docs/GARDEN_DESIGN_SPEC.md).
const GARDEN_HEX = [
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
];
const GARDEN_CSS_ONLY = [
  "#19220e",
  "#3e4433",
  "#f7f6f0",
  "#92b3cf",
  "#3f683b",
  "#6e5b28",
];
const CANONICAL_HEX = new Set([...GARDEN_HEX]);

test("locks the runtime palette to the canonical style.css values", async () => {
  const { SOLARIZED_PALETTE } = await import("../src/palette.ts");
  const css = readFileSync(resolve(packageRoot, "style.css"), "utf8");

  const cssValues = new Map(
    [...css.matchAll(/--solarized-([a-z0-9]+):\s*(#[0-9a-f]{6})/gi)].map(
      ([, key, value]) => [key, value.toLowerCase()],
    ),
  );

  assert.equal(Object.keys(SOLARIZED_PALETTE).length, 16);
  for (const [key, value] of Object.entries(SOLARIZED_PALETTE)) {
    assert.equal(
      value,
      cssValues.get(key),
      `SOLARIZED_PALETTE.${key} must match the canonical style.css value`,
    );
  }
  assert.deepEqual(
    new Set(Object.values(SOLARIZED_PALETTE)),
    CANONICAL_HEX,
    "runtime palette must carry exactly the 16 canonical values",
  );
});

test("readCssColor resolves browser values and falls back without a DOM", async () => {
  const { readCssColor, SOLARIZED_PALETTE } = await import("../src/palette.ts");

  // No DOM: returns the canonical fallback untouched.
  assert.equal(readCssColor("--background", SOLARIZED_PALETTE.base3), "#e3e1d1");
  assert.equal(readCssColor("var(--background)", SOLARIZED_PALETTE.base03), "#1c2412");

  // A non-canonical fallback is a programming error and is rejected.
  assert.throws(() => readCssColor("--background", "#ffffff"), TypeError);

  // Browser path: prefers the computed property value; unset properties fall back.
  const computed = new Map([
    ["--background", "  #a2afa9 "],
    ["--solarized-base03", "#1c2412"],
  ]);
  globalThis.document = { documentElement: {} };
  globalThis.getComputedStyle = () => ({
    getPropertyValue: (name) => computed.get(name) ?? "",
  });
  try {
    assert.equal(readCssColor("--background", SOLARIZED_PALETTE.base3), "#a2afa9");
    assert.equal(readCssColor("var(--background)", SOLARIZED_PALETTE.base3), "#a2afa9");
    assert.equal(readCssColor("--missing", SOLARIZED_PALETTE.base3), "#e3e1d1");
  } finally {
    delete globalThis.document;
    delete globalThis.getComputedStyle;
  }
});

test("re-exports the runtime palette bridge from the package entry", () => {
  const indexSource = readFileSync(resolve(packageRoot, "src/index.ts"), "utf8");
  assert.match(indexSource, /export \{ readCssColor, SOLARIZED_PALETTE \} from "\.\/palette";/);
  assert.match(indexSource, /export type \{[\s\S]*SolarizedPaletteKey/);
});

// ---------------------------------------------------------------------------
// Scanner contract: first-party runtime source may only carry Solarized colors.
// ---------------------------------------------------------------------------

const SCAN_EXTENSIONS = /\.(ts|tsx|vue|js|mjs|jsx|glsl|css|svg)$/;
const TEST_FILE = /\.(test|spec)\.[cm]?[jt]sx?$/;
// Third-party bundles and generated output are out of contract scope
// (coverage/lcov reports are tooling output, never first-party source).
const EXCLUDED_SEGMENTS = ["node_modules", "dist", "coverage", "__tests__", "vendor"];

const HEX_RE = /#[0-9a-fA-F]{3,4}\b|#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{8}\b/g;
const NUM_HEX_RE = /\b0x[0-9a-fA-F]{6}\b/g;
const DIRECT_COLOR_RE = /\b(?:rgb|rgba|hsl|hsla|oklch|oklab)\((?!var\()/g;
const TAILWIND_COLOR_RE =
  /\b(?:text|bg|border|ring|fill|stroke|from|to|via|decoration)-(?:red|blue|green|yellow|orange|purple|violet|pink|cyan|teal|amber|emerald|indigo|slate|gray|grey|neutral|black|white|zinc|stone|lime|sky|rose|fuchsia)(?:-\d{2,3})?(?:\/\d{1,3})?\b/g;
const LEGACY_THEME_TOKEN_RE =
  /(?:--(?:silver|accent-electric(?:-glow)?|terminal-(?:green|amber|red|cyan|purple|blue))(?:-\d+)?|\b(?:text|bg|border|ring|fill|stroke|decoration)-silver-\d+\b)/g;
// Legacy hsl(var(--token)) / hsl(var(${...})) wrappers: a color function
// wrapping a token reference instead of resolving it.

// Comments and URLs are not runtime colors; strip them before matching so the
// scanner only sees literals the renderer could actually consume.
function stripComments(source) {
  return source
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/(^|[^:\\/'"])\/\/[^\n]*/g, "$1");
}

function scanFirstPartySource() {
  const files = [];
  const walk = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith(".")) continue;
      const path = join(dir, entry.name);
      if (EXCLUDED_SEGMENTS.some((segment) => path.split("/").includes(segment))) {
        continue;
      }
      if (entry.isDirectory()) walk(path);
      else if (SCAN_EXTENSIONS.test(entry.name) && !TEST_FILE.test(entry.name)) {
        files.push(path);
      }
    }
  };
  for (const subdir of ["apps", "packages"]) walk(join(repoRoot, subdir));

  const findings = {};
  for (const file of files.sort()) {
    const relativePath = relative(repoRoot, file);
    const exception = ALLOWED_FILES[relativePath];
    if (exception?.literals === "any") continue;
    const allowed = new Set(exception?.literals ?? []);
    const source = stripComments(readFileSync(file, "utf8"));
    const scanSource =
      relativePath === "packages/design-system/style.css"
        ? source.replace(
            /^\s*--(?:silver|accent-electric(?:-glow)?|terminal-(?:green|amber|red|cyan|purple|blue))[^:]*:.*$/gm,
            " ",
          )
        : source;
    const flagged = new Set();
    for (const match of scanSource.matchAll(HEX_RE)) {
      const literal = match[0].toLowerCase();
      if (!allowed.has(literal)) flagged.add(literal);
    }
    for (const match of scanSource.matchAll(NUM_HEX_RE)) {
      if (!allowed.has(match[0].toLowerCase())) flagged.add(match[0].toLowerCase());
    }
    for (const match of scanSource.matchAll(DIRECT_COLOR_RE)) flagged.add(match[0].toLowerCase());
    for (const match of scanSource.matchAll(TAILWIND_COLOR_RE)) flagged.add(match[0].toLowerCase());
    if (relativePath !== "packages/design-system/style.css") {
      for (const match of scanSource.matchAll(LEGACY_THEME_TOKEN_RE)) flagged.add(match[0].toLowerCase());
    }
    if (flagged.size) findings[relativePath] = [...flagged].sort();
  }
  return findings;
}

/**
 * Explicit exceptions. `literals: "any"` waives the check entirely (the file is
 * not owned by the UltiCode theme); a literal list pins the only allowed
 * values. All other files may only carry literals listed in DEBT_BASELINE.
 */
const ALLOWED_FILES = {
  "packages/design-system/style.css": {
    reason:
      "canonical CSS owner; only the 16 palette literals + derived garden semantic literals are allowed",
    literals: [...GARDEN_HEX, ...GARDEN_CSS_ONLY],
  },
  "packages/design-system/src/palette.ts": {
    reason: "canonical runtime bridge",
    literals: [...CANONICAL_HEX],
  },
  "packages/auth-ui/src/components/OAuthButton.vue": {
    reason: "external Google brand",
    literals: ["#34a853", "#4285f4", "#ea4335", "#fbbc05"],
  },
  "apps/console/src/composables/useAvatar.ts": {
    reason: "deterministic local SVG avatar illustration palette",
    literals: [
      "#0f766e",
      "#111827",
      "#172554",
      "#1f2937",
      "#2563eb",
      "#7c2d12",
      "#7c3aed",
      "#8d5524",
      "#9f1239",
      "#b45309",
      "#be123c",
      "#d99a6c",
      "#f59e0b",
      "#f6c7a7",
      "#ffffff",
    ],
  },
  "apps/console/src/types/contest.ts": {
    reason: "external Codeforces rating convention",
    literals: [
      "#0000ff",
      "#008000",
      "#03a89e",
      "#808080",
      "#aa00aa",
      "#ff0000",
      "#ff8c00",
    ],
  },
  "apps/management/public/placeholder.svg": {
    reason: "static Solarized placeholder artwork",
    literals: [...CANONICAL_HEX],
  },
  "apps/management/src/i18n/locales/en-US/modules/tags.ts": {
    reason: "garden sky-ink tag-color input example",
    literals: ["#46769b"],
  },
  "apps/management/src/i18n/locales/zh-CN/modules/tags.ts": {
    reason: "garden sky-ink tag-color input example",
    literals: ["#46769b"],
  },
  // ---------------------------------------------------------------------------
  // Landing design source (docs/GARDEN_DESIGN_SPEC.md). These files ARE the
  // origin of the Garden palette; their decorative literals are canonical by
  // definition. Sample-data strings ("#146", "#2904", "#704") are problem IDs
  // in copy, not colors. Do not add NEW files here — consume tokens instead.
  // ---------------------------------------------------------------------------
  "apps/console/src/i18n/locales/en-US/landing.ts": {
    reason: "landing sample-data strings (problem IDs), not colors",
    literals: ["#146", "#2904", "#704"],
  },
  "apps/console/src/i18n/locales/zh-CN/landing.ts": {
    reason: "landing sample-data strings (problem IDs), not colors",
    literals: ["#146", "#2904", "#704"],
  },
  "apps/console/src/views/landing/LandingView.vue": {
    reason: "landing design source; blueprint-frame decoration palette",
    literals: [
      "#19220e", "#3e4433", "#545c45", "#838f81", "#92b3cf",
      "#a2afa9", "#e3e1d1", "#f7f6f0", "bg-sky", "rgba(",
    ],
  },
  "apps/console/src/views/landing/components/FinalStorySection.vue": {
    reason: "landing design source; section decoration palette",
    literals: ["#19220e", "#203325", "#38402f", "#59614c", "#e3e1d1", "#ffffff", "rgba("],
  },
  "apps/console/src/views/landing/components/HeroSection.vue": {
    reason: "landing design source; hero showcase + accent palette",
    literals: [
      "#146", "#3f683b", "#588e67", "#6e5b28", "#8f4822", "#c0bcb0",
      "#e2e8d0", "#f2efe7", "#f4f2ea", "#faf9f5", "#fff", "bg-sky", "rgba(",
    ],
  },
  "apps/console/src/views/landing/components/LandingFooter.vue": {
    reason: "landing design source; footer decoration palette",
    literals: ["#588e67", "#8c9985", "#e3e1d1", "rgba("],
  },
  "apps/console/src/views/landing/components/LandingHeader.vue": {
    reason: "landing design source; translucent header surfaces",
    literals: ["#ffffff", "rgba("],
  },
  "apps/console/src/views/landing/components/ProductProofSection.vue": {
    reason: "landing design source; translucent proof surfaces",
    literals: ["bg-sky", "rgba("],
  },
  "apps/console/src/views/landing/components/UseCasesSection.vue": {
    reason: "landing design source; use-case card decoration palette",
    literals: ["#2d3a24", "#8c6228", "#eeece1", "bg-sky", "rgba("],
  },
};

const DEBT_BASELINE = {};

test("scanner contract rejects undeclared color literals in first-party source", () => {
  const findings = scanFirstPartySource();
  const allowed = new Set(Object.keys(ALLOWED_FILES));
  const debt = new Set(Object.keys(DEBT_BASELINE));

  const undeclared = Object.keys(findings)
    .filter((file) => !allowed.has(file) && !debt.has(file))
    .sort();
  const detail = undeclared
    .map((file) => `  ${file}: ${findings[file].join(", ")}`)
    .join("\n");
  assert.deepEqual(
    undeclared,
    [],
    `non-Solarized color literals outside the documented baseline:\n${detail}\n` +
      "Add legitimate external brand/data/vendor cases to ALLOWED_FILES or " +
      "task-owned migration entries to DEBT_BASELINE.",
  );

  for (const [file, entry] of Object.entries(ALLOWED_FILES)) {
    if (entry.literals === "any") continue;
    const actual = findings[file] ?? [];
    const extra = actual.filter((literal) => !entry.literals.includes(literal));
    assert.deepEqual(
      extra,
      [],
      `${file} (${entry.reason}) may only use ` +
        `${JSON.stringify(entry.literals)}, found ${JSON.stringify(extra)}`,
    );
  }

  for (const [file, expected] of Object.entries(DEBT_BASELINE)) {
    const actual = findings[file] ?? [];
    const extra = actual.filter((literal) => !expected.includes(literal));
    assert.deepEqual(
      extra,
      [],
      `${file} gained undeclared literals ${JSON.stringify(extra)} ` +
        `(pinned debt: ${JSON.stringify(expected)}); migrate them to the ` +
        "runtime bridge instead.",
    );
  }
});

