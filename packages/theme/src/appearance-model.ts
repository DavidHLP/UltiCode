import { THEME_MODES, type ThemeMode } from "./ThemeMode";

export const APPEARANCE_OPTIONS = {
  accent: ["neutral", "blue", "violet", "emerald", "orange"],
  base: ["neutral", "zinc", "slate", "stone", "gray"],
  chart: ["default", "contrast"],
  font: ["system", "serif", "mono"],
  radius: ["none", "small", "medium", "large"],
  density: ["comfortable", "compact"],
  menu: ["subtle", "solid"],
  mode: THEME_MODES,
} as const;

export type Appearance = {
  [
    K in keyof typeof APPEARANCE_OPTIONS
  ]: (typeof APPEARANCE_OPTIONS)[K][number];
};
export type AppearanceKey = keyof Appearance;
export const APPEARANCE_STORAGE_PREFIX = "ulticode-appearance:v1:";
export const APPEARANCE_ACCOUNT_KEY = "ulticode-appearance-account";
export const DEFAULT_APPEARANCE: Readonly<Appearance> = Object.freeze({
  accent: "neutral",
  base: "neutral",
  chart: "default",
  font: "system",
  radius: "medium",
  density: "comfortable",
  menu: "subtle",
  mode: "system",
});
export const APPEARANCE_PRESETS = {
  default: DEFAULT_APPEARANCE,
  focus: {
    ...DEFAULT_APPEARANCE,
    accent: "blue",
    base: "slate",
    font: "system",
    density: "compact",
  },
  warm: {
    ...DEFAULT_APPEARANCE,
    accent: "orange",
    base: "stone",
    radius: "large",
    menu: "solid",
  },
} as const satisfies Record<string, Readonly<Appearance>>;

export function parseAppearance(value: unknown): Appearance {
  const result = { ...DEFAULT_APPEARANCE };
  if (!value || typeof value !== "object" || Array.isArray(value))
    return result;
  for (const key of Object.keys(APPEARANCE_OPTIONS) as AppearanceKey[]) {
    const candidate = Object.hasOwn(value, key)
      ? (value as Record<string, unknown>)[key]
      : undefined;
    if ((APPEARANCE_OPTIONS[key] as readonly unknown[]).includes(candidate)) {
      Object.assign(result, { [key]: candidate });
    }
  }
  return result;
}

export function decodeAppearance(value: string | null): Appearance {
  try {
    return parseAppearance(value ? JSON.parse(value) : null);
  } catch {
    return { ...DEFAULT_APPEARANCE };
  }
}

export function appearanceStorageKey(
  accountId: string | number | null,
): string {
  return (
    APPEARANCE_STORAGE_PREFIX +
    (accountId === null
      ? "guest"
      : `user:${encodeURIComponent(String(accountId))}`)
  );
}

export function appearanceWithMode(
  value: Appearance,
  mode: ThemeMode,
): Appearance {
  return parseAppearance({ ...value, mode });
}
