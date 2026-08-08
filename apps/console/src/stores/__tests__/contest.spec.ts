import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { setActivePinia, createPinia } from "pinia";
import { useVirtualContestStore } from "@/stores/virtualContest";
import { fetchVirtualSession } from "@/api/contest";
import type { VirtualContestSession } from "@/types/contest";

vi.mock("@/api/contest", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/api/contest")>();
  return {
    ...actual,
    startVirtualContest: vi.fn(),
    finishVirtualContest: vi.fn(),
    fetchVirtualSession: vi.fn(),
  };
});

const VIRTUAL_SESSION_PREFIX = "ulticode:virtual-session:";

function makeStartedSession(
  overrides: Partial<VirtualContestSession> = {},
): VirtualContestSession {
  return {
    id: "live-session-id",
    contestId: "contest-upcoming-002",
    title: "链表专题赛",
    status: "STARTED",
    startedAt: "2026-06-18T09:41:35.516",
    endsAt: "2026-06-18T11:11:35.516",
    isActive: true,
    isCompleted: false,
    score: 0,
    penalty: 0,
    ...overrides,
  };
}

describe("useVirtualContestStore — loadVirtualSession (R10.1 / F-51)", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it("treats the persisted session as a placeholder and re-validates with the server (F-51 root cause)", async () => {
    // Mirror the user's reported bug: cache says "started" but the
    // participant row is FINISHED in the DB.
    const stale = makeStartedSession({ status: "started", isActive: true });
    sessionStorage.setItem(
      VIRTUAL_SESSION_PREFIX + stale.contestId,
      JSON.stringify(stale),
    );

    // The backend's authoritative answer: null (the participant row is
    // already FINISHED, so getVirtualSession returns no active session).
    vi.mocked(fetchVirtualSession).mockResolvedValue(null);

    const store = useVirtualContestStore();
    await store.loadVirtualSession(stale.contestId);

    // Server MUST be hit even when a cache exists — that's the whole
    // point of R10.1.
    expect(fetchVirtualSession).toHaveBeenCalledWith(stale.contestId);
    // Stale cache must be overwritten by the server's null.
    expect(store.virtualSession).toBeNull();
    // Cache slot must be cleared so a subsequent refresh doesn't re-read
    // the same stale blob.
    expect(
      sessionStorage.getItem(VIRTUAL_SESSION_PREFIX + stale.contestId),
    ).toBeNull();
    // isInVirtualContest should now read false → the timer card hides.
    expect(store.isInVirtualContest).toBe(false);
  });

  it("adopts the server's session when both cache and server report active", async () => {
    const cached = makeStartedSession({
      id: "cache-id",
      status: "started",
      isActive: true,
    });
    sessionStorage.setItem(
      VIRTUAL_SESSION_PREFIX + cached.contestId,
      JSON.stringify(cached),
    );

    const server = makeStartedSession({ id: "server-id" });
    vi.mocked(fetchVirtualSession).mockResolvedValue(server);

    const store = useVirtualContestStore();
    await store.loadVirtualSession(cached.contestId);

    expect(fetchVirtualSession).toHaveBeenCalledWith(cached.contestId);
    expect(store.virtualSession).toEqual(server);
    // Server's payload is now the cached snapshot.
    expect(
      sessionStorage.getItem(VIRTUAL_SESSION_PREFIX + cached.contestId),
    ).toEqual(JSON.stringify(server));
  });

  it("renders the cache immediately and lets the server response overwrite (R3.4 perf nicety)", async () => {
    const cached = makeStartedSession({ id: "cache-id" });
    sessionStorage.setItem(
      VIRTUAL_SESSION_PREFIX + cached.contestId,
      JSON.stringify(cached),
    );

    // Promise that doesn't resolve until the test says so. We assert
    // that the cache was applied to the store *before* the await.
    let resolveServer!: (value: VirtualContestSession | null) => void;
    vi.mocked(fetchVirtualSession).mockImplementation(
      () => new Promise<VirtualContestSession | null>((resolve) => {
        resolveServer = resolve;
      }),
    );

    const store = useVirtualContestStore();
    const loadPromise = store.loadVirtualSession(cached.contestId);

    // Synchronously after kicking off the load, the cache is rendered.
    expect(store.virtualSession).toEqual(cached);

    // Now let the server respond with null and confirm the store adopts
    // the server's truth.
    resolveServer(null);
    await loadPromise;

    expect(store.virtualSession).toBeNull();
  });

  it("falls back to the server when no cache exists", async () => {
    const server = makeStartedSession();
    vi.mocked(fetchVirtualSession).mockResolvedValue(server);

    const store = useVirtualContestStore();
    await store.loadVirtualSession(server.contestId);

    expect(fetchVirtualSession).toHaveBeenCalledWith(server.contestId);
    expect(store.virtualSession).toEqual(server);
    // The fresh server payload is itself cached for the next refresh.
    expect(
      sessionStorage.getItem(VIRTUAL_SESSION_PREFIX + server.contestId),
    ).toEqual(JSON.stringify(server));
  });

  it("keeps the cached session when the network request fails", async () => {
    const cached = makeStartedSession();
    sessionStorage.setItem(
      VIRTUAL_SESSION_PREFIX + cached.contestId,
      JSON.stringify(cached),
    );

    vi.mocked(fetchVirtualSession).mockRejectedValue(new Error("boom"));

    const store = useVirtualContestStore();
    await store.loadVirtualSession(cached.contestId);

    // The cache is the best we have while offline — keep it so the
    // timer survives an offline navigation.
    expect(store.virtualSession).toEqual(cached);
  });

  it("clears the in-memory session when the server fetch fails and no cache is present", async () => {
    vi.mocked(fetchVirtualSession).mockRejectedValue(new Error("boom"));

    const store = useVirtualContestStore();
    await store.loadVirtualSession("contest-x");

    expect(store.virtualSession).toBeNull();
  });
});

describe("useVirtualContestStore — start/finish atomicity (C3)", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it("startVirtualContest: API failure leaves state and storage untouched", async () => {
    const { startVirtualContest } = await import("@/api/contest");
    vi.mocked(startVirtualContest).mockRejectedValue(new Error("boom"));

    const store = useVirtualContestStore();
    const contestId = "contest-new";

    await expect(store.startVirtualContest(contestId)).rejects.toThrow("boom");
    expect(store.virtualSession).toBeNull();
    expect(sessionStorage.getItem(VIRTUAL_SESSION_PREFIX + contestId)).toBeNull();
    expect(store.error).toBe("boom");
  });

  it("finishVirtualContest: API failure leaves session and storage untouched", async () => {
    const { startVirtualContest, finishVirtualContest } = await import("@/api/contest");
    const session = makeStartedSession({ contestId: "contest-running" });
    vi.mocked(startVirtualContest).mockResolvedValue(session);

    // Establish a running session
    const store = useVirtualContestStore();
    await store.startVirtualContest(session.contestId);
    expect(store.virtualSession).not.toBeNull();

    // Now make finish reject
    vi.mocked(finishVirtualContest).mockRejectedValue(new Error("finish failed"));

    await expect(
      store.finishVirtualContest(session.contestId),
    ).rejects.toThrow("finish failed");
    // Session and storage must still hold the original session
    expect(store.virtualSession).not.toBeNull();
    expect(store.virtualSession!.id).toBe(session.id);
    expect(
      sessionStorage.getItem(VIRTUAL_SESSION_PREFIX + session.contestId),
    ).not.toBeNull();
  });
});
