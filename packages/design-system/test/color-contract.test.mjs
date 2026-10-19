import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve, relative } from 'node:path';
import test from 'node:test';
import { UI_PALETTE, readCssColor } from '../src/palette.ts';

const repo = resolve(process.cwd(), '../..');

test('runtime fallbacks use the default theme and do not enforce an obsolete palette', () => {
  assert.equal(readCssColor('--background', UI_PALETTE.background), '#ffffff');
  assert.equal(readCssColor('var(--foreground)', UI_PALETTE.foreground), '#171717');
  assert.equal(readCssColor('--unknown', '#fafafa'), '#fafafa');
  for (const color of Object.values(UI_PALETTE)) assert.match(color, /^#[a-f\d]{6}$/i);
});

test('reads browser tokens and rejects executable or malformed variable references', () => {
  const computed = new Map([['--background', ' #ffffff '], ['--primary', '#abc'], ['--bad', '#12345']]);
  globalThis.document = { documentElement: {} };
  globalThis.getComputedStyle = () => ({ getPropertyValue: name => computed.get(name) ?? '' });
  try {
    assert.equal(readCssColor('--background', '#000000'), '#ffffff');
    assert.equal(readCssColor('var(--primary)', '#000000'), '#abc');
    assert.equal(readCssColor('--bad', '#000000'), '#000000');
    for (const invalid of ['red', 'var(--primary, red)', '--primary);background:url(javascript:alert(1))', 'url(https://evil.example)']) {
      assert.equal(readCssColor(invalid, '#171717'), '#171717');
    }
  } finally { delete globalThis.document; delete globalThis.getComputedStyle; }
});

test('converts OKLCH for canvas renderers without dropping alpha', () => {
  let pixels = [1, 2, 3, 255];
  globalThis.document = {
    documentElement: {},
    createElement: () => ({ getContext: () => ({ canvas: {}, fillRect() {}, getImageData: () => ({ data: pixels }) }) }),
  };
  globalThis.getComputedStyle = () => ({ getPropertyValue: () => 'oklch(1 0 0 / 10%)' });
  globalThis.CSS = { supports: () => true };
  try {
    assert.equal(readCssColor('--border', '#000000'), '#010203');
    pixels = [255, 255, 255, 26];
    assert.equal(readCssColor('--border', '#000000'), '#ffffff1a');
  } finally { delete globalThis.document; delete globalThis.getComputedStyle; delete globalThis.CSS; }
});

const exceptions = {
  'packages/auth-ui/src/components/OAuthButton.vue': ['#34a853', '#4285f4', '#ea4335', '#fbbc05'],
  'apps/console/src/composables/useAvatar.ts': ['#0f766e', '#111827', '#172554', '#1f2937', '#2563eb', '#7c2d12', '#7c3aed', '#8d5524', '#9f1239', '#b45309', '#be123c', '#d99a6c', '#f59e0b', '#f6c7a7', '#ffffff'],
  'apps/console/src/types/contest.ts': ['#0000ff', '#008000', '#03a89e', '#808080', '#aa00aa', '#ff0000', '#ff8c00'],
  'apps/management/src/i18n/locales/en-US/modules/tags.ts': ['#46769b'],
  'apps/management/src/i18n/locales/zh-CN/modules/tags.ts': ['#46769b'],
  'apps/landing/src/i18n/locales/en-US/landing.ts': ['#146', '#2904', '#704'],
  'apps/landing/src/i18n/locales/zh-CN/landing.ts': ['#146', '#2904', '#704'],
};

// External brand, rating and avatar colors are data, not theme options.
test('app and shared presentation source consume semantic colors', () => {
  const findings = [];
  function walk(directory) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      if (['node_modules', 'dist', '__tests__', 'vendor'].includes(entry.name)) continue;
      const path = resolve(directory, entry.name);
      if (entry.isDirectory()) { walk(path); continue; }
      if (!/\.(vue|ts|css)$/.test(path) || /\.(spec|test)\.ts$/.test(path)) continue;
      const file = relative(repo, path);
      const source = readFileSync(path, 'utf8').replace(/\/\*[\s\S]*?\*\/|<!--[\s\S]*?-->/g, '').replace(/^\s*\/\/[^\n]*/gm, '');
      const colors = source.match(/#[a-f\d]{6}\b|#[a-f\d]{8}\b|#[a-f\d]{3,4}\b|\b(?:bg|text|border|ring|fill|stroke)-(?:red|blue|green|yellow|orange|violet|pink|cyan|teal|amber|emerald|indigo|slate|gray|neutral|zinc|stone|lime|sky|rose|fuchsia)-\d{2,3}\b/gi) ?? [];
      const extra = [...new Set(colors.map(color => color.toLowerCase()))].filter(color => !(exceptions[file] ?? []).includes(color));
      if (extra.length) findings.push(`${file}: ${extra.join(', ')}`);
    }
  }
  for (const dir of ['apps/console/src', 'apps/management/src', 'apps/landing/src', 'packages/auth-ui/src', 'packages/sidebar-menu/src', 'packages/badge-config/src']) walk(resolve(repo, dir));
  assert.deepEqual(findings, [], 'Use semantic tokens; keep external data exceptions narrow.\n' + findings.join('\n'));
});
