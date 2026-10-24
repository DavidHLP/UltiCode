export default {
  title: "Agent submission review",
  description:
    "Explain this verdict and suggest a practical next step; saving a learning plan is optional.",
  reviewTitle: "Review this submission",
  reviewDescription:
    "See the conclusion and next step first; save a learning plan only if useful.",
  reviewError: "Analyze this error",
  reviewComplexity: "Review complexity",
  closePanel: "Close review",
  openWorkspace: "Open Agent workspace",
  sourceConsent:
    "I agree that my submitted source code may be sent to an external model service together with the authorized reference materials for this analysis.",
  sourceModes: {
    metadata:
      "The review uses submission metadata and authorized reference materials; your submitted source code is not sent.",
    owner_source: "This review may include your submitted source code.",
  },
  analysisUnavailable: "Agent analysis is currently unavailable.",
  analysisFailed:
    "This analysis did not complete. Reload the thread to check its status, or start a new review and try again.",
  planFailure:
    "The optional learning plan could not be saved or read back. Check its status before trying again.",
  analysisWaiting:
    "Analyzing this submission. New content will appear here as it arrives.",
  capabilityLoading: "Checking review availability…",
  retry: "Retry",
  persistenceUnavailable:
    "Saving learning plans is disabled. Confirmation, save, and recovery are unavailable.",
  optionalPlan: "Save as a learning plan (optional)",
  evidence: "Evidence and analysis details",
  savedPlanReadback: "Saved learning plan",
  planReadbackUnavailable:
    "The saved plan is recorded, but its details could not be loaded. The save receipt remains authoritative.",
  facts: "Verified facts",
  noFacts: "No facts recorded.",
  hypotheses: "Hypotheses to check",
  noHypotheses: "No hypotheses recorded.",
  preview: "Draft preview",
  defaultQuestion: "What should I learn from this submission?",
  promptMistakes: "Explain likely mistakes",
  promptComplexity: "Review time and space complexity",
  sourceSubmissionId: "Submission UUID",
  sourceSubmissionIdHint:
    "Enter the full UUID of your own submission, not a problem number or title.",
  question: "How should Agent help with this result?",
  questionPlaceholder: "Describe what you want to understand or do next…",
  start: "Start analysis",
  newThread: "New review",
  newThreadConfirm:
    "This review has an unsaved answer or draft. Starting a new review will leave this thread, but will not delete it from the server. Continue?",
  refresh: "Reload review",
  reloadConfirm:
    "Reloading will discard unsaved edits in this panel; the server thread will remain unchanged. Continue?",
  thread: "Thread",
  status: "Current status",
  draft: "Optional learning plan",
  draftTitle: "Title",
  draftContent: "Content",
  updateDraft: "Update draft",
  analyze: "Start analysis",
  confirm: "Confirm this draft",
  save: "Save to UltiCode",
  cancel: "Cancel thread",
  recover: "Check save result",
  generated: "Conclusion and next step",
  citations: "Citations",
  citationChecks: "Citation checks",
  source: "Source location",
  quote: "Source excerpt",
  toolEvents: "Tool activity",
  toolStarted: "Started",
  toolCompleted: "Completed",
  waitingConfirmation:
    "Confirmation records your approval; only the separate save step writes to UltiCode.",
  savingWarning:
    "Saving is in progress. Cancelling cannot undo a write already sent to UltiCode.",
  savedPlan: "Saved plan ID: {id}",
  noCitations: "No citations to display.",
  statuses: {
    idle: "Waiting for input",
    loading: "Restoring thread",
    creating: "Creating thread",
    awaiting_confirmation: "Awaiting confirmation",
    analyzing: "Generating and checking",
    confirmed: "Confirmed; save is a separate step",
    saving: "Saving",
    saved: "Completed",
    unknown: "Save result unknown",
    failed: "Failed",
    cancelled: "Cancelled",
  },
  context: {
    problem: "Problem",
    invalid:
      "A selected reference is unavailable, mismatched, or too large. Choose context again and send a new message.",
    retryOriginal:
      "Retry uses this turn's saved references. To change them, choose new context and send a new message.",
    forumComments: "Forum comments",
    forumScope:
      "Choose accessible forum comments or replies as additional references, independent of the selected problem.",
    filterPosts: "Filter posts on this page",
    noPosts: "No matching posts on this page.",
    choose: "Choose context",
    title: "Analysis context",
    description:
      "Choose a problem, one of your practice submissions, and optional comments. Nothing is sent until you send a message.",
    problemSubmission: "Problem & submission",
    comments: "Comments",
    solutionComments: "Solution comments",
    search: "Search problems by title",
    searchAction: "Search",
    previous: "Previous",
    next: "Next",
    selectedSubmission: "Selected submission",
    includeProblem: "Include the problem statement",
    noSubmissions: "No submissions for this problem on this page.",
    commentScope:
      "Choose a solution to see its comments. Select up to 3 comments, each at most 2,000 characters.",
    noSolutions: "No solutions in the current feed.",
    noComments: "No comments on this solution.",
    clearComments: "Clear selected comments",
    disclosure:
      "Selected problem text (up to 8,000 characters) and comments will be sent to the external model as untrusted reference material. Submitted source code still requires separate consent.",
    newConversation:
      "Changing the submission starts a new analysis. The existing conversation remains saved.",
    apply: "Use this context",
    clear: "Remove references",
    problemReference: "Problem statement",
    loadFailed: "Could not load this context. Try the lookup again.",
    ineligible:
      "Select your own ordinary-practice submission with a final verdict.",
  },
  workspace: {
    modelResponseInvalid:
      "The model returned an invalid response, so this analysis could not be completed. Your conversation is saved. Retrying starts another model request.",
    toggleSidebar: "Toggle conversation sidebar",
    navigation: "Workspace navigation",
    backToProblems: "Back to problems",
    keepConversation: "Keep conversation",
    searchHistory: "Search loaded conversations",
    clearSearch: "Clear conversation search",
    noSearchResults: "No matching conversations in the loaded history.",
    latest: "Back to latest",
    keyboardHint: "Enter to send · Shift + Enter for a new line",
    title: "Agent workspace",
    new: "New analysis",
    history: "Recent conversations",
    noHistory: "Your conversations will appear here.",
    turns: "turns",
    loading: "Loading…",
    loadMore: "Load more",
    loadOlder: "Load earlier turns",
    retention:
      "Conversations stay saved until you delete them. Deleting a conversation does not delete saved learning plans.",
    emptyTitle: "Choose a submission to analyze",
    readyTitle: "Start a conversation about this submission",
    readyDescription:
      "Ask a question to create a saved conversation. You can follow up later from this workspace.",
    emptyDescription:
      "Start from one of your ordinary-practice submissions. Each conversation stays bound to that submission, so you can ask follow-up questions without mixing contexts.",
    findSubmission: "Open my submissions",
    recentSubmissions: "Recent submissions",
    noRecentSubmissions:
      "No eligible practice submissions among your latest three. Open all submissions to choose an earlier result.",
    submissionsError:
      "Recent submissions could not be loaded. Try again or open all submissions.",
    problem: "Problem {id}",
    objectHint: "Analysis target",
    submission: "Your submission",
    delete: "Delete",
    deleting: "Deleting…",
    reload: "Reload",
    deleteConfirm:
      "Delete this conversation and its turns? Separately saved learning plans will remain.",
    eyebrow: "Submission analysis",
    generating: "Analyzing this turn…",
    queued: "This turn is waiting to run.",
    turnFailed:
      "This turn did not complete. Your earlier conversation is still saved.",
    conversationActions: "Conversation actions",
    resizeSidebar: "Resize conversation sidebar",
    analysisIncomplete: "Analysis not completed",
    earlierAnalysisIncomplete: "Earlier analysis not completed",
    sourceResponseBlocked:
      "This response was blocked by the source protection check. Ask for an explanation with line references instead of complete code lines. Your conversation is saved.",
    evidenceNotVerified:
      "This response could not be verified against the authorized evidence. Your conversation is saved. Retrying starts another model request.",
    cancelled:
      "This turn was cancelled. Earlier turns remain saved. A request already sent to the model may still finish and incur usage; no further analysis steps will run.",
    activity: "Analysis activity",
    copyAnswer: "Copy answer",
    copied: "Copied",
    copyFailed: "Could not copy. Select the answer and copy it manually.",
    reuseQuestion: "Use question again",
    keepDraft: "Clear your current draft before reusing a question.",
    referencePreviews: "Reference previews",
    previewCitation: "Preview reference {index}",
    verifiedReference:
      "Verified reference metadata. Source excerpts are not stored in the conversation.",
    followupSuggestions: "Follow-up suggestions",
    followupExplain: "Explain this analysis step by step",
    followupEdges: "Which boundary cases should I review?",
    followupPractice: "Suggest a practice exercise based on this analysis",
    sourceAuthorization: "Source access for the next message",
    sourceApproved: "Authorized",
    sourcePending: "Not authorized",
    contextOverview: "Review outgoing context",
    outgoingMaterials: "Materials for the next message",
    included: "Included",
    notIncluded: "Not included",
    materialsNotice:
      "Only the selected references are included. Submitted source requires separate authorization for each send.",
    changeMaterials: "Change materials",
    readSource: "Read authorized submission source",
    searchEvidence: "Search authorized references",
    verifySources: "Verify source citations",
    analysisStep: "Analysis step",
    retry: "Retry turn",
    composerLabel: "Message Agent",
    composerPlaceholder: "Ask a follow-up about this submission…",
    cancelTurn: "Cancel turn",
    cancelling: "Cancelling…",
    send: "Send",
    disclaimer:
      "Agent responses may be incomplete. Check cited locations before relying on a conclusion.",
    unavailable:
      "Agent has been disabled by an administrator. Please contact your administrator.",
    errors: {
      requestFailed:
        "The request did not complete. Check the connection and reload this view to see the saved state.",
      streamFailed:
        "The live activity stream disconnected. Automatic observation is limited; if the result is still pending, reload to check the server state.",
      streamTimedOut:
        "The live activity stream timed out. The turn may still be running.",
      runReplaced:
        "A newer run replaced this turn. Reload the conversation to see its current state.",
      submissionRequired:
        "Open a conversation from one of your eligible submissions.",
    },
  },
  errors: {
    requestFailed:
      "The request did not complete. Check the service connection, then reload the thread to see its canonical state.",
    streamFailed:
      "The event stream disconnected. The server remains the source of truth; reload the thread.",
    streamTimedOut:
      "The event stream timed out. The server remains the source of truth; reload the thread.",
    runReplaced:
      "This event stream was replaced by a newer run; the page now shows the server's current state.",
    sourceMismatch:
      "This Agent thread belongs to a different submission and was not loaded.",
  },
} as const;
