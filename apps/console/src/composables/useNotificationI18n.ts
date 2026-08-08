import { useI18n } from "vue-i18n";
import type { NotificationItem } from "@/types/notification";

/**
 * Normalize a backend type value to one of the supported TS NotificationType
 * values. The achievement listener writes the type in lowercase while every
 * other listener writes uppercase; accept both shapes.
 */
function normalizeType(raw: string | undefined | null): string {
  if (!raw) return "system";
  const upper = raw.toUpperCase();
  switch (upper) {
    case "ACHIEVEMENT":
    case "BADGE_EARNED":
      return "achievement";
    case "COMMENT":
    case "REPLY":
    case "MENTION":
    case "UPVOTE":
    case "FOLLOW":
    case "SUBMISSION":
    case "CONTEST":
    case "CONTEST_REMINDER":
    case "SYSTEM":
      return upper.toLowerCase();
    default:
      return "system";
  }
}

function readMetaString(
  meta: NotificationItem["metadata"],
  key: string,
): string {
  if (!meta || typeof meta !== "object") return "";
  const value = (meta as Record<string, unknown>)[key];
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return "";
}

/**
 * Fallback extractors for legacy notifications whose listeners did not write
 * the relevant fields into `metadata`. They are **only** invoked when the
 * metadata field is absent.
 *
 * Limitation: these patterns assume the backend title is the English template
 * (e.g. "<user> followed you"). If the backend ever emits localized titles
 * for a non-English locale, the regexes will silently fail and the
 * notification will fall back to the raw `notification.title`. New listeners
 * should populate `metadata` instead of relying on these patterns.
 */
function extractFollowUsername(title: string): string {
  // Backend title is "<username> followed you" (English template).
  const match = title.match(/^(.+?)\s+followed you/i);
  return match ? match[1] : "";
}

function extractContestTitle(title: string): string {
  // Backend title is "Contest '<title>' starts in 24 hours" or
  // "Contest '<title>' starts in 1 hour".
  const match = title.match(/^Contest\s+['"](.+?)['"]\s+starts in/i);
  return match ? match[1] : "";
}

function isContestReminder24h(title: string): boolean {
  // Backend writes "starts in 24h" (reminderType="24h"). Also accept the
  // spelled-out "24 hours" form for rows persisted before reminderType
  // metadata became the structured source of truth.
  return /starts in 24\s*h(ours?)?/i.test(title);
}

export interface NotificationDisplay {
  title: string;
  body: string;
}

export function useNotificationI18n(): {
  display: (notification: NotificationItem) => NotificationDisplay;
} {
  const { t } = useI18n();

  function buildDisplay(notification: NotificationItem): NotificationDisplay {
    const type = normalizeType(notification.type);
    const meta = notification.metadata ?? null;
    const base = `personal.notifications.templates.${type}`;

    switch (type) {
      case "submission": {
        const rawStatus = readMetaString(meta, "status") || notification.title;
        // Strip the "Submission judged: " prefix if the backend already
        // produced the canonical title, so we can re-translate it cleanly.
        const status = rawStatus.replace(/^Submission judged:\s*/i, "");
        const statusKey = `personal.notifications.templates.submission.status.${status}`;
        const translatedStatus = t(statusKey);
        const statusText =
          translatedStatus === statusKey ? status : translatedStatus;
        const problemTitle = readMetaString(meta, "problemTitle");
        return {
          title: t(`${base}.title`, { status: statusText }),
          body: problemTitle ? t(`${base}.body`, { problemTitle }) : "",
        };
      }
      case "achievement": {
        return {
          title: t(`${base}.title`),
          body: t(`${base}.body`),
        };
      }
      case "follow": {
        const username =
          readMetaString(meta, "username") ||
          extractFollowUsername(notification.title);
        return {
          title: username
            ? t(`${base}.title`, { username })
            : notification.title,
          body: notification.body,
        };
      }
      case "contest_reminder": {
        const contestTitle =
          readMetaString(meta, "contestTitle") ||
          extractContestTitle(notification.title);
        // reminderType metadata ("24h"/"1h") is the structured source of
        // truth; fall back to the title regex only for rows lacking metadata.
        const reminderType = readMetaString(meta, "reminderType");
        const use24h =
          reminderType === "24h" || isContestReminder24h(notification.title);
        const titleKey = use24h ? `${base}.title24h` : `${base}.title1h`;
        return {
          title: contestTitle
            ? t(titleKey, { contestTitle })
            : notification.title,
          body: t(`${base}.body`),
        };
      }
      case "contest": {
        const contestTitle = readMetaString(meta, "contestTitle");
        return {
          title: contestTitle
            ? t(`${base}.title`, { contestTitle })
            : notification.title,
          body: notification.body,
        };
      }
      case "comment":
      case "reply":
      case "mention":
      case "upvote": {
        const username = readMetaString(meta, "username");
        return {
          title: username
            ? t(`${base}.title`, { username })
            : notification.title,
          body: notification.body,
        };
      }
      case "system":
      default: {
        return {
          title: notification.title,
          body: notification.body,
        };
      }
    }
  }

  // `t()` from vue-i18n is reactive to the current locale, and Vue templates
  // re-render when locale changes. Wrapping the result in a `computed()` per
  // call would create N reactive wrappers for an N-item list with no
  // additional correctness. Callers should treat the return value as a plain
  // object re-evaluated on every render.
  return {
    display: (notification) => buildDisplay(notification),
  };
}
