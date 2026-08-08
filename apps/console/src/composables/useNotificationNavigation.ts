import { useRouter } from "vue-router";
import { useNotificationStore } from "@/stores/notification";
import type { NotificationItem } from "@/types/notification";

/**
 * Safe navigation target for a notification link.
 *
 * - `external` — an explicit `http(s)` URL the app opens in a new tab.
 * - `internal` — a single-leading-slash app route handled by Vue Router.
 * - `none` — no navigation (covers `javascript:`, `data:`, `vbscript:`,
 *   protocol-relative `//host`, `mailto:`, and any other non-route string).
 */
export type NotificationTarget =
  | { kind: "external"; href: string }
  | { kind: "internal"; to: string }
  | { kind: "none" };

/**
 * Classify a notification link into a safe navigation target.
 *
 * The notification `link` originates from the backend and is untrusted at the
 * presentation layer. This is the single owner of link policy for the inbox
 * workflow so the badge and full-inbox adapters cannot diverge: only an
 * explicit `http(s)` URL is treated as external, and only a path that starts
 * with a single `/` (not protocol-relative `//`) is treated as an app route.
 * Everything else — `javascript:`, `data:`, `vbscript:`, `//host`, `mailto:` —
 * is dropped to `none` so it is never passed to `window.open` or `router.push`.
 */
export function resolveNotificationTarget(
  link: string | null | undefined,
): NotificationTarget {
  if (!link) {
    return { kind: "none" };
  }
  if (/^https?:\/\//i.test(link)) {
    return { kind: "external", href: link };
  }
  if (link.startsWith("/") && !link.startsWith("//")) {
    return { kind: "internal", to: link };
  }
  return { kind: "none" };
}

/**
 * Owns the click workflow for a notification: mark it read (best-effort,
 * keeping the unread state consistent across adapters) and then navigate to
 * the safely-classified target. Both notification presentations delegate here
 * so activation and link policy live in one place.
 */
export function useNotificationNavigation() {
  const router = useRouter();
  const notificationStore = useNotificationStore();

  async function open(notification: NotificationItem): Promise<void> {
    if (!notification.isRead) {
      try {
        await notificationStore.markAsRead(notification.id, true);
      } catch {
        // Read-state update is best-effort; navigation should still proceed.
      }
    }
    const target = resolveNotificationTarget(notification.link);
    if (target.kind === "external") {
      window.open(target.href, "_blank", "noopener,noreferrer");
    } else if (target.kind === "internal") {
      await router.push(target.to);
    }
  }

  return { open, resolveNotificationTarget };
}
