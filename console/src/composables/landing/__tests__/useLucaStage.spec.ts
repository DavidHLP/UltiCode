/**
 * useLucaStage unit tests.
 *
 * useLucaStage self-provides synchronously. mountStage calls it directly and
 * captures the returned stage. mountConsumer intentionally has no parent provider
 * so inject() returns the built-in fallback.
 */
import { defineComponent, h, nextTick, ref, shallowRef, watch } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import {
  useLucaStage,
  useLucaStageConsumer,
  type LucaCommand,
  type LucaStage,
} from "../useLucaStage";

// -------------------------------------------------------------------------- //
// Mount helpers
// -------------------------------------------------------------------------- //

/** Calls useLucaStage (which self-provides synchronously) and captures the stage. */
function mountStage(callback?: () => void) {
  const stage = shallowRef<LucaStage | null>(null);

  const Provider = defineComponent({
    setup() {
      stage.value = useLucaStage(
        ref<HTMLElement | null>(null),
        { onFutureTransitionComplete: callback },
      );
      return () => h("div");
    },
  });

  const wrapper = mount(Provider, { global: { stubs: { mode: true } } });
  return { wrapper, stage };
}

/** No parent provider → inject returns the fallback. */
function mountConsumer() {
  const stage = shallowRef<LucaStage | null>(null);
  const Host = defineComponent({
    setup() {
      stage.value = useLucaStageConsumer();
      return () => h("div");
    },
  });
  const wrapper = mount(Host, { global: { stubs: { mode: true } } });
  return { wrapper, stage };
}

// -------------------------------------------------------------------------- //
// Consumer fallback
// -------------------------------------------------------------------------- //

describe("useLucaStageConsumer", () => {
  let wrapper: ReturnType<typeof mount>;

  afterEach(() => {
    wrapper?.unmount();
    vi.restoreAllMocks();
  });

  it("returns no-op stage when no provider is present", () => {
    const { wrapper: w, stage } = mountConsumer();
    wrapper = w;
    expect(stage.value!.completedCommand.value).toBeNull();
    expect(() => stage.value!.requestFutureTransition()).not.toThrow();
    expect(() => stage.value!.reportCommandCompleted(999)).not.toThrow();
  });
});

// -------------------------------------------------------------------------- //
// Command channel
// -------------------------------------------------------------------------- //

describe("useLucaStage — command channel", () => {
  let wrapper: ReturnType<typeof mount>;

  afterEach(() => {
    wrapper?.unmount();
    vi.restoreAllMocks();
  });

  it("requestExplode publishes an explode command with a new id", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    stage.value!.requestExplode();
    expect(stage.value!.command.value).not.toBeNull();
    expect(stage.value!.command.value!.kind).toBe("explode");
    expect(typeof stage.value!.command.value!.id).toBe("number");
  });

  it("requestReverse publishes a reverse command", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    stage.value!.requestReverse();
    expect(stage.value!.command.value).not.toBeNull();
    expect(stage.value!.command.value!.kind).toBe("reverse");
  });

  it("each call increments the command id", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    stage.value!.requestExplode();
    const id1 = stage.value!.command.value!.id;
    stage.value!.requestReverse();
    const id2 = stage.value!.command.value!.id;
    expect(id2).toBeGreaterThan(id1);
  });
});

// -------------------------------------------------------------------------- //
// reportCommandCompleted
// -------------------------------------------------------------------------- //

describe("useLucaStage — reportCommandCompleted", () => {
  let wrapper: ReturnType<typeof mount>;

  afterEach(() => {
    wrapper?.unmount();
    vi.restoreAllMocks();
  });

  it("fires completedCommand once for a known id", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    stage.value!.requestExplode();
    const cmd = stage.value!.command.value!;
    stage.value!.reportCommandCompleted(cmd.id);
    expect(stage.value!.completedCommand.value?.id).toBe(cmd.id);
    expect(stage.value!.completedCommand.value?.kind).toBe("explode");
  });

  it("second report with same id is idempotent (defensive no-op)", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    stage.value!.requestExplode();
    const cmd = stage.value!.command.value!;
    stage.value!.reportCommandCompleted(cmd.id);
    expect(() => stage.value!.reportCommandCompleted(cmd.id)).not.toThrow();
    expect(stage.value!.completedCommand.value?.id).toBe(cmd.id);
  });

  it("reportCommandCompleted with unknown id is a defensive no-op", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    expect(() => stage.value!.reportCommandCompleted(9999)).not.toThrow();
    expect(stage.value!.completedCommand.value).toBeNull();
  });

  it("different command ids advance the completedCommand channel", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    stage.value!.requestReverse();
    const cmd1 = stage.value!.command.value!;
    stage.value!.reportCommandCompleted(cmd1.id);
    expect(stage.value!.completedCommand.value?.id).toBe(cmd1.id);
    expect(stage.value!.completedCommand.value?.kind).toBe("reverse");

    stage.value!.requestExplode();
    const cmd2 = stage.value!.command.value!;
    expect(cmd2.id).not.toBe(cmd1.id);
    stage.value!.reportCommandCompleted(cmd2.id);
    expect(stage.value!.completedCommand.value?.id).toBe(cmd2.id);
    expect(stage.value!.completedCommand.value?.kind).toBe("explode");
  });

  // Regression test: if `pendingTransitionId` were set AFTER `command.value`
  // was published, a synchronous completion call during `requestFutureTransition`
  // could miss the callback.  A flush:'sync' watcher fires immediately when the
  // source is already non-null — proving the id was reserved before the command
  // became observable to any watcher.
  it("synchronous completion during requestFutureTransition fires callback (race regression)", async () => {
    const onTransitionComplete = vi.fn();
    const { wrapper: w, stage } = mountStage(onTransitionComplete);
    wrapper = w;

    let syncCallCount = 0;
    const stop = watch<LucaCommand | null>(
      () => stage.value!.command.value,
      (cmd) => {
        if (!cmd) return;
        syncCallCount++;
        stage.value!.reportCommandCompleted(cmd.id);
      },
      { flush: "sync" },
    );

    stage.value!.requestFutureTransition();
    await nextTick();

    expect(syncCallCount).toBeGreaterThan(0);
    expect(onTransitionComplete).toHaveBeenCalledTimes(1);

    stop();
  });
});

// -------------------------------------------------------------------------- //
// requestFutureTransition
// -------------------------------------------------------------------------- //

describe("useLucaStage — requestFutureTransition", () => {
  let wrapper: ReturnType<typeof mount>;

  afterEach(() => {
    wrapper?.unmount();
    vi.restoreAllMocks();
  });

  it("dispatches an explode command", () => {
    const { wrapper: w, stage } = mountStage();
    wrapper = w;
    stage.value!.requestFutureTransition();
    expect(stage.value!.command.value?.kind).toBe("explode");
  });

  it("fires onFutureTransitionComplete when completion is reported", () => {
    const onTransitionComplete = vi.fn();
    const { wrapper: w, stage } = mountStage(onTransitionComplete);
    wrapper = w;
    stage.value!.requestFutureTransition();
    const cmd = stage.value!.command.value!;
    expect(onTransitionComplete).not.toHaveBeenCalled();
    stage.value!.reportCommandCompleted(cmd.id);
    expect(onTransitionComplete).toHaveBeenCalledTimes(1);
  });

  it("reverse does not fire the future-transition callback", () => {
    const onTransitionComplete = vi.fn();
    const { wrapper: w, stage } = mountStage(onTransitionComplete);
    wrapper = w;
    stage.value!.requestReverse();
    const cmd = stage.value!.command.value!;
    stage.value!.reportCommandCompleted(cmd.id);
    expect(onTransitionComplete).not.toHaveBeenCalled();
  });
});
