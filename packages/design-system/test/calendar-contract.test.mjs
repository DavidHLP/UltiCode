import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const calendar = readFileSync('src/components/ui/calendar/Calendar.vue', 'utf8');

test('shared calendar marks completed dates without forwarding app data to Reka UI', () => {
  assert.match(calendar, /completedDates\?: string\[\]/);
  assert.match(calendar, /reactiveOmit\([\s\S]*"completedDates"/);
  assert.match(calendar, /props\.completedDates\?\.includes\(weekDate\.toString\(\)\)/);
  assert.match(calendar, /bg-status-warning-surface/);
  assert.match(calendar, /border-status-warning-mark\/45/);
});
