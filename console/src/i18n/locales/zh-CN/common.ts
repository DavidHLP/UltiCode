export default {
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

  // Validation
  validation: {
    username: {
      required: "用户名不能为空",
      minLength: "用户名至少需要 3 个字符",
      maxLength: "用户名最多 20 个字符",
      format: "用户名只能包含字母、数字和下划线",
    },
    email: {
      required: "邮箱不能为空",
      invalid: "邮箱格式不正确",
    },
    password: {
      required: "密码不能为空",
      minLength: "密码至少需要 8 个字符",
      uppercase: "密码必须包含至少一个大写字母",
      lowercase: "密码必须包含至少一个小写字母",
      number: "密码必须包含至少一个数字",
    },
    confirmPassword: {
      required: "请确认密码",
      mismatch: "两次输入的密码不一致",
    },
  },
} as const;
