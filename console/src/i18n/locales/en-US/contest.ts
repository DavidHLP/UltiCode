export default {
  // Contest list
  list: {
    title: "Contests",
    mainTitle: "UltiCode Contests",
    subtitle:
      "Join weekly challenges, solve problems in real-time, and improve your global ranking.",
    loading: "Loading contest data...",
    upcoming: "Upcoming",
    running: "Running",
    finished: "Finished",
    past: "Past Contests",
    pastSubtitle: "Join virtual contests to prepare for the ranking contest",
    partner: "Contest Partner",
    noContests: "No contests available",
    noContestsHint:
      "There are no contests running right now. Check back later!",
    noUpcomingHint: "No upcoming contests scheduled yet. Stay tuned!",
    noFinishedHint:
      "No finished contests yet. Participate in contests to see your history here.",
    viewAll: "View All",
    live: "Live Contests",
    liveSubtitle:
      "Compete while the clock is running and climb the live board.",
    liveBadge: "Live",
    liveNow: "Live Now",
    remaining: "Remaining:",
    rated: "Rated:",
    liveProgress: "Live Progress",
    time: "Time:",
    duration: "Duration:",
    startsIn: "Time until start:",
    addToCalendar: "Add to Calendar",
  },

  // My Contests
  myContests: {
    title: "My Contests",
    subtitle: "View your registered, participated, and virtual contests",
    loading: "Loading contests...",
    tabs: {
      registered: "Registered",
      participated: "Participated",
      virtual: "Virtual",
    },
    registeredTitle: "Registered Contests",
    noRegistered: "No registered contests",
    historyTitle: "Contest History",
    noParticipated: "No participated contests yet",
    rank: "Rank {rank} / {total}",
    score: "Score: {score}",
    virtualTitle: "Virtual Contests",
    noVirtual: "No virtual contests completed",
  },

  // Contest types
  types: {
    title: "Type",
    weekly: "Weekly Contest",
    biweekly: "Biweekly Contest",
    monthly: "Monthly Contest",
    special: "Special Contest",
    virtual: "Virtual Contest",
    ICPC: "ICPC",
    IOI: "IOI",
    CUSTOM: "Custom",
    icpc: "ICPC",
    ioi: "IOI",
    custom: "Custom",
    rated: "Rated",
    unrated: "Unrated",
  },

  // Contest detail
  detail: {
    details: "Contest Details",
    duration: "Duration",
    startTime: "Start Time",
    endTime: "End Time",
    problems: "Problems",
    ranking: "Ranking",
    myRanking: "My Ranking",
    participants: "Participants",
    register: "Register",
    unregister: "Unregister",
    enterContest: "Enter Contest",
    virtualParticipate: "Virtual Participate",
    registered: "Registered",
    notStarted: "Contest Not Started",
    inProgress: "Contest In Progress",
    ended: "Contest Ended",
    loading: "Loading contest...",
    unregistrationFailed: "Failed to unregister from contest",
    startVirtualFailed: "Failed to start virtual contest",
    backToList: "Back to Contest List",
    backToContest: "Back to Contest",
    problemNotInContest: {
      title: "This problem is not part of this contest",
      description: "Return to the contest to browse its problems.",
      action: "Return to Contest",
    },
    // ContestProblemDock labels — surfaced in the toolbar popover so
    // contest context stays available without adding a full-width bar.
    shell: {
      score: "Score",
      rank: "Rank",
      solved: "Solved",
      problems: "Problems",
      endsIn: "Ends in",
      startsIn: "Starts in",
      problemNav: "Problems",
    },
    // Sub-line under the title; explains why the Solutions tab
    // isn't visible during a live contest. P1-3 in the spec.
    solutionsHiddenHint:
      "Solutions and public code are hidden during the contest. They unlock after it ends.",
    // Contest-aware submit feedback (Chunk D). Each status maps
    // to a dedicated message so the user can see how this submit
    // changed their contest standing.
    submit: {
      judging: "Submitted — scoring…",
      accepted:
        "Accepted +{delta} pts · {total} pts · {solved}/{totalProblems} solved",
      wrongAnswer: "Wrong Answer — may add +{penalty}s penalty",
      compileError: "Compile Error — not scored",
    },
    // Announcement bell in the shell. "unread" is a degraded
    // v1 heuristic (last 24h); per-user lastReadAt is a
    // future schema change.
    announcements: {
      title: "Announcements",
      empty: "No announcements yet.",
      unread: "{n} unread",
      markRead: "Mark all as read",
      pinned: "Pinned",
    },
    registering: "Registering...",
    unregistering: "Unregistering...",
    starting: "Starting...",
    liveRanking: "Live Ranking",
    contestStatus: "Contest Status",
    endsAt: "Ends at",
    endedAt: "Ended at",
    status: "Status",
    youAreRegistered: "You are registered",
    registrationOpen: "Registration is open",
    submissionsLive: "Submissions are live",
    resultsPublished: "Results Published",
    replayHint: "Replay with virtual contest",
    rules: "Rules & Notes",
    challenges: "Contest Challenges",
    problemsLocked: "Problems Locked",
    problemsUnlockHint:
      "Challenges unlock when the contest begins. Register now so you're ready at the start time.",
    leaderboard: "Leaderboard",
    viewAll: "VIEW ALL",
    unrated: "Unrated",
    problemHeaders: {
      title: "Title",
      difficulty: "Difficulty",
      score: "Score",
      acceptance: "Acceptance",
      action: "Action",
    },
    // Per-row action labels for the contest problem list. Driven by
    // `getRowAction(contestStatus, problemStatus)` in
    // ContestProblemList.vue. Each key maps to a short imperative
    // verb; styling differentiates primary/secondary/disabled.
    row: {
      start: "Start",
      continue: "Continue",
      view: "View",
      locked: "Locked",
      review: "Review",
      // 全场 / overall field-wide copy. We deliberately separate
      // these from per-user status to avoid the previous "0 已解决"
      // ambiguity (the user couldn't tell if 0 meant "no one tried"
      // vs "system not loaded").
      solvedByAll: "{n} solved overall",
      totalSubmissions: "{n} submissions overall",
      noDataYet: "No submissions yet",
      attempted: "Attempted {n} times",
      notStarted: "Not started",
      solved: "Solved",
    },
    rankingHeaders: {
      rank: "Rank",
      user: "User",
      score: "Score",
      time: "Time",
      problems: "Problems",
      rating: "Rating",
      problemsSolved: "Problems Solved",
    },
    notFound: {
      title: "Contest Not Found",
      description:
        "The contest you are looking for might have been moved or removed.",
      return: "Return to Contests",
    },
    layoutCollapsedForRunning: "Rules hidden while the contest is running",
  },

  // Post-game review labels — surfaced in the contest toolbar popover.
  review: {
    title: "Review",
    firstACLabel: "First accepted",
    firstAC: "First accepted at {time}",
    finalScoreLabel: "Final score",
    finalScore: "Final score: {score}",
    breakdownLabel: "Verdicts",
    verdictBreakdown:
      "{n} submissions · {wa} wrong · {tle} TLE · {re} RE · {ce} CE",
    retake: "Retake as practice",
    addToNotebook: "Add to notebook",
    viewOnRanking: "View",
    empty: "No submissions for this problem yet.",
  },

  // My Contests page
  my: {
    title: "My Contests",
    subtitle: "View your registered, participated, and virtual contests",
  },

  // Rankings page
  rankings: {
    title: "Rankings",
    subtitle: "Global and local contest rankings",
    global: "Global",
    local: "Local",
  },

  // Ranking
  ranking: {
    title: "Ranking",
    national: "National Ranking",
    attended: "Attended {n} contests",
    rank: "Rank",
    user: "User",
    score: "Score",
    penalty: "Penalty",
    finishTime: "Finish Time",
    acceptedCount: "Accepted",
    totalTime: "Total Time",
    ratingChange: "Rating Change",
    globalRanking: "Global",
    localRanking: "Local Ranking",
    frozen: "Rankings are frozen during the final minutes",
    noRankings: "No rankings available",
    live: "Live",
    connecting: "Connecting...",
  },

  // First Solve
  firstSolve: {
    title: "First Solve!",
    solved: "solved the problem first!",
  },

  // Rating
  rating: {
    title: "Rating",
    current: "Current Rating",
    highest: "Highest Rating",
    change: "Change",
    newbie: "Newbie",
    pupil: "Pupil",
    specialist: "Specialist",
    expert: "Expert",
    candidateMaster: "Candidate Master",
    master: "Master",
    internationalMaster: "International Master",
    grandmaster: "Grandmaster",
    internationalGrandmaster: "International Grandmaster",
    legendaryGrandmaster: "Legendary Grandmaster",
  },

  // Virtual contest
  virtual: {
    title: "Virtual Contest",
    description:
      "Participate in past contests at any time as if it were a real contest",
    start: "Start Virtual Contest",
    started: "Virtual contest started",
    inProgress: "Virtual Contest In Progress",
    active: "Virtual Contest Active",
    contestId: "Contest ID:",
    finishEarly: "Finish Early",
    finishTitle: "Finish Virtual Contest?",
    finishDescription:
      "Are you sure you want to finish this virtual contest early? This action cannot be undone.",
    finishFailed: "Failed to finish virtual contest",
    timeRemaining: "Time Remaining",
    result: "Virtual Contest Result",
  },

  // Status
  status: {
    upcoming: "Upcoming",
    running: "Running",
    finished: "Finished",
    started: "Started",
    ended: "Ended",
    cancelled: "Cancelled",
    tbd: "Time TBD",
    calculating: "Calculating...",
    registrationOpen: "Registration Open",
    registrationClosed: "Registration Closed",
    draft: "Draft",
    published: "Published",
    freezing: "Freezing",
    archived: "Archived",
  },

  // Time
  time: {
    startsIn: "Starts in",
    endsIn: "Ends in",
    days: "d",
    hours: "h",
    minutes: "m",
    seconds: "s",
    min_short: "min",
    countdown_full: "{d}d {h}h {m}m {s}s",
    countdown_short: "{h}h {m}m {s}s",
  },

  // Messages
  messages: {
    registrationSuccess: "Registration successful",
    registrationFailed: "Registration failed",
    unregistrationSuccess: "Unregistered successfully",
    contestNotStarted: "Contest has not started yet",
    contestEnded: "Contest has ended",
    alreadyRegistered: "You are already registered for this contest",
    notRegistered: "You are not registered for this contest",
  },

  // R9.3 / F-40 / F-41 / F-43 / F-44 / F-47: empty/loading/error states
  // referenced from ContestBrowseView, ContestRankingsView, MyContests,
  // and the WS reconnecting banner.
  empty: {
    contests: "No contests available",
    rankings: "No rankings yet — be the first!",
    history: "No contest history yet",
    virtualHistory: "No virtual replays yet",
  },
  loading: {
    rankings: "Loading rankings...",
    history: "Loading contest history...",
  },
  error: {
    rankingsLoadFailed: "Failed to load rankings. Please refresh.",
    historyLoadFailed: "Failed to load contest history.",
    notRegisteredForVirtualReplay:
      "You must finish the original contest before replaying virtually",
    contestCancelledNoVirtual:
      "This contest was cancelled and cannot be replayed virtually",
    alreadyInVirtualContestOtherTab:
      "You already have an active virtual session in another tab",
  },
  connection: {
    reconnecting: "Network unstable, reconnecting...",
    reconnectFailed: "Reconnection failed. Please check your network.",
    rejected: "You are not registered for this contest",
  },
  replay: {
    historyTitle: "My virtual replays",
    emptyState: "You haven't replayed any contests yet",
    replayButton: "Replay virtually",
    durationHours: "{hours}h duration",
  },
} as const;
