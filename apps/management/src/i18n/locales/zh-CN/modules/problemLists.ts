export default {
  title: '题单管理',
  createTitle: '创建题单',
  editTitle: '编辑题单',
  addList: '添加列表',
  createList: '创建列表',
  editList: '编辑列表',
  searchPlaceholder: '搜索题单...',
  generalInfo: '基本信息',
  problems: '题目',
  errorLoading: '加载题单失败',
  backToLists: '返回列表',

  // ========== 列定义 ==========
  columns: {
    id: 'ID',
    title: '标题',
    name: '名称',
    author: '作者',
    status: '状态',
    problemCount: '题目数',
    problems: '题目数',
    isPublic: '公开',
    isFeatured: '精选',
    featured: '精选',
    visibility: '可见性',
    order: '排序',
    description: '描述',
    createdAt: '创建时间',
    updatedAt: '更新时间',
  },

  // ========== 状态 ==========
  status: {
    all: '全部状态',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
    visibility: '可见性',
    public: '公开',
    private: '私有',
    problems: '题目',
    featured: '精选',
    total: '总计',
    saving: '保存中...',
  },

  // ========== 可见性 ==========
  visibility: {
    public: '公开',
    private: '私有',
    unlisted: '未列出',
  },

  // ========== 操作 ==========
  actions: {
    view: '查看',
    edit: '编辑',
    delete: '删除',
    publish: '发布',
    feature: '精选',
  },

  // ========== 筛选器 ==========
  filters: {
    type: '类型',
    allTypes: '全部类型',
    featured: '精选',
    standard: '标准',
    visibility: '可见性',
    allVisibility: '全部可见性',
    public: '公开',
    private: '私有',
  },

  // ========== 表单 ==========
  form: {
    name: '名称',
    namePlaceholder: '输入列表名称',
    description: '描述',
    descriptionPlaceholder: '输入列表描述（可选）',
    isPublic: '公开',
    isPublicDescription: '公开列表对所有用户可见',
    isFeatured: '精选',
    isFeaturedDescription: '精选列表会在首页展示',
    isFeaturedTooltip: '开启后此列表将在首页精选区域展示',
    bannerTag: '标签',
    bannerTagPlaceholder: '例如: 推荐、热门',
    bannerTagDescription: '显示在横幅上的标签文字',
    bannerTheme: '主题',
    bannerThemePlaceholder: '选择主题颜色',
    sortOrder: '排序',
    sortOrderDescription: '数字越小排序越靠前',
    saving: '保存中...',
    saved: '已保存',
    saveError: '保存失败',
    saveChanges: '保存更改',
    creating: '创建中...',
    createList: '创建列表',
    validation: {
      nameRequired: '名称是必填项',
    },
  },

  // ========== 分区标题 ==========
  sections: {
    basicInfo: '基本信息',
    visibilityFeatured: '可见性与精选',
    bannerSettings: '横幅设置',
  },

  // ========== 主题 ==========
  themes: {
    blue: '蓝色',
    green: '绿色',
    purple: '紫色',
    orange: '橙色',
    red: '红色',
  },

  // ========== 题目管理器 ==========
  problemsManager: {
    manageProblems: '管理题目',
    problemsCount: '{count} 道题目',
    addProblem: '添加题目',
    saving: '保存中...',
    saveChanges: '保存更改',
    order: '排序',
    problem: '题目',
    difficulty: '难度',
    noProblems: '暂无题目，点击"添加题目"开始',
    removeProblem: '移除题目',
  },

  // ========== 删除对话框 ==========
  delete: {
    title: '删除列表',
    description: '确定要删除列表"{name}"吗？此操作不可撤销。',
    confirm: '确认删除',
    cancel: '取消',
    thisList: '此列表',
  },

  // ========== Toast 消息 ==========
  toast: {
    loadFailed: '加载题单失败',
    createSuccess: '题单创建成功',
    createFailed: '创建题单失败',
    updateSuccess: '题单更新成功',
    updateFailed: '更新题单失败',
    deleteSuccess: '题单删除成功',
    deleteFailed: '删除题单失败',
    problemsUpdated: '题目更新成功',
    problemsUpdateFailed: '更新题目失败',
    createdSuccess: '列表创建成功',
    updatedSuccess: '列表更新成功',
    deletedSuccess: '列表删除成功',
    requestCanceled: '请求已取消，请重试',
    networkError: '网络连接失败',
  },

  // ========== 终端风格 ==========
  terminal: {
    total: '总计',
    featured: '精选',
    public: '公开',
    loading: '加载中...',
  },

  // ========== 统计 ==========
  stats: {
    total: '总计',
    featured: '精选',
    public: '公开',
    listManagement: '题单管理',
  },

  // ========== 空状态 ==========
  empty: {
    title: '暂无列表',
    description: '点击上方按钮创建第一个列表',
  },
} as const
