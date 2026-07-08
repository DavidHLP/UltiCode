import { mount, flushPromises } from "@vue/test-utils";
import { describe, expect, afterEach, beforeEach, it, vi } from "vitest";
import VirtualContestTimer from "../VirtualContestTimer.vue";

const { contestStoreMock, toastErrorMock } = vi.hoisted(() => ({
  contestStoreMock: {
    isInVirtualContest: true,
    virtualSession: {
      id: "session-1",
      contestId: "contest-1",
      status: "started",
      startedAt: "2026-06-16T12:00:00.000Z",
      endsAt: "2026-06-16T12:00:02.000Z",
      isActive: true,
      score: 0,
      penalty: 0,
    },
    finishVirtualContest: vi.fn(),
  },
  toastErrorMock: vi.fn(),
}));

vi.mock("@/stores/virtualContest", () => ({
  useVirtualContestStore: () => contestStoreMock,
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("vue-sonner", () => ({
  toast: {
    error: toastErrorMock,
  },
}));

const stubs = {
  Card: { template: "<section><slot /></section>" },
  CardContent: { template: "<div><slot /></div>" },
  Button: { template: "<button><slot /></button>" },
  AlertDialog: { template: "<div><slot /></div>" },
  AlertDialogTrigger: { template: "<div><slot /></div>" },
  AlertDialogContent: { template: "<div><slot /></div>" },
  AlertDialogDescription: { template: "<p><slot /></p>" },
  AlertDialogFooter: { template: "<div><slot /></div>" },
  AlertDialogHeader: { template: "<div><slot /></div>" },
  AlertDialogTitle: { template: "<h2><slot /></h2>" },
  AlertDialogAction: { template: "<button><slot /></button>" },
  AlertDialogCancel: { template: "<button><slot /></button>" },
  Clock: { template: "<svg />" },
  Trophy: { template: "<svg />" },
};

function mountTimer() {
  return mount(VirtualContestTimer, {
    global: { stubs },
  });
}

describe("VirtualContestTimer", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-16T12:00:00.000Z"));
    contestStoreMock.isInVirtualContest = true;
    contestStoreMock.virtualSession = {
      id: "session-1",
      contestId: "contest-1",
      status: "started",
      startedAt: "2026-06-16T12:00:00.000Z",
      endsAt: "2026-06-16T12:00:02.000Z",
      isActive: true,
      score: 0,
      penalty: 0,
    };
    contestStoreMock.finishVirtualContest.mockResolvedValue(undefined);
    toastErrorMock.mockClear();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("finishes the virtual contest automatically when remaining time reaches zero", async () => {
    mountTimer();

    expect(contestStoreMock.finishVirtualContest).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(2000);
    await flushPromises();

    expect(contestStoreMock.finishVirtualContest).toHaveBeenCalledTimes(1);
    expect(contestStoreMock.finishVirtualContest).toHaveBeenCalledWith("contest-1");
  });

  it("does not submit duplicate finish requests after the timer has expired", async () => {
    mountTimer();

    await vi.advanceTimersByTimeAsync(5000);
    await flushPromises();

    expect(contestStoreMock.finishVirtualContest).toHaveBeenCalledTimes(1);
  });
});
