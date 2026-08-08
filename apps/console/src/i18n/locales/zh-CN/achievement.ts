export default {
  // Achievement gallery
  title: "成就",
  description: "通过解决问题、参加比赛和贡献社区来追踪进度并获取徽章。",

  // Stats
  earned: "已获得",
  total: "总数",
  points: "积分",
  complete: "完成度",

  // Categories
  categories: {
    all: "全部",
    problemSolving: "解题",
    consistency: "连续",
    contest: "比赛",
    community: "社区",
  },

  // Empty state
  empty: {
    title: "暂无成就",
    description: "开始解题以获得你的第一个徽章！",
  },

  // Badge unlock toast
  unlock: {
    title: "成就解锁！",
    earnedPoints: "你获得了 {points} 积分！",
  },
} as const;
