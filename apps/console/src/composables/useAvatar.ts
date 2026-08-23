import { computed, type MaybeRef, unref } from "vue";

const AVATAR_COLORS = ["#0f766e", "#2563eb", "#7c3aed", "#be123c", "#b45309"];

function localAvatarDataUrl(username: string): string {
  let hash = 0;
  for (const character of username) {
    hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
  }
  const color = AVATAR_COLORS[hash % AVATAR_COLORS.length];
  const skin = ["#f6c7a7", "#d99a6c", "#8d5524"][hash % 3];
  const hair = ["#1f2937", "#7c2d12", "#f59e0b"][hash % 3];
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128"><rect width="128" height="128" fill="${color}"/><circle cx="64" cy="64" r="52" fill="#ffffff" fill-opacity=".14"/><path d="M22 128c4-27 20-39 42-39s38 12 42 39" fill="#172554"/><circle cx="64" cy="61" r="29" fill="${skin}"/><path d="M35 60c0-25 13-37 29-37s29 12 29 37c-8-9-17-13-29-13S43 51 35 60Z" fill="${hair}"/><circle cx="53" cy="63" r="3" fill="#111827"/><circle cx="75" cy="63" r="3" fill="#111827"/><path d="M54 77c6 5 14 5 20 0" fill="none" stroke="#9f1239" stroke-linecap="round" stroke-width="3"/></svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

export function resolveAvatar(
  username?: string,
  avatar?: string | null,
): string {
  const name = username || "anonymous";
  return avatar && avatar.trim().length > 0
    ? avatar
    : localAvatarDataUrl(name);
}

/**
 * Normalize an avatar URL. A custom App profile avatar wins; otherwise use a
 * deterministic local SVG so every user has a concrete image without a
 * third-party network dependency.
 */
export function useAvatar(
  username: MaybeRef<string | undefined>,
  avatar: MaybeRef<string | undefined>,
) {
  const normalizedAvatar = computed(() => {
    return resolveAvatar(unref(username), unref(avatar));
  });

  return { normalizedAvatar };
}
