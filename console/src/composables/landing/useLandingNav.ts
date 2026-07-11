/**
 * Landing-page shared navigation and roving-tabindex helpers.
 *
 * - `landingNavItems` is the single source of truth for the three real product
 *   surfaces the landing page links to (problem set, contest list, forum).
 *   Both the social proof bar and any future cross-section reuse must pull
 *   from this list — never redeclare the same `{ name }` routes inline.
 *
 * - `useRovingTablist` encapsulates the WAI-ARIA roving tabindex pattern
 *   (Arrow / Home / End keys) so tablist components don't reinvent it.
 */

import { nextTick, type Ref } from 'vue';

export interface LandingNavItem {
  readonly key: 'practice' | 'contest' | 'community';
  readonly to: { name: string };
  readonly i18nPath: string;
}

export const landingNavItems: ReadonlyArray<LandingNavItem> = [
  { key: 'practice', to: { name: 'problemset' }, i18nPath: 'landing.social.practice' },
  { key: 'contest', to: { name: 'contest-list' }, i18nPath: 'landing.social.contest' },
  { key: 'community', to: { name: 'forum-home' }, i18nPath: 'landing.social.community' },
] as const;

export interface RovingTablistOptions<K extends string> {
  selected: Ref<K>;
  items: ReadonlyArray<{ key: K }>;
  tabRefs: Ref<Record<K, HTMLButtonElement | null>>;
}

export interface RovingTablistApi<K extends string> {
  onKeydown: (event: KeyboardEvent, currentKey: K) => void;
  focusTab: (key: K) => void;
}

export function useRovingTablist<K extends string>(
  options: RovingTablistOptions<K>,
): RovingTablistApi<K> {
  const { selected, items, tabRefs } = options;

  const focusTab = (key: K) => {
    nextTick(() => {
      tabRefs.value[key]?.focus();
    });
  };

  const onKeydown = (event: KeyboardEvent, currentKey: K) => {
    const currentIndex = items.findIndex((item) => item.key === currentKey);
    if (currentIndex === -1) return;
    let nextIndex = currentIndex;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      nextIndex = (currentIndex + 1) % items.length;
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      nextIndex = (currentIndex - 1 + items.length) % items.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = items.length - 1;
    } else {
      return;
    }
    event.preventDefault();
    const nextKey = items[nextIndex].key;
    selected.value = nextKey;
    focusTab(nextKey);
  };

  return { onKeydown, focusTab };
}