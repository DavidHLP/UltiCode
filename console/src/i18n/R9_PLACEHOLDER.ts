// R8.6 i18n keys added to contest.ts were not referenced by any view
// or store (R8 review MED-2). Moved here as a placeholder so the
// translation team has a single reference; R9 will wire them into the
// relevant view templates as part of the LOW i18n closure.

export const R8_PLACEHOLDER_KEYS = {
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
    notRegisteredForVirtualReplay: "You must finish the original contest before replaying virtually",
    contestCancelledNoVirtual: "This contest was cancelled and cannot be replayed virtually",
    alreadyInVirtualContestOtherTab: "You already have an active virtual session in another tab",
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
};
