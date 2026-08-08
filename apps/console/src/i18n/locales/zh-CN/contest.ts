export default {
  // Contest list
  list: {
    title: "比赛",
    mainTitle: "UltiCode 竞赛",
    subtitle: "参加每周挑战，实时解决问题，提升你的全球排名。",
    loading: "正在加载比赛数据...",
    upcoming: "即将开始",
    running: "进行中",
    finished: "已结束",
    past: "往期竞赛",
    pastSubtitle: "参加虚拟竞赛，为排名赛做准备",
    partner: "竞赛伙伴",
    noContests: "暂无比赛",
    noContestsHint: "当前没有正在进行的比赛，请稍后再来查看！",
    noUpcomingHint: "暂无即将开始的比赛，敬请期待！",
    noFinishedHint: "暂无已结束的比赛，参与比赛后可以在此查看您的历史记录。",
    viewAll: "查看全部",
    live: "正在进行的比赛",
    liveSubtitle: "在倒计时中竞争，攀登实时排行榜。",
    liveBadge: "直播",
    liveNow: "正在直播",
    remaining: "剩余：",
    rated: "计分：",
    liveProgress: "实时进度",
    time: "时间：",
    duration: "时长：",
    startsIn: "距开始：",
    addToCalendar: "添加到日历",
  },

  // My Contests
  myContests: {
    title: "我的比赛",
    subtitle: "查看您已报名、已参加和虚拟的比赛",
    loading: "加载比赛中...",
    tabs: {
      registered: "已报名",
      participated: "已参加",
      virtual: "虚拟",
    },
    registeredTitle: "已报名的比赛",
    noRegistered: "没有已报名的比赛",
    historyTitle: "比赛历史",
    noParticipated: "暂无参加过的比赛",
    rank: "排名 {rank} / {total}",
    score: "得分：{score}",
    virtualTitle: "虚拟比赛",
    noVirtual: "没有已完成的虚拟比赛",
  },

  // Contest types
  types: {
    title: "类型",
    weekly: "周赛",
    biweekly: "双周赛",
    monthly: "月赛",
    special: "特别赛",
    virtual: "虚拟比赛",
    ICPC: "ICPC",
    IOI: "IOI",
    CUSTOM: "自定义赛",
    icpc: "ICPC",
    ioi: "IOI",
    custom: "自定义赛",
    rated: "计分",
    unrated: "不计分",
  },

  // Contest detail
  detail: {
    details: "比赛详情",
    duration: "比赛时长",
    startTime: "开始时间",
    endTime: "结束时间",
    problems: "题目",
    ranking: "排名",
    myRanking: "我的排名",
    participants: "参赛人数",
    register: "报名参赛",
    unregister: "取消报名",
    enterContest: "进入比赛",
    virtualParticipate: "虚拟参赛",
    registered: "已报名",
    notStarted: "比赛未开始",
    inProgress: "比赛进行中",
    ended: "比赛已结束",
    loading: "正在加载比赛...",
    unregistrationFailed: "取消报名失败",
    startVirtualFailed: "开始虚拟比赛失败",
    backToList: "返回比赛列表",
    backToContest: "返回比赛",
    problemNotInContest: {
      title: "本题不属于本场比赛",
      description: "请返回比赛页面查看本题集。",
      action: "返回比赛",
    },
    // ContestProblemDock 工具栏弹层标签。比赛中用户在题目页
    // 可以收纳查看当前比赛、剩余时间、得分、排名。
    shell: {
      score: "得分",
      rank: "排名",
      solved: "已过",
      problems: "题目",
      endsIn: "剩余",
      startsIn: "距开始",
      problemNav: "题目导航",
    },
    // 标题下方一行小字,解释为什么比赛进行中看不到题解。
    solutionsHiddenHint: "题解与公开代码已在比赛期间隐藏，比赛结束后开放。",
    // 比赛化提交反馈 (Chunk D)。不同状态显示不同消息,让用户
    // 立刻看到这次提交对比赛结果的影响。
    submit: {
      judging: "已提交,正在判题…",
      accepted:
        "通过 +{delta} 分 · 当前 {total} 分 · 已过 {solved}/{totalProblems}",
      wrongAnswer: "未通过 — 可能产生 +{penalty} 秒罚时",
      compileError: "编译错误 — 不计入排名",
    },
    // 比赛公告 bell (顶栏右侧铃铛)。"未读"采用降级 v1 策略
    // (近 24h),未来 schema 变更可改为 per-user lastReadAt。
    announcements: {
      title: "公告",
      empty: "暂无公告。",
      unread: "{n} 条未读",
      markRead: "全部标记为已读",
      pinned: "置顶",
    },
    registering: "报名中...",
    unregistering: "取消报名中...",
    starting: "开始中...",
    liveRanking: "实时排名",
    contestStatus: "比赛状态",
    endsAt: "结束于",
    endedAt: "结束于",
    status: "状态",
    youAreRegistered: "您已报名",
    registrationOpen: "报名开放中",
    submissionsLive: "提交已开放",
    resultsPublished: "结果已公布",
    replayHint: "通过虚拟比赛重温",
    rules: "规则与说明",
    challenges: "比赛题目",
    problemsLocked: "题目已锁定",
    problemsUnlockHint: "比赛开始后题目将解锁。立即报名，准备好迎接挑战。",
    leaderboard: "排行榜",
    viewAll: "查看全部",
    unrated: "不计分",
    problemHeaders: {
      title: "题目",
      difficulty: "难度",
      score: "分数",
      acceptance: "通过率",
      action: "操作",
    },
    // 题目列表每行的主操作按钮。`getRowAction()` 根据比赛状态
    // 和个人提交状态返回对应的 key。视觉上区分主/次/禁用。
    row: {
      start: "开始",
      continue: "继续",
      view: "查看",
      locked: "未解锁",
      review: "复盘",
      // 全场 (field-wide) 数据,区别于个人状态。之前的 "0 已解决"
      // / "0 提交记录" 容易让用户混淆"没人提交"和"系统没数据",
      // 这里显式写"全场"消除歧义。
      solvedByAll: "全场 {n} 人通过",
      totalSubmissions: "全场 {n} 次提交",
      noDataYet: "暂无全场提交",
      attempted: "已尝试 {n} 次",
      notStarted: "未开始",
      solved: "已通过",
    },
    rankingHeaders: {
      rank: "排名",
      user: "用户",
      score: "得分",
      time: "用时",
      problems: "题目",
      rating: "Rating",
      problemsSolved: "通过数",
    },
    notFound: {
      title: "未找到比赛",
      description: "您正在寻找的比赛可能已被移动或删除。",
      return: "返回比赛列表",
    },
    layoutCollapsedForRunning: "比赛进行中规则已折叠",
  },

  // 赛后复盘标签 — 已收纳进竞赛工具栏弹层。
  review: {
    title: "赛后复盘",
    firstACLabel: "首次通过",
    firstAC: "首次通过 {time}",
    finalScoreLabel: "最终得分",
    finalScore: "最终得分 {score}",
    breakdownLabel: "判题分布",
    verdictBreakdown: "共 {n} 次提交 · 错 {wa} · TLE {tle} · RE {re} · CE {ce}",
    retake: "重新练习",
    addToNotebook: "加入错题本",
    viewOnRanking: "查看",
    empty: "暂无本题提交记录。",
  },

  // My Contests page
  my: {
    title: "我的比赛",
    subtitle: "查看您已报名、已参加和虚拟的比赛",
  },

  // Rankings page
  rankings: {
    title: "排行榜",
    subtitle: "全球和本地竞赛排名",
    global: "全球",
    local: "本地",
  },

  // Ranking
  ranking: {
    title: "排行榜",
    national: "全国排名",
    attended: "参加了 {n} 场比赛",
    rank: "排名",
    user: "用户",
    score: "得分",
    penalty: "罚时",
    finishTime: "完成时间",
    acceptedCount: "通过题数",
    totalTime: "总用时",
    ratingChange: "Rating 变化",
    globalRanking: "全球",
    localRanking: "本地排名",
    frozen: "排行榜在最后几分钟已冻结",
    noRankings: "暂无排名数据",
    live: "实时",
    connecting: "连接中...",
  },

  // First Solve
  firstSolve: {
    title: "首位解答！",
    solved: "率先解决了这道题！",
  },

  // Rating
  rating: {
    title: "Rating",
    current: "当前 Rating",
    highest: "最高 Rating",
    change: "变化",
    newbie: "新手",
    pupil: "初级",
    specialist: "专家",
    expert: "高手",
    candidateMaster: "准大师",
    master: "大师",
    internationalMaster: "国际大师",
    grandmaster: "特级大师",
    internationalGrandmaster: "国际特级大师",
    legendaryGrandmaster: "传奇大师",
  },

  // Virtual contest
  virtual: {
    title: "虚拟比赛",
    description: "在任意时间以真实比赛的形式参加历史比赛",
    start: "开始虚拟比赛",
    started: "虚拟比赛已开始",
    inProgress: "虚拟比赛进行中",
    active: "虚拟比赛进行中",
    contestId: "比赛ID：",
    finishEarly: "提前结束",
    finishTitle: "结束虚拟比赛？",
    finishDescription: "您确定要提前结束这场虚拟比赛吗？此操作无法撤销。",
    finishFailed: "结束虚拟比赛失败",
    timeRemaining: "剩余时间",
    result: "虚拟比赛结果",
  },

  // Status
  status: {
    upcoming: "即将开始",
    running: "进行中",
    finished: "已结束",
    started: "已开始",
    ended: "已结束",
    cancelled: "已取消",
    tbd: "时间待定",
    calculating: "计算中...",
    registrationOpen: "报名中",
    registrationClosed: "报名已截止",
    draft: "草稿",
    published: "已发布",
    freezing: "封榜中",
    archived: "已归档",
  },

  // Time
  time: {
    startsIn: "距开始还有",
    endsIn: "距结束还有",
    days: "天",
    hours: "小时",
    minutes: "分钟",
    seconds: "秒",
    min_short: "分钟",
    countdown_full: "{d}天{h}小时{m}分{s}秒",
    countdown_short: "{h}小时{m}分{s}秒",
  },

  // Messages
  messages: {
    registrationSuccess: "报名成功",
    registrationFailed: "报名失败",
    unregistrationSuccess: "已取消报名",
    contestNotStarted: "比赛尚未开始",
    contestEnded: "比赛已结束",
    alreadyRegistered: "您已报名此比赛",
    notRegistered: "您尚未报名此比赛",
  },

  // R9.3 / F-40 / F-41 / F-43 / F-44 / F-47: empty/loading/error states
  // referenced from ContestBrowseView, ContestRankingsView, MyContests,
  // and the WS reconnecting banner.
  empty: {
    contests: "暂无比赛",
    rankings: "暂无排名 — 争当首位!",
    history: "暂无参赛记录",
    virtualHistory: "暂无虚拟赛重放记录",
  },
  loading: {
    rankings: "正在加载排行榜...",
    history: "正在加载参赛记录...",
  },
  error: {
    rankingsLoadFailed: "排行榜加载失败，请刷新重试",
    historyLoadFailed: "参赛记录加载失败",
    notRegisteredForVirtualReplay: "您必须先完成原比赛才能开启虚拟重放",
    contestCancelledNoVirtual: "该比赛已取消，无法开启虚拟重放",
    alreadyInVirtualContestOtherTab: "您已在另一个标签页开启虚拟赛",
  },
  connection: {
    reconnecting: "网络不稳定，正在重连...",
    reconnectFailed: "重连失败，请检查网络",
    rejected: "您未报名此比赛",
  },
  replay: {
    historyTitle: "我的虚拟重放",
    emptyState: "您尚未重放任何比赛",
    replayButton: "虚拟重放",
    durationHours: "{hours}小时 时长",
  },
} as const;
