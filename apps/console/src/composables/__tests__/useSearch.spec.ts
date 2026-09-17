import { effectScope, type EffectScope } from "vue";
import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { useSearch, type UseSearchReturn } from "../useSearch";
import { SearchIndex, type SearchResponse } from "@/types/search";

const mocks = vi.hoisted(() => ({
  search: vi.fn(),
}));

vi.mock("@/api/search", () => ({
  searchApi: {
    search: mocks.search,
  },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve, reject };
}
function response(query: string): SearchResponse {
  return {
    query,
    total: 1,
    page: 1,
    limit: 10,
    results: [
      {
        id: query,
        type: SearchIndex.PROBLEMS,
        title: query,
        url: `/problems/${query}`,
      },
    ],
  };
}

function createSearch(): { search: UseSearchReturn; scope: EffectScope } {
  const scope = effectScope();
  let search!: UseSearchReturn;
  scope.run(() => {
    search = useSearch({ debounceMs: 50 });
  });
  return { search, scope };
}

async function flushPromises(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

describe("useSearch request lifecycle", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mocks.search.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("ignores stale responses and keeps the latest request loading state", async () => {
    const first = deferred<SearchResponse>();
    const second = deferred<SearchResponse>();
    mocks.search
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const { search, scope } = createSearch();

    search.search("old");
    vi.advanceTimersByTime(50);
    const firstSignal = mocks.search.mock.calls[0][1] as AbortSignal;

    search.search("new");
    expect(firstSignal.aborted).toBe(true);
    vi.advanceTimersByTime(50);
    expect(mocks.search).toHaveBeenCalledTimes(2);
    expect(search.loading.value).toBe(true);

    first.resolve(response("old"));
    await flushPromises();
    expect(search.results.value).toEqual([]);
    expect(search.error.value).toBeNull();
    expect(search.loading.value).toBe(true);

    second.resolve(response("new"));
    await flushPromises();
    expect(search.results.value[0]?.id).toBe("new");
    expect(search.loading.value).toBe(false);
    scope.stop();
  });

  it("ignores stale errors and finally handlers after a newer query starts", async () => {
    const first = deferred<SearchResponse>();
    const second = deferred<SearchResponse>();
    mocks.search
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise);
    const { search, scope } = createSearch();

    search.search("old");
    vi.advanceTimersByTime(50);
    search.search("new");
    vi.advanceTimersByTime(50);

    first.reject(new Error("old request failed"));
    await flushPromises();
    expect(search.error.value).toBeNull();
    expect(search.loading.value).toBe(true);

    second.reject(new Error("new request failed"));
    await flushPromises();
    expect(search.error.value).toBe("new request failed");
    expect(search.loading.value).toBe(false);
    scope.stop();
  });

  it("cancels debounce and in-flight requests on clear, close, and scope dispose", () => {
    const pending = deferred<SearchResponse>();
    mocks.search.mockReturnValue(pending.promise);
    const { search, scope } = createSearch();

    search.search("queued");
    search.clear();
    vi.advanceTimersByTime(50);
    expect(mocks.search).not.toHaveBeenCalled();

    search.search("active");
    vi.advanceTimersByTime(50);
    const signal = mocks.search.mock.calls[0][1] as AbortSignal;
    expect(signal.aborted).toBe(false);

    search.close();
    expect(signal.aborted).toBe(true);
    expect(search.loading.value).toBe(false);

    search.search("disposed");
    vi.advanceTimersByTime(50);
    const disposedSignal = mocks.search.mock.calls[1][1] as AbortSignal;
    scope.stop();
    expect(disposedSignal.aborted).toBe(true);
  });
});
