export default {
  // HTTP errors
  http: {
    400: "请求参数错误",
    401: "请先登录",
    403: "没有权限执行此操作",
    404: "请求的资源不存在",
    409: "资源冲突",
    500: "服务器内部错误",
    networkError: "网络连接失败",
    timeout: "请求超时",
  },

  // Auth module errors (1xxxx)
  auth: {
    AUTH_10001: "用户名或密码错误",
    AUTH_10002: "该账户未设置密码，请使用其他登录方式",
    AUTH_10003: "用户名已被使用",
    AUTH_10004: "邮箱已被使用",
    AUTH_10005: "账户不存在",
    AUTH_10006: "登录已过期，请重新登录",
  },

  // User module errors (2xxxx)
  user: {
    USER_20001: "用户不存在",
    USER_20002: "没有权限修改他人资料",
  },

  // Problem module errors (3xxxx)
  problem: {
    PROBLEM_30001: "题目不存在",
    PROBLEM_30002: "题目已被锁定",
    PROBLEM_30003: "需要会员权限",
  },

  // Submission module errors (4xxxx)
  submission: {
    SUBMISSION_40001: "提交记录不存在",
    SUBMISSION_40002: "需要用户ID",
    SUBMISSION_40003: "提交过于频繁，请稍后重试",
    SUBMISSION_40004: "代码不能为空",
    SUBMISSION_40005: "不支持的编程语言",
  },

  // Solution module errors (5xxxx)
  solution: {
    SOLUTION_50001: "题解不存在",
    SOLUTION_50002: "没有权限删除他人题解",
    SOLUTION_50003: "没有权限修改他人题解",
    SOLUTION_50004: "评论不存在",
    SOLUTION_50007: "需要先通过此题才能发布题解",
    SOLUTION_50008: "您已为此题发布过题解",
  },

  // Forum module errors (6xxxx)
  forum: {
    FORUM_60001: "帖子不存在",
    FORUM_60002: "社区不存在",
    FORUM_60003: "社区已被限制",
    FORUM_60004: "没有权限编辑此帖子",
    FORUM_60005: "没有权限删除此帖子",
    FORUM_60006: "评论不存在",
    FORUM_60007: "帖子已被锁定",
  },

  // Contest module errors (7xxxx)
  contest: {
    CONTEST_70001: "比赛不存在",
    CONTEST_70002: "只能报名即将开始的比赛",
    CONTEST_70003: "您已报名此比赛",
    CONTEST_70004: "您尚未报名此比赛",
    CONTEST_70005: "报名已截止",
    CONTEST_70006: "比赛人数已满",
    CONTEST_70007: "没有权限参加此比赛",
    CONTEST_70008: "比赛尚未开始",
    CONTEST_70009: "比赛已结束",
    CONTEST_70010: "比赛尚未开始",
  },

  // Bookmark module errors (8xxxx)
  bookmark: {
    BOOKMARK_80001: "收藏夹不存在",
    BOOKMARK_80002: "无法删除默认收藏夹",
    BOOKMARK_80003: "收藏夹名称已存在",
  },

  // Problem list module errors (9xxxx)
  problemList: {
    PROBLEM_LIST_90001: "题单不存在",
    PROBLEM_LIST_90002: "没有权限编辑此题单",
    PROBLEM_LIST_90003: "这是私密题单，无法访问",
  },

  // Default error message
  default: "操作失败，请稍后重试",
  unknown: "发生未知错误",
} as const;
