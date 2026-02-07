export default {
  // Status
  status: {
    pending: "Pending",
    judging: "Judging",
    accepted: "Accepted",
    wrongAnswer: "Wrong Answer",
    timeLimitExceeded: "Time Limit Exceeded",
    memoryLimitExceeded: "Memory Limit Exceeded",
    runtimeError: "Runtime Error",
    compileError: "Compile Error",
    outputLimitExceeded: "Output Limit Exceeded",
    presentationError: "Presentation Error",
    systemError: "System Error",
  },

  // Status descriptions
  statusDescriptions: {
    pending: "Your code has been submitted and is waiting to be processed",
    judging: "Running test cases",
    accepted: "All test cases passed",
    wrongAnswer: "Output doesn't match expected result",
    timeLimitExceeded: "Execution time exceeded the limit",
    memoryLimitExceeded: "Memory usage exceeded the limit",
    runtimeError: "An error occurred during execution",
    compileError: "Code failed to compile",
    outputLimitExceeded: "Output size exceeded the limit",
    presentationError: "Output format is incorrect",
    systemError: "A system error occurred. Please try again.",
  },

  // Status suggestions
  statusSuggestions: {
    accepted:
      "Well done! Consider optimizing your algorithm for better performance.",
    wrongAnswer:
      "Check edge cases and carefully read the problem requirements.",
    timeLimitExceeded:
      "Try using a more efficient algorithm or data structure.",
    memoryLimitExceeded:
      "Reduce memory usage and avoid creating large data structures.",
    runtimeError:
      "Check for array out of bounds, null pointers, or division by zero.",
    compileError: "Check for syntax errors and missing imports.",
  },

  // Details
  details: {
    testcasesPassed: "Testcases Passed",
    runtime: "Runtime",
    memory: "Memory",
    language: "Language",
    submittedAt: "Submitted At",
    testcase: "Testcase {n}",
    input: "Input",
    expectedOutput: "Expected Output",
    yourOutput: "Your Output",
    stderr: "Error Output",
    compilerOutput: "Compiler Output",
  },

  // Actions
  actions: {
    viewDetails: "View Details",
    resubmit: "Resubmit",
    copyCode: "Copy Code",
    shareCode: "Share Code",
  },

  // Run code
  run: {
    running: "Running...",
    runCode: "Run Code",
    customTestcase: "Custom Testcase",
    addTestcase: "Add Testcase",
    removeTestcase: "Remove Testcase",
    testcaseInput: "Testcase Input",
    testcaseOutput: "Testcase Output",
    runResult: "Run Result",
    expectedOutput: "Expected Output",
    actualOutput: "Actual Output",
  },

  // Messages
  messages: {
    submitting: "Submitting...",
    submitted: "Code submitted",
    submitFailed: "Submission failed",
    runSuccess: "Run successful",
    runFailed: "Run failed",
    codeCopied: "Code copied",
  },
} as const;
