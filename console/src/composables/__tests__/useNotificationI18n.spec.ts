import { describe, it, expect, vi, beforeEach } from "vitest";
import type { NotificationItem, NotificationType } from "@/types/notification";
import type { NotificationDisplay } from "../useNotificationI18n";

// Build a translation table covering the keys exercised by the composable.
// Anything not in the table resolves to the key itself, which mirrors
// vue-i18n's behavior for missing keys and lets us assert lookup behavior.
const translations: Record<string, string> = {
  // submission status
  "personal.notifications.templates.submission.status.Accepted": "通过",
  "personal.notifications.templates.submission.status.Pending": "等待中",
  // submission template
  "personal.notifications.templates.submission.title": "提交判题: {status}",
  "personal.notifications.templates.submission.body": "题目: {problemTitle}",
  // achievement
  "personal.notifications.templates.achievement.title": "获得成就",
  "personal.notifications.templates.achievement.body": "你解锁了一个新徽章。",
  // follow
  "personal.notifications.templates.follow.title": "{username} 关注了你",
  // contest reminder
  "personal.notifications.templates.contest_reminder.title24h":
    "比赛「{contestTitle}」还有 24 小时开始",
  "personal.notifications.templates.contest_reminder.title1h":
    "比赛「{contestTitle}」还有 1 小时开始",
  "personal.notifications.templates.contest_reminder.body": "请做好参赛准备。",
  // contest
  "personal.notifications.templates.contest.title": "比赛更新: {contestTitle}",
  // comment / reply / mention / upvote
  "personal.notifications.templates.comment.title": "{username} 评论了你的帖子",
  "personal.notifications.templates.reply.title": "{username} 回复了你",
  "personal.notifications.templates.mention.title": "{username} 提到了你",
  "personal.notifications.templates.upvote.title": "{username} 赞了你的帖子",
  // system
  "personal.notifications.templates.system.title": "系统通知",
  "personal.notifications.templates.system.body": "",
};

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      const template = translations[key] ?? key;
      if (!params) return template;
      return template.replace(/\{(\w+)\}/g, (_, name) => {
        const v = params[name];
        return v === undefined || v === null ? `{${name}}` : String(v);
      });
    },
  }),
}));

import { useNotificationI18n } from "../useNotificationI18n";

function makeNotification(
  overrides: Partial<NotificationItem> = {},
): NotificationItem {
  return {
    id: "n-1",
    title: "raw title",
    body: "raw body",
    type: "SYSTEM" as NotificationType,
    category: "SYSTEM",
    link: null,
    metadata: null,
    isRead: false,
    createdAt: "2026-06-08T00:00:00Z",
    ...overrides,
  };
}

describe("useNotificationI18n", () => {
  let display: (n: NotificationItem) => NotificationDisplay;

  beforeEach(() => {
    display = useNotificationI18n().display;
  });

  describe("type normalization", () => {
    it("passes through uppercase known types", () => {
      const result = display(
        makeNotification({ type: "COMMENT" as NotificationType }),
      );
      // comment template title is "{username} 评论了你的帖子" — without
      // metadata.username we fall back to raw title.
      expect(result.title).toBe("raw title");
      expect(result.body).toBe("raw body");
    });

    it("accepts lowercase legacy types (case-insensitive)", () => {
      const result = display(
        makeNotification({
          type: "comment" as unknown as NotificationType,
        }),
      );
      // Falls back to raw title because no metadata.username.
      expect(result.title).toBe("raw title");
    });

    it("maps BADGE_EARNED to the achievement template", () => {
      const result = display(
        makeNotification({
          type: "BADGE_EARNED" as unknown as NotificationType,
        }),
      );
      expect(result.title).toBe("获得成就");
      expect(result.body).toBe("你解锁了一个新徽章。");
    });

    it("falls back to system template for unknown types", () => {
      const result = display(
        makeNotification({
          type: "WHATEVER" as unknown as NotificationType,
        }),
      );
      expect(result.title).toBe("raw title");
      expect(result.body).toBe("raw body");
    });

    it("treats empty/null type as system", () => {
      const result = display(
        makeNotification({ type: "" as unknown as NotificationType }),
      );
      expect(result.title).toBe("raw title");
    });
  });

  describe("submission branch", () => {
    it("translates status and problemTitle from metadata", () => {
      const result = display(
        makeNotification({
          type: "SUBMISSION",
          metadata: { status: "Accepted", problemTitle: "Two Sum" },
        }),
      );
      expect(result.title).toBe("提交判题: 通过");
      expect(result.body).toBe("题目: Two Sum");
    });

    it("strips 'Submission judged: ' prefix from the fallback title", () => {
      const result = display(
        makeNotification({
          type: "SUBMISSION",
          title: "Submission judged: Pending",
          metadata: {},
        }),
      );
      // The strip happens before lookup; with no metadata.status we use the
      // title (post-strip) as the status key. Lookup key is
      // "...submission.status.Pending" which IS in the table.
      expect(result.title).toBe("提交判题: 等待中");
    });

    it("omits body when problemTitle is missing", () => {
      const result = display(
        makeNotification({
          type: "SUBMISSION",
          metadata: { status: "Accepted" },
        }),
      );
      expect(result.title).toBe("提交判题: 通过");
      expect(result.body).toBe("");
    });

    it("passes status text through when no translation exists", () => {
      const result = display(
        makeNotification({
          type: "SUBMISSION",
          metadata: { status: "Custom Verdict" },
        }),
      );
      // The status key is not in the table → t() returns the key, and the
      // composable falls back to the raw status string.
      expect(result.title).toBe("提交判题: Custom Verdict");
    });
  });

  describe("follow branch", () => {
    it("uses metadata.username when present", () => {
      const result = display(
        makeNotification({
          type: "FOLLOW",
          metadata: { username: "alice" },
        }),
      );
      expect(result.title).toBe("alice 关注了你");
    });

    it("falls back to regex extraction of English title", () => {
      const result = display(
        makeNotification({
          type: "FOLLOW",
          title: "bob followed you",
        }),
      );
      expect(result.title).toBe("bob 关注了你");
    });

    it("falls back to raw title when neither metadata nor regex match", () => {
      const result = display(
        makeNotification({
          type: "FOLLOW",
          title: "Alice 关注了你",
        }),
      );
      // Chinese title doesn't match the English regex; raw title preserved.
      expect(result.title).toBe("Alice 关注了你");
    });
  });

  describe("contest_reminder branch", () => {
    it("uses title24h for 24-hour reminder with metadata", () => {
      const result = display(
        makeNotification({
          type: "CONTEST_REMINDER",
          metadata: { contestTitle: "Weekly Cup" },
          title: "Contest 'Weekly Cup' starts in 24 hours",
        }),
      );
      expect(result.title).toBe("比赛「Weekly Cup」还有 24 小时开始");
      expect(result.body).toBe("请做好参赛准备。");
    });

    it("uses title1h for 1-hour reminder with metadata", () => {
      const result = display(
        makeNotification({
          type: "CONTEST_REMINDER",
          metadata: { contestTitle: "Weekly Cup" },
          title: "Contest 'Weekly Cup' starts in 1 hour",
        }),
      );
      expect(result.title).toBe("比赛「Weekly Cup」还有 1 小时开始");
    });

    it("falls back to title regex when metadata is missing", () => {
      const result = display(
        makeNotification({
          type: "CONTEST_REMINDER",
          title: "Contest 'Spring Open' starts in 24 hours",
        }),
      );
      expect(result.title).toBe("比赛「Spring Open」还有 24 小时开始");
    });

    it("falls back to raw title when nothing matches", () => {
      const result = display(
        makeNotification({
          type: "CONTEST_REMINDER",
          title: "比赛提醒",
        }),
      );
      expect(result.title).toBe("比赛提醒");
    });

    it("uses metadata.reminderType to pick the 24h vs 1h template", () => {
      const result24 = display(
        makeNotification({
          type: "CONTEST_REMINDER",
          metadata: { contestTitle: "Cup", reminderType: "24h" },
          title: "Contest 'Cup' starts in 24h",
        }),
      );
      expect(result24.title).toBe("比赛「Cup」还有 24 小时开始");

      const result1 = display(
        makeNotification({
          type: "CONTEST_REMINDER",
          metadata: { contestTitle: "Cup", reminderType: "1h" },
          title: "Contest 'Cup' starts in 1h",
        }),
      );
      expect(result1.title).toBe("比赛「Cup」还有 1 小时开始");
    });
  });

  describe("contest branch", () => {
    it("uses contestTitle from metadata", () => {
      const result = display(
        makeNotification({
          type: "CONTEST",
          metadata: { contestTitle: "Open" },
        }),
      );
      expect(result.title).toBe("比赛更新: Open");
    });

    it("falls back to raw title when no metadata", () => {
      const result = display(makeNotification({ type: "CONTEST" }));
      expect(result.title).toBe("raw title");
    });
  });

  describe("comment / reply / mention / upvote branches", () => {
    it.each([
      ["COMMENT", "comment", "{username} 评论了你的帖子"],
      ["REPLY", "reply", "{username} 回复了你"],
      ["MENTION", "mention", "{username} 提到了你"],
      ["UPVOTE", "upvote", "{username} 赞了你的帖子"],
    ])("%s uses metadata.username when present", (type) => {
      const result = display(
        makeNotification({
          type: type as NotificationType,
          metadata: { username: "carol" },
        }),
      );
      // The expected translated string for each type.
      const expected: Record<string, string> = {
        COMMENT: "carol 评论了你的帖子",
        REPLY: "carol 回复了你",
        MENTION: "carol 提到了你",
        UPVOTE: "carol 赞了你的帖子",
      };
      expect(result.title).toBe(expected[type]);
    });

    it("falls back to raw title when no username metadata", () => {
      const result = display(
        makeNotification({ type: "COMMENT" as NotificationType }),
      );
      expect(result.title).toBe("raw title");
    });
  });

  describe("system and default branches", () => {
    it("returns raw title/body for system notifications", () => {
      const result = display(makeNotification({ type: "SYSTEM" }));
      expect(result.title).toBe("raw title");
      expect(result.body).toBe("raw body");
    });
  });

  describe("readMetaString edge cases", () => {
    it("coerces numeric metadata to string", () => {
      // The follow branch with no metadata.username — but the username
      // extraction uses metadata first. Use a SUBMISSION with a numeric
      // problemTitle to verify coercion.
      const result = display(
        makeNotification({
          type: "SUBMISSION",
          metadata: {
            status: "Accepted",
            problemTitle: 12345 as unknown as string,
          },
        }),
      );
      expect(result.body).toBe("题目: 12345");
    });

    it("coerces boolean metadata to string", () => {
      const result = display(
        makeNotification({
          type: "FOLLOW",
          metadata: { username: true as unknown as string },
        }),
      );
      // The template "{username} 关注了你" receives "true".
      expect(result.title).toBe("true 关注了你");
    });

    it("ignores non-string/number/boolean metadata values", () => {
      const result = display(
        makeNotification({
          type: "FOLLOW",
          metadata: { username: { nested: "obj" } as unknown as string },
        }),
      );
      // No usable username → fall back to raw title.
      expect(result.title).toBe("raw title");
    });
  });
});
