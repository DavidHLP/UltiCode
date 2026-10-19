import { computed, type Ref } from "vue";
import { THEME_CYCLE, type ThemeMode } from "./ThemeMode";
import {
  initAppearance,
  resetAppearanceForTest,
  setAppearanceMode,
  useAppearance,
} from "./appearance";

const theme = computed(() => useAppearance().settings.value.mode);

export function initTheme(): Ref<ThemeMode> {
  initAppearance();
  return theme;
}
export function setTheme(mode: ThemeMode): void {
  setAppearanceMode(mode);
}
export function cycleTheme(): ThemeMode {
  const mode = useAppearance().settings.value.mode;
  const next =
    THEME_CYCLE[(THEME_CYCLE.indexOf(mode) + 1) % THEME_CYCLE.length];
  setTheme(next);
  return next;
}
export function useColorTheme() {
  return { theme: initTheme(), setTheme, cycleTheme };
}
export function __resetForTest(): void {
  resetAppearanceForTest();
}
export { isThemeMode } from "./ThemeMode";
