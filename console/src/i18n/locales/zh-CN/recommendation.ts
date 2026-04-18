export default {
  title: "题目推荐",
  description: {
    daily: "根据你的练习记录和掌握程度，为你智能推荐的每日题目",
    weakPoints: "针对你薄弱的知识点进行专项强化训练",
    challenge: "突破舒适区，挑战更高难度的题目",
    similar: "查找与指定题目相似的练习题，巩固同类算法",
  },
  filter: {
    tags: "标签筛选",
    allTags: "全部标签",
    all: "全选",
    refresh: "刷新",
  },
  card: {
    score: "推荐指数",
    reason: "推荐理由",
  },
  empty: {
    daily: "暂无每日推荐，快去做几道题吧！",
    "weak-points": "暂无薄弱点数据，继续练习以获取更精准的推荐",
    challenge: "暂无挑战题目，请先完成更多中等难度题目",
    similar: "请搜索题目以查找相似题目",
  },
  search: {
    placeholder: "搜索题目...",
    noResults: "未找到相关题目",
  },
} as const;
