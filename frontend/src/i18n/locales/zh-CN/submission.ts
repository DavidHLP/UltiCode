export default {
  // Status
  status: {
    pending: "等待中",
    judging: "判题中",
    accepted: "通过",
    wrongAnswer: "答案错误",
    timeLimitExceeded: "超时",
    memoryLimitExceeded: "内存超限",
    runtimeError: "运行错误",
    compileError: "编译错误",
    outputLimitExceeded: "输出超限",
    presentationError: "格式错误",
    systemError: "系统错误",
  },

  // Status descriptions
  statusDescriptions: {
    pending: "代码已提交，等待判题系统处理",
    judging: "正在运行测试用例",
    accepted: "所有测试用例通过",
    wrongAnswer: "输出结果与预期不符",
    timeLimitExceeded: "运行时间超过限制",
    memoryLimitExceeded: "内存使用超过限制",
    runtimeError: "程序运行时发生错误",
    compileError: "代码编译失败",
    outputLimitExceeded: "输出数据量超过限制",
    presentationError: "输出格式不正确",
    systemError: "判题系统出现问题，请重新提交",
  },

  // Status suggestions
  statusSuggestions: {
    accepted: "代码通过了！考虑优化算法来提升性能。",
    wrongAnswer: "检查边界情况，仔细阅读题目要求。",
    timeLimitExceeded: "尝试使用更高效的算法或数据结构。",
    memoryLimitExceeded: "减少内存使用，避免创建大型数据结构。",
    runtimeError: "检查数组越界、空指针或除零错误。",
    compileError: "检查语法错误和缺少的导入。",
  },

  // Details
  details: {
    testcasesPassed: "通过测试用例",
    runtime: "运行时间",
    memory: "内存消耗",
    language: "语言",
    submittedAt: "提交时间",
    testcase: "测试用例 {n}",
    input: "输入",
    expectedOutput: "预期输出",
    yourOutput: "你的输出",
    stderr: "错误信息",
    compilerOutput: "编译器输出",
  },

  // Actions
  actions: {
    viewDetails: "查看详情",
    resubmit: "重新提交",
    copyCode: "复制代码",
    shareCode: "分享代码",
  },

  // Run code
  run: {
    running: "运行中...",
    runCode: "运行代码",
    customTestcase: "自定义测试用例",
    addTestcase: "添加测试用例",
    removeTestcase: "移除测试用例",
    testcaseInput: "测试用例输入",
    testcaseOutput: "测试用例输出",
    runResult: "运行结果",
    expectedOutput: "预期输出",
    actualOutput: "实际输出",
  },

  // Messages
  messages: {
    submitting: "提交中...",
    submitted: "代码已提交",
    submitFailed: "提交失败",
    runSuccess: "运行成功",
    runFailed: "运行失败",
    codeCopied: "代码已复制",
  },
} as const;
