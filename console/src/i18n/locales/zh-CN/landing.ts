export default {
  nav: {
    primary: "主导航",
    footer: "页脚导航",
    problems: "题库",
    contests: "竞赛",
    community: "社区",
    rankings: "排行榜",
    login: "登录",
    register: "注册",
    enter: "进入平台",
    settings: "外观与语言",
  },
  hero: {
    eyebrow: "UltiCode / 在线评测",
    title: "让每一次提交，都成为可见的进步。",
    subtitle: "从编写、运行到即时评测，在题目、竞赛与社区中持续验证你的能力。",
    ctaPrimary: "开始刷题",
    ctaSecondary: "浏览竞赛",
    sceneAlt: "装饰性三维场景：镜头跟随一次代码提交穿越评测世界",
  },
  parse: {
    eyebrow: "01 / 解析",
    title: "代码落下，结构浮现。",
    body: "在浏览器里编写并运行代码。你写下的每一行，都会被解析成可以执行、可以验证的结构。",
    points: {
      run: "在线编写与运行，无需配置本地环境",
      languages: "七种语言：JavaScript、TypeScript、Python、Java、C++、Go、C",
      debug: "清晰的编辑与调试体验，贴近日常开发习惯",
    },
  },
  matrix: {
    eyebrow: "02 / 评测",
    title: "穿过测试矩阵，立刻知道结果。",
    body: "提交之后，代码穿过一组测试用例：依次执行、逐项反馈。耗时与内存一目了然，判定即刻返回。",
    status: {
      running: "运行中",
      passed: "通过",
    },
    verdict: "Accepted",
    demoNote: "以上为示意演示，非平台实时数据。",
  },
  growth: {
    eyebrow: "03 / 成长",
    title: "提交不再是孤立事件。",
    body: "每一次判定都会沉淀下来，成为可以回看、可以复盘的轨迹。进步不靠感觉，靠记录。",
    points: {
      history: "完整的解题与提交历史",
      lists: "用题单组织自己的练习路径",
      review: "复盘每次提交的结果与代价",
      trend: "长期观察能力曲线的变化",
      achievements: "解锁成就与阶段目标",
    },
  },
  network: {
    eyebrow: "04 / 网络",
    title: "从个人轨迹，到更大的星图。",
    body: "UltiCode 不只是单人刷题工具。在竞赛中校准排名，在社区里交换解法——你的轨迹会汇入更大的网络。",
    contest: {
      title: "竞赛",
      points: {
        schedule: "赛程与参与状态，一目了然",
        standing: "实时排行榜与排名变化",
        ranking: "历史成绩沉淀为长期参考",
      },
      cta: "浏览竞赛",
    },
    community: {
      title: "社区",
      points: {
        solutions: "发布与阅读题解",
        discussion: "围绕题目展开讨论",
        bookmarks: "收藏值得回看的思路",
      },
      cta: "进入社区",
    },
  },
  finale: {
    title: "下一次提交，从这里开始。",
    body: "写下代码，按下提交。剩下的路，让评测、记录与社区陪你走完。",
    ctaPrimary: "开始刷题",
    ctaSecondary: "创建账号",
    ctaSecondaryAuthed: "我的空间",
  },
  footer: {
    tagline: "可验证的进步",
  },
  chrome: {
    scrollHint: "滚动",
  },
} as const;
