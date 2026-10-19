import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { getDifficultyBadgeClass } from '../src/variants.ts';

const css = readFileSync('style.css', 'utf8');
const typography = readFileSync('../theme/src/typography.css', 'utf8');
const manifest = JSON.parse(readFileSync('package.json', 'utf8'));

test('one shared UI owns official registry components and public exports', () => {
  assert.equal(manifest.exports['./style.css'], './style.css');
  assert.equal(manifest.exports['./ui/*'].import, './src/components/ui/*/index.ts');
  const registry = JSON.parse(readFileSync('components.json', 'utf8'));
  assert.equal(registry.style, 'reka-nova');
  assert.equal(registry.rtl, true);
  for (const app of ['console', 'management', 'landing']) assert.ok(css.includes(`@source "../../apps/${app}/src"`));
});

test('publishes official semantic tokens without terminal palette aliases or geometry overrides', () => {
  for (const token of ['background', 'foreground', 'card', 'popover', 'primary', 'secondary', 'muted', 'accent', 'border', 'input', 'ring', 'destructive', 'sidebar', 'chart-1', 'chart-5']) {
    assert.ok(css.includes(`--color-${token}: var(--${token})`), token);
  }
  assert.doesNotMatch(css, /solarized|terminal-|\.rounded-none\s*\{/);
  assert.match(css, /\.dark\s*\{/);
  assert.ok(css.includes('@import "shadcn-vue/tailwind.css"'));
  const official = readFileSync('node_modules/shadcn-vue/dist/tailwind.css', 'utf8');
  for (const state of ['data-open', 'data-checked', 'data-active', 'data-horizontal']) assert.ok(official.includes(`@custom-variant ${state}`));
});

test('all controlled appearance choices affect tokens or official component states', () => {
  for (const base of ['zinc', 'slate', 'stone', 'gray']) assert.ok(css.includes(`data-appearance-base="${base}"`));
  for (const accent of ['blue', 'violet', 'emerald', 'orange']) {
    assert.ok(css.includes(`:root[data-appearance-accent="${accent}"]`));
    assert.ok(css.includes(`:root.dark[data-appearance-accent="${accent}"]`));
  }
  assert.ok(css.includes('data-appearance-chart="contrast"'));
  assert.ok(css.includes('data-appearance-menu="solid"'));
  for (const radius of ['none', 'small', 'large']) assert.ok(css.includes(`data-appearance-radius="${radius}"`));
  for (const font of ['serif', 'mono']) assert.ok(typography.includes(`data-appearance-font="${font}"`));
  assert.ok(typography.includes('data-uc-density="compact"'));
});

test('status and difficulty semantics remain fixed and unknown values fall back safely', () => {
  for (const state of ['success', 'warning', 'error', 'info', 'special']) {
    assert.ok(css.includes(`--status-${state}-mark:`));
    assert.ok(css.includes(`--status-${state}-surface:`));
  }
  assert.ok(getDifficultyBadgeClass('easy').includes('status-success'));
  assert.ok(getDifficultyBadgeClass('MEDIUM').includes('status-warning'));
  assert.ok(getDifficultyBadgeClass('hard').includes('status-error'));
  assert.equal(getDifficultyBadgeClass('unknown'), getDifficultyBadgeClass('<script>'));
});

test('typography respects native font availability, touch targets and reduced motion', () => {
  assert.doesNotMatch(typography, /https?:|fontsource|Instrument Serif|Newsreader/);
  assert.match(typography, /--uc-text-2xs:\s*0\.75rem/);
  assert.match(css, /@media \(pointer: coarse\)/);
  assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
});
