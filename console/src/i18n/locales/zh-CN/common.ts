export default {
  // Accessibility
  skipToContent: "跳转到主要内容",
  landingFrameTitle: "落地页",

  // Common actions
  actions: {
    save: "保存",
    cancel: "取消",
    delete: "删除",
    edit: "编辑",
    create: "创建",
    submit: "提交",
    confirm: "确认",
    search: "搜索",
    filter: "筛选",
    sort: "排序",
    refresh: "刷新",
    loadMore: "加载更多",
    copyLink: "复制链接",
    share: "分享",
    reply: "回复",
    close: "关闭",
    back: "返回",
    next: "下一步",
    previous: "上一步",
    reset: "重置",
    apply: "应用",
    clear: "清除",
    view: "查看",
    download: "下载",
    upload: "上传",
    add: "添加",
    remove: "移除",
    update: "更新",
    copy: "复制",
    paste: "粘贴",
    retry: "重试",
    toggleLanguage: "切换语言",
    selectAll: "全选",
  },

  // Months
  months: {
    jan: "一月",
    feb: "二月",
    mar: "三月",
    apr: "四月",
    may: "五月",
    jun: "六月",
    jul: "七月",
    aug: "八月",
    sep: "九月",
    oct: "十月",
    nov: "十一月",
    dec: "十二月",
  },

  // Common status
  status: {
    loading: "加载中...",
    success: "成功",
    error: "错误",
    pending: "等待中",
    empty: "暂无数据",
    notFound: "未找到",
    saving: "保存中...",
    saved: "已保存",
    processing: "处理中...",
    completed: "已完成",
    failed: "失败",
  },

  // Time related
  time: {
    now: "刚刚",
    today: "今天",
    yesterday: "昨天",
    earlier: "更早",
    minutesAgo: "{n} 分钟前",
    hoursAgo: "{n} 小时前",
    daysAgo: "{n} 天前",
    weeksAgo: "{n} 周前",
    monthsAgo: "{n} 个月前",
    yearsAgo: "{n} 年前",
  },

  // Common labels
  labels: {
    all: "全部",
    none: "无",
    yes: "是",
    no: "否",
    on: "开",
    off: "关",
    enabled: "启用",
    disabled: "禁用",
    required: "必填",
    optional: "可选",
    default: "默认",
    custom: "自定义",
    less: "少",
    more: "多",
    example: "示例",
    explanation: "解释",
    soon: "敬请期待",
    guest: "访客",
    admin: "管理员",
    new: "新",
  },

  // Pagination
  pagination: {
    page: "第 {current} 页，共 {total} 页",
    items: "共 {total} 条",
    itemsPerPage: "每页 {count} 条",
    goToPage: "跳转到",
    firstPage: "第一页",
    lastPage: "最后一页",
    previousPage: "上一页",
    nextPage: "下一页",
  },

  // Confirmation dialogs
  confirm: {
    title: "确认",
    deleteTitle: "确认删除",
    deleteMessage: "确定要删除吗？此操作无法撤销。",
    unsavedChanges: "有未保存的更改，确定要离开吗？",
  },

  // Messages
  messages: {
    operationSuccess: "操作成功",
    operationFailed: "操作失败",
    copiedToClipboard: "已复制到剪贴板",
    copyFailed: "复制失败",
    networkError: "网络连接失败，请稍后重试",
    serverError: "服务器错误，请稍后重试",
  },

  // Storage
  storage: {
    localStorageFailed: "语言偏好设置将在关闭浏览器后不会保存",
    sessionStorageFallback: "语言偏好设置仅在此会话期间保持",
    memoryStorageFallback: "语言偏好设置仅在当前页面有效",
    storageRecovered: "语言偏好设置现在可以正常保存",
  },

  // Network status
  network: {
    online: "已连接",
    offline: "您当前处于离线状态",
    offlineFor: "离线 {duration}",
    reconnect: "重新连接",
    backOnline: "网络已恢复",
    connectionLost: "连接已断开",
    connectionRestored: "连接已恢复",
  },

  // Error handling
  error: {
    title: "出了点问题",
    boundaryMessage: "渲染此组件时发生错误，请重试。",
    default: "发生意外错误",
    showDetails: "显示详情",
    hideDetails: "隐藏详情",
    retryMessage: "请重试，如果问题持续存在请联系支持。",
    notFound: "页面未找到",
    forbidden: "访问被拒绝",
    unauthorized: "请登录以继续",
    serverError: "服务器错误，请稍后重试。",
    networkError: "网络错误，请检查您的连接。",
    timeout: "请求超时，请重试。",
  },

  // Global search
  search: {
    placeholder: "搜索题目、用户、帖子...",
    noResults: '未找到 "{query}" 的相关结果',
    startTyping: "输入以开始搜索...",
    openSearchTip: "打开搜索",
    resultsCount: "{total} 个结果",
    navigateTip: "进行导航",
    selectTip: "进行选择",
    types: {
      problem: "题目",
      user: "用户",
      post: "帖子",
      solution: "题解",
      contest: "比赛",
    },
  },

  // PWA
  pwa: {
    updateAvailable: "有新版本可用",
    updateDescription: "新版本已准备就绪，点击更新。",
    update: "更新",
    offlineReady: "应用已可离线使用",
    installPrompt: "安装应用以获得更好体验",
    install: "安装",
    syncing: "同步中...",
    syncComplete: "成功同步 {count} 个提交",
    syncFailed: "同步提交失败",
    queuedSubmissions: "{count} 个提交待同步",
  },

  // Appearance
  appearance: {
    theme: "主题",
    light: "浅色",
    dark: "深色",
    system: "跟随系统",
  },

  // Dismiss button
  dismiss: "关闭",
} as const;
