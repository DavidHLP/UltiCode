export default {
  brand: "ULTICODE",
  version: "v1.0.0-beta",
  signIn: "Sign in",
  console: "Enter platform",
  freeStart: "Start for free",
  primaryNavigation: "Primary navigation",
  mobileNavigation: "Mobile navigation",
  openMenu: "Open navigation menu",
  closeMenu: "Close navigation menu",
  heroEyebrow: "Write the idea. Let the code prove it.",
  titlePart1: "Train logic.",
  titlePart2: "Test it.",
  titlePart3: "Know it works.",
  subtitle:
    "Start with one problem, sharpen your approach through immediate judging, then turn every submission into lasting skill through contests and editorials.",
  tryProblem: "Try a problem",
  noCreditCard: "No credit card · Sign up to save submissions",
  workbenchTitle: "practice/two-sum",
  ready: "Ready",
  sampleProblem: "Sample problem · Array / Hash table",
  sampleProblemTitle: "Two Sum",
  languageSelect: "Choose programming language",
  runCode: "Run",
  running: "Judging",
  outputHint: "> Choose a language, then run this solution.",
  outputCompile: "> Compiling in an isolated runner…",
  outputCaseOne: "✓ Sample 1 passed: [2, 7, 11, 15] → [0, 1]",
  outputCaseTwo: "✓ Sample 2 passed: [3, 2, 4] → [1, 2]",
  outputComplete: "> 2 / 2 samples passed",
  compileSuccess: "Solution passed. Continue with the full problem.",
  workflowEyebrow: "One complete practice loop",
  workflowTitle: "From reading the prompt to explaining why it works",
  workflowSubtitle:
    "UltiCode keeps practice, contests, and discussion in one learning path, so you can focus on solving instead of switching tools.",
  practiceTitle: "Break down the problem",
  practiceDesc:
    "Filter by topic, submit in a familiar language, and use clear feedback to find missed edge cases.",
  competeTitle: "Test your pace",
  competeDesc:
    "Join timed contests and measure your solving rhythm and implementation accuracy under the same rules.",
  reviewTitle: "Explain the approach",
  reviewDesc:
    "Read editorials, discuss complexity, and turn one accepted solution into a method you can reuse.",
  social: {
    eyebrow: "Available now, not aspirational",
    title: "One practice path, three open doors",
    practice: { label: "PRACTICE", desc: "Public problems and submissions" },
    contest: { label: "CONTEST", desc: "Timed events and rankings" },
    community: { label: "DISCUSS", desc: "Solutions, comments, review" },
    verify: "Open entry",
  },
  feature: {
    eyebrow: "From source to verdict",
    title: "Every step serves a trustworthy result",
    pipeline: {
      label: "Source to judging verdict",
      source: "Source",
      compile: "Compile",
      run: "Run",
      accepted: "Accepted",
    },
    editor: {
      command: "editor.write()",
      title: "Online editor",
      desc: "Write and refine a solution beside the problem without switching tools.",
    },
    judge: {
      command: "judge.run()",
      title: "Isolated judging",
      desc: "Compile, run, and return a clear verdict that identifies the failing stage.",
    },
    contest: {
      command: "contest.rank()",
      title: "Timed contests",
      desc: "Submit under the same rules and time window, then inspect the ranking.",
    },
    lists: {
      command: "problem.save()",
      title: "Lists and bookmarks",
      desc: "Organize problems by goal and turn scattered practice into a plan.",
    },
    solutions: {
      command: "solution.publish()",
      title: "Markdown solutions",
      desc: "Capture reasoning, complexity, and edge cases so one pass stays useful.",
    },
    community: {
      command: "thread.reply()",
      title: "Live discussion",
      desc: "Keep asking around a problem and its tradeoffs instead of copying an answer.",
    },
  },
  usecase: {
    eyebrow: "Different goals, one feedback loop",
    title: "Why are you opening UltiCode today?",
    subtitle:
      "Choose the problem you are solving; the page shows only the workflow that matters.",
    tabsLabel: "Use cases",
    learner: {
      label: "Daily practice",
      signal: "GOAL / BUILD CONSISTENCY",
      title: "Connect each session into a visible practice trail",
      desc: "Enter from a topic list, review failures immediately, and save solutions worth revisiting.",
      point1: "Choose the next problem by topic",
      point2: "Keep submissions and solutions",
    },
    school: {
      label: "Coursework",
      signal: "GOAL / TEACH WITH EVIDENCE",
      title: "Make assignment outcomes inspectable",
      desc: "Use shared problems and judging rules so feedback stays about code rather than environment differences.",
      point1: "One submission path",
      point2: "Explicit judging states",
    },
    contest: {
      label: "Contest training",
      signal: "GOAL / PERFORM UNDER TIME",
      title: "Test consistency under a clock and ranking",
      desc: "Timed events expose real bottlenecks in reading, implementation, and debugging pace.",
      point1: "Timed contest flow",
      point2: "Ranking feedback",
    },
    interview: {
      label: "Interview prep",
      signal: "GOAL / EXPLAIN THE TRADEOFF",
      title: "Do more than pass—explain why",
      desc: "After a classic problem, record complexity and alternatives so code becomes an explainable method.",
      point1: "Practice classic topics",
      point2: "Review reasoning in Markdown",
    },
  },
  timeline: {
    eyebrow: "Capability log",
    title: "Only what you can use today",
    subtitle:
      "No fictional quarterly roadmap. Every capability below is reachable from the current product navigation.",
    available: "Available",
    open: "Open now",
    judge: {
      title: "Problems and isolated judging",
      desc: "Browse public problems, write a solution, and inspect submission results.",
    },
    contest: {
      title: "Contests and rankings",
      desc: "Open the contest list, solve under time, and view rankings.",
    },
    community: {
      title: "Solutions and developer community",
      desc: "Publish Markdown solutions and complete the reasoning through discussion.",
    },
  },
  faq: {
    eyebrow: "Before you start",
    title: "Common questions, direct answers",
    free: {
      question: "Does UltiCode cost anything to use?",
      answer:
        "Registration and the current public problem entry are free; the page does not ask for a credit card.",
    },
    judge: {
      question: "Is the hero run demo a real online judge?",
      answer:
        "No. The hero workbench is an explicit interaction demo; the platform judging flow begins after you submit on a real problem.",
    },
    privacy: {
      question: "How are sign-in credentials stored?",
      answer:
        "Access and refresh tokens stay in HttpOnly cookies and are not exposed to page scripts.",
    },
    school: {
      question: "Can I use it to organize coursework?",
      answer:
        "The existing problem, submission, and contest flows support shared practice. This page does not claim school administration features that are not shipped.",
    },
    languages: {
      question: "Which programming languages are supported?",
      answer:
        "Language support follows the options on each problem editor. The hero demo includes C++, Python, and JavaScript.",
    },
    api: {
      question: "Is there a public API or offline mode?",
      answer:
        "This landing page does not promise a public API or offline mode; current product navigation is the source of available entry points.",
    },
  },
  ctaTitle: "Your next accepted solution starts here",
  ctaDesc:
    "Create a free account to save submissions, enter contests, and review algorithms with other solvers.",
  ctaBrowse: "Browse all problems",
  ctaContests: "View contests",
  footer: {
    statement:
      "Turn every submission into an algorithmic method you can reuse.",
    practice: "Practice",
    problemset: "Public problems",
    compete: "Compete",
    contests: "Contest list",
    connect: "Connect",
    community: "Developer community",
  },
  copyright: "© 2026 UltiCode Project · Apache License 2.0",
};
