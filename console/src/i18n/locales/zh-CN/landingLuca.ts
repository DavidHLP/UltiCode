export default {
  loading: "初始化",
  enter: "进入",
  skipLoader: "跳过引序",
  portalHint: "判定 · 竞赛 · 记忆",
  portalStatus: "正在初始化架构",
  brand: "ULTICODE",
  nav: {
    primaryNav: "主导航",
    mobileNav: "移动端导航",
    skipToContent: "跳到正文",
    home: "索引",
    problems: "题库",
    contests: "竞赛",
    community: "社区",
    talk: "进入",
    openMenu: "打开菜单",
    closeMenu: "关闭菜单",
  },
  // 九拍叙事——多面体按每拍中文文案字面化变态。中文 headline 两 locale
  // 都用中文(品牌文案 locale-stable,spec 要求 "translate nothing");
  // 英文 sub-line 为可选小字副标题。
  beats: {
    squashed: {
      eyebrow: "表层 · 01",
      title: "表层之上的阻力。",
      subline: "Friction above the surface",
    },
    cracked: {
      eyebrow: "内部 · 02",
      title: "界面之下,一颗无阻力的核心。",
      subline: "Beneath the interface, a frictionless core",
    },
    snapped: {
      eyebrow: "准则 · 03",
      title: "以精度,代替游说。",
      subline: "Precision over rhetoric",
    },
    axed: {
      eyebrow: "颗粒路径 · 04",
      title: "一条线,贯穿始终。",
      subline: "One line, through and through",
    },
    opened: {
      eyebrow: "开口 · 05",
      title: "入口所在。",
      subline: "Where the entrance is",
      cta: "开始你的第一行代码",
    },
    quarteted: {
      eyebrow: "解剖 · 06",
      title: "控制的解剖。",
      subline: "Anatomy of control",
      pillars: {
        editor: { label: "编码", desc: "解耦的书写层" },
        judge: { label: "判题", desc: "隔离的判定运行时" },
        contest: { label: "竞赛", desc: "时间框定的执行窗口" },
        community: { label: "社区", desc: "持久的记忆图" },
      },
    },
    timed: {
      eyebrow: "凭据 · 07",
      title: "记录,公开。",
      subline: "Records, public",
      logLabel: "规格日志",
      years: {
        "2021": "公开测试上线",
        "2022": "隔离判题运行时",
        "2023": "题解与讨论同帧",
        "2024": "限时竞赛窗口",
        "2025": "持久记忆图",
        "2026": "非对称新标准",
      },
    },
    still: {
      eyebrow: "静默 · 08",
      title: "一台安静的机器。",
      subline: "A quiet machine",
    },
    broken: {
      eyebrow: "地平 · 09",
      title: "新标准,是非对称的。",
      subline: "The new standard is asymmetric",
      ctaPrimary: "创造未来",
      ctaSecondary: "今昔并存",
    },
  },
  social: {
    label: "外部链接",
    github: "GitHub",
    docs: "项目文档",
    community: "开发者社区",
  },
  footer: {
    builtWith: "用 Vue 3 与 Spring Boot 构建",
    copyright: "© 2026 UltiCode 项目 · Apache License 2.0",
    rights: "ICP 备案申请中 · ICP filing pending",
  },
} as const;
