import { computed, type ComputedRef } from "vue";
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
 * The achievement listener doesn't include the achievement name in metadata,
 * so we fall back to extracting it from the rendered title when needed.
 */
function extractFollowUsername(title: string): string {
  // Backend title is "<username> followed you" (English)
  const match = title.match(/^(.+?)\s+followed you/i);
  if (match) return match[1] ?? "";
  return "";
}

function extractContestTitle(title: string): string {
  // Backend title is "Contest '<title>' starts in 24 hours" or
  // "Contest '<title>' starts in 1 hour".
  const match = title.match(/^Contest\s+['"](.+?)['"]\s+starts in/i);
  if (match) return match[1] ?? "";
  return "";
}

function isContestReminder24h(title: string): boolean {
  return /starts in 24 hours/i.test(title);
}

export interface NotificationDisplay {
  title: string;
  body: string;
}

export function useNotificationI18n(): {
  display: (notification: NotificationItem) => ComputedRef<NotificationDisplay>;
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
          body: problemTitle
            ? t(`${base}.body`, { problemTitle })
            : "",
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
        const use24h = isContestReminder24h(notification.title);
        const titleKey = use24h
          ? `${base}.title24h`
          : `${base}.title1h`;
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

  return {
    display: (notification) => computed(() => buildDisplay(notification)),
  };
}
