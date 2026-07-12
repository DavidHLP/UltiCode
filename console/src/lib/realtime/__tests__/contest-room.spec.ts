// console/src/lib/realtime/__tests__/contest-room.spec.ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { ContestRoom } from "../contest-room";
import { createInMemoryTransport } from "../in-memory-transport";

describe("ContestRoom", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("joins on the fast path when transport is already connected", async () => {
    const { transport, helpers } = createInMemoryTransport();
    helpers.simulateConnect();
    const room = new ContestRoom(transport);

    const promise = room.join("contest-1");
    await expect(promise).resolves.toMatchObject({
      success: true,
      contestId: "contest-1",
    });

    expect(room.contestId).toBe("contest-1");
    expect(helpers.subscriptions().get("contest-contest-1")).toBe(
      "/topic/contest/contest-1",
    );
    expect(helpers.published()).toEqual([
      { destination: "/app/contest.join", body: JSON.stringify({ contestId: "contest-1" }) },
    ]);
  });

  it("joins via the ready hook when transport is still connecting", async () => {
    const { transport, helpers } = createInMemoryTransport({
      // Mirror the production adapter: emit "ready" from onConnect so the
      // ContestRoom's join-during-connect dance fires.
      onConnect: (t) => t.emit("ready"),
    });
    const room = new ContestRoom(transport);

    const promise = room.join("contest-2");
    // Still connecting — the ready hook has not fired yet.
    expect(helpers.published()).toEqual([]);

    helpers.simulateConnect();
    await expect(promise).resolves.toMatchObject({ contestId: "contest-2" });

    expect(room.contestId).toBe("contest-2");
    expect(helpers.published()).toEqual([
      { destination: "/app/contest.join", body: JSON.stringify({ contestId: "contest-2" }) },
    ]);
  });

  it("rejects with timeout error when ready never fires within 10s", async () => {
    const { transport } = createInMemoryTransport();
    const room = new ContestRoom(transport);

    const promise = room.join("contest-3");
    promise.catch(() => {
      /* prevent unhandled rejection — the assertion below observes it */
    });

    vi.advanceTimersByTime(10_000);
    await expect(promise).rejects.toThrow("Connection timeout");
  });

  it("dispose cancels a pending join so it never settles", async () => {
    const { transport } = createInMemoryTransport();
    const room = new ContestRoom(transport);

    const promise = room.join("contest-4");
    let settled = false;
    promise.then(
      () => (settled = true),
      () => (settled = true),
    );

    room.dispose();
    // Microtask drain so any post-cleanup settlement runs.
    await Promise.resolve();
    expect(settled).toBe(false);

    // Advance past the timeout — dispose cleared it, so the promise stays
    // pending and no rejection ever fires (would hang the test otherwise).
    vi.advanceTimersByTime(11_000);
    await Promise.resolve();
    expect(settled).toBe(false);
  });

  it("leave unsubscribes the per-contest subscription and publishes leave", async () => {
    const { transport, helpers } = createInMemoryTransport();
    helpers.simulateConnect();
    const room = new ContestRoom(transport);

    await room.join("contest-5");
    expect(helpers.subscriptions().has("contest-contest-5")).toBe(true);

    const response = await room.leave();
    expect(response).toMatchObject({ success: true, contestId: "contest-5" });
    expect(room.contestId).toBeNull();
    expect(helpers.subscriptions().has("contest-contest-5")).toBe(false);

    const lastPublish = helpers.published().at(-1);
    expect(lastPublish).toEqual({
      destination: "/app/contest.leave",
      body: JSON.stringify({ contestId: "contest-5" }),
    });
  });

  it("leave is a no-op success when not currently in a contest", async () => {
    const { transport } = createInMemoryTransport();
    const room = new ContestRoom(transport);

    const response = await room.leave();
    expect(response).toMatchObject({
      success: true,
      contestId: "",
      message: expect.stringContaining("Not in any"),
    });
    expect(room.contestId).toBeNull();
  });

  it("re-subscribes the contest topic when joining a different contest", async () => {
    const { transport, helpers } = createInMemoryTransport();
    helpers.simulateConnect();
    const room = new ContestRoom(transport);

    await room.join("contest-a");
    expect(helpers.subscriptions().get("contest-contest-a")).toBe("/topic/contest/contest-a");

    await room.join("contest-b");
    expect(helpers.subscriptions().has("contest-contest-a")).toBe(false);
    expect(helpers.subscriptions().get("contest-contest-b")).toBe("/topic/contest/contest-b");
    expect(room.contestId).toBe("contest-b");
  });

  it("dispatches typed room events from delivered contest messages", async () => {
    const { transport, helpers } = createInMemoryTransport();
    helpers.simulateConnect();
    const room = new ContestRoom(transport);

    const rankings: unknown[] = [];
    const firstSolves: unknown[] = [];
    room.onRankingUpdate((data: unknown) => rankings.push(data));
    room.onFirstSolve((data: unknown) => firstSolves.push(data));

    await room.join("contest-6");

    helpers.deliver("/topic/contest/contest-6", {
      type: "ranking_update",
      contestId: "contest-6",
      rankings: [{ userId: "u1" }],
      updatedAt: "2026-07-13T00:00:00Z",
    });
    helpers.deliver("/topic/contest/contest-6", {
      event: "first_solve",
      contestId: "contest-6",
      problemId: "p1",
      problemTitle: "Two Sum",
      userId: "u1",
      username: "alice",
      solvedAt: "2026-07-13T00:01:00Z",
    });

    expect(rankings).toEqual([
      {
        type: "ranking_update",
        contestId: "contest-6",
        rankings: [{ userId: "u1" }],
        updatedAt: "2026-07-13T00:00:00Z",
      },
    ]);
    expect(firstSolves).toHaveLength(1);
    expect(firstSolves[0]).toMatchObject({ problemId: "p1", username: "alice" });
  });

  it("falls back to contest_update when the message body lacks type/event", async () => {
    const { transport, helpers } = createInMemoryTransport();
    helpers.simulateConnect();
    const room = new ContestRoom(transport);

    const updates: unknown[] = [];
    transport.on("contest_update", (data: unknown) => updates.push(data));

    await room.join("contest-7");
    helpers.deliver("/topic/contest/contest-7", { arbitrary: "payload" });

    expect(updates).toEqual([{ arbitrary: "payload" }]);
  });

  it("malformed JSON falls back to contest_update without throwing", async () => {
    const { transport, helpers } = createInMemoryTransport();
    helpers.simulateConnect();
    const room = new ContestRoom(transport);

    const updates: unknown[] = [];
    transport.on("contest_update", (data: unknown) => updates.push(data));

    await room.join("contest-8");
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => helpers.deliverRaw("/topic/contest/contest-8", "{not-json")).not.toThrow();
    spy.mockRestore();
    expect(updates).toEqual([]);
  });

  it("onConnectionStatus forwards connection status from the transport", async () => {
    const { transport, helpers } = createInMemoryTransport();
    const room = new ContestRoom(transport);

    const statuses: string[] = [];
    room.onConnectionStatus((s: unknown) => statuses.push(s as string));

    transport.connect();
    expect(statuses).toContain("connecting");

    helpers.simulateConnect();
    expect(statuses).toContain("connected");

    helpers.simulateDisconnect();
    expect(statuses).toContain("disconnected");
  });

  it("returns an unsubscribe function for typed event registrations", async () => {
    const { transport, helpers } = createInMemoryTransport();
    helpers.simulateConnect();
    const room = new ContestRoom(transport);

    const announcements: unknown[] = [];
    const off = room.onAnnouncement((data: unknown) => announcements.push(data));

    await room.join("contest-9");
    helpers.deliver("/topic/contest/contest-9", {
      type: "announcement",
      id: "a1",
      contestId: "contest-9",
      title: "Heads up",
      content: "Server maintenance in 5 min",
      createdAt: "2026-07-13T00:00:00Z",
    });
    expect(announcements).toHaveLength(1);

    off();
    helpers.deliver("/topic/contest/contest-9", {
      type: "announcement",
      id: "a2",
      contestId: "contest-9",
      title: "Update",
      content: "Now in 1 min",
      createdAt: "2026-07-13T00:01:00Z",
    });
    expect(announcements).toHaveLength(1);
  });
});