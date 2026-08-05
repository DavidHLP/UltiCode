export default {
  // 权限错误
  permission: {
    forbiddenPage: '您没有权限访问此页面',
    forbiddenRole: '您没有访问此页面所需的角色',
  },

  // 通用错误
  general: {
    unknown: '发生未知错误',
    network: '网络错误，请检查网络连接',
    timeout: '请求超时，请稍后重试',
    server: '服务器错误，请稍后重试',
    notFound: '请求的资源不存在',
    validation: '数据验证失败',
    unauthorized: '未授权访问',
    forbidden: '禁止访问',
  },

  // HTTP 状态码
  http: {
    400: '请求参数错误',
    401: '未授权，请登录',
    403: '禁止访问',
    404: '资源不存在',
    405: '请求方法不允许',
    408: '请求超时',
    409: '资源冲突',
    422: '数据验证失败',
    429: '请求过于频繁，请稍后重试',
    500: '服务器内部错误',
    502: '网关错误',
    503: '服务暂时不可用',
    504: '网关超时',
  },

  // 表单验证
  validation: {
    required: '此字段为必填项',
    email: '请输入有效的邮箱地址',
    url: '请输入有效的 URL',
    minLength: '最少需要 {min} 个字符',
    maxLength: '最多允许 {max} 个字符',
    min: '最小值为 {min}',
    max: '最大值为 {max}',
    pattern: '格式不正确',
    number: '请输入数字',
    integer: '请输入整数',
    positiveNumber: '请输入正数',
    password: {
      minLength: '密码至少需要 8 个字符',
      uppercase: '密码需要包含大写字母',
      lowercase: '密码需要包含小写字母',
      number: '密码需要包含数字',
      special: '密码需要包含特殊字符',
      match: '两次密码输入不一致',
    },
    username: {
      minLength: '用户名至少需要 3 个字符',
      maxLength: '用户名最多允许 20 个字符',
      pattern: '用户名只能包含字母、数字和下划线',
      taken: '用户名已被使用',
    },
    emailField: {
      invalid: '请输入有效的邮箱地址',
      taken: '邮箱已被注册',
    },
  },

  // 数据加载
  loading: {
    failed: '数据加载失败',
    retry: '重试',
    retrying: '重试中...',
    noMore: '没有更多数据',
    refreshing: '刷新中...',
  },

  // 文件上传
  upload: {
    failed: '文件上传失败',
    tooLarge: '文件大小超过限制',
    invalidType: '文件类型不支持',
    invalidExtension: '文件扩展名不支持',
    emptyFile: '文件不能为空',
    multipleFailed: '{count} 个文件上传失败',
  },

  // 业务错误
  business: {
    userNotFound: '用户不存在',
    problemNotFound: '题目不存在',
    contestNotFound: '比赛不存在',
    submissionNotFound: '提交不存在',
    duplicateEntry: '数据已存在',
    operationFailed: '操作失败',
    invalidOperation: '无效的操作',
    dependencyError: '存在依赖关系，无法执行此操作',
  },

  // 错误类型（用于 error.ts getErrorContext）
  errorType: {
    validation: {
      title: '请求参数错误',
      default: '请检查输入数据',
      suggestion: '请检查输入数据的格式和范围',
    },
    unauthorized: {
      title: '未授权',
      message: '请先登录后再进行此操作',
      suggestion: '请检查登录状态或重新登录',
    },
    forbidden: {
      title: '禁止访问',
      message: '您没有权限执行此操作',
      suggestion: '请联系管理员获取相应权限',
    },
    notFound: {
      title: '资源不存在',
      message: '请求的资源不存在',
      suggestion: '请检查请求的资源是否正确',
    },
    serverError: {
      title: '服务器错误',
      message: '服务器内部错误，请稍后重试',
      suggestion: '请稍后重试，如问题持续请联系管理员',
    },
    network: {
      title: '网络错误',
      suggestion: '请检查网络连接后重试',
    },
  },

  apiErrorCanceled: '请求已取消',
} as const
