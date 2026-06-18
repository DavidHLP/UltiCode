import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

function readSource(path: string) {
  return readFileSync(resolve(process.cwd(), `src/${path}`), "utf8");
}

/**
 * Contest / virtual-contest problems must not expose other participants'
 * editorial-style solutions while the contest is live. The fix is
 * implemented in two layers: the desktop layout (driven by
 * `useProblemLayout` / `headerStore`) and the mobile tab bar
 * (`MobileProblemLayout.vue`). Both must react to the `?contestId=`
 * query parameter that the contest problem page sets.
 */
describe("useProblemLayout hides solutions tab in contest mode", () => {
  const source = readSource("views/problems/composables/useProblemLayout.ts");

  it("treats the presence of route.query.contestId as contest context", () => {
    expect(source).toMatch(/route\.query\.contestId/);
    expect(source).toMatch(/const\s+isContest\s*=\s*computed/);
  });

  it("threads isContest through every layout config builder", () => {
    for (const fn of [
      "getLeetLayoutConfig",
      "getClassicLayoutConfig",
      "getCompactLayoutConfig",
      "getWideLayoutConfig",
      "createInitialHeaderGroups",
    ]) {
      // Each builder must accept (or forward) isContest so the solutions
      // header can be omitted in contest mode. The signature may span
      // multiple lines, so allow newlines inside the parameter list.
      const fnDecl = new RegExp(
        `function\\s+${fn}\\s*\\([\\s\\S]*?isContest`,
        "m",
      );
      expect(source).toMatch(fnDecl);
    }
  });

  it("omits the solutions header when isContest is true", () => {
    // createInitialHeaderGroups should only push the solutions header
    // inside a `!isContest` branch.
    const createFnBody = source.match(
      /function\s+createInitialHeaderGroups[\s\S]*?\n\}/,
    )?.[0];
    expect(createFnBody).toBeDefined();
    expect(createFnBody).toMatch(/if\s*\(\s*!isContest\s*\)/);
    // The solutions header constant must be referenced inside the guard.
    const guardIndex = createFnBody!.indexOf("!isContest");
    const solutionsIdIndex = createFnBody!.indexOf("SOLUTIONS_HEADER_ID");
    expect(guardIndex).toBeGreaterThan(-1);
    expect(solutionsIdIndex).toBeGreaterThan(guardIndex);
  });

  it("rewrites ?tab=solutions to ?tab=description in contest mode", () => {
    // The URL→store watcher must intercept the solutions tab and replace
    // it with description so deep links / refreshes never land on a
    // hidden panel.
    expect(source).toMatch(
      /isContest\.value\s*&&\s*tabName\s*===\s*"solutions"[\s\S]*?router\s*\.\s*replace/,
    );
  });

  it("re-inits the layout when isContest changes", () => {
    // Navigating from a contest problem to a regular problem (or vice
    // versa) must rebuild the layout so the solutions tab appears /
    // disappears.
    expect(source).toMatch(
      /watch\(\s*isContest[\s\S]*?initData\(config\.groups,\s*config\.layout\)/,
    );
  });

  it("preserves route.query when syncing the active tab back to the URL", () => {
    // P1 regression: the store→URL watcher used to push the
    // `problem-detail` route without `query`, which dropped
    // `?contestId=...` on the first tab click. Once `isContest` flipped
    // false, the previously hidden Solutions tab reappeared mid-contest.
    // The fix is to forward `query: route.query` on the push.
    const storeToUrlBlock = source.match(
      /\/\/ Sync Store to URL[\s\S]*?router\s*\.\s*push\(\s*\{[\s\S]*?\}\s*\)/,
    )?.[0];
    expect(storeToUrlBlock).toBeDefined();
    expect(storeToUrlBlock!).toMatch(
      /router\s*\.\s*push\(\s*\{[\s\S]*?name:\s*"problem-detail"[\s\S]*?query:\s*route\.query[\s\S]*?\}\s*\)/,
    );
  });
});

describe("MobileProblemLayout hides solutions tab in contest mode", () => {
  const source = readSource(
    "views/problems/components/MobileProblemLayout.vue",
  );

  it("reads contestId from the problem context", () => {
    expect(source).toMatch(/useProblemContext/);
    expect(source).toMatch(
      /const\s*\{\s*contestId\s*\}\s*=\s*useProblemContext/,
    );
  });

  it("rebuilds the tab list when contestId changes", () => {
    // `tabs` must be a computed so the mobile tab bar reacts to the
    // contest query parameter.
    expect(source).toMatch(/const\s+tabs\s*=\s*computed\(\s*\(\)\s*=>\s*\{/);
  });

  it("omits the solutions entry when contestId is set", () => {
    const tabsBody = source.match(
      /const\s+tabs\s*=\s*computed\(\s*\(\)\s*=>\s*\{([\s\S]*?)\n\}\);/,
    )?.[1];
    expect(tabsBody).toBeDefined();
    // The solutions entry is pushed only inside `!isContest`.
    expect(tabsBody!).toMatch(/if\s*\(\s*!isContest\s*\)/);
    const guardIndex = tabsBody!.indexOf("!isContest");
    const solutionsIndex = tabsBody!.indexOf('id: "solutions"');
    expect(guardIndex).toBeGreaterThan(-1);
    expect(solutionsIndex).toBeGreaterThan(guardIndex);
  });

  it("falls back to description when the URL still points at solutions in contest mode", () => {
    // Initial URL→activeTab sync must rewrite `?tab=solutions` to
    // description so refreshes on the hidden tab do not break the UI.
    expect(source).toMatch(
      /contestId\.value\s*!==\s*null[\s\S]*?"solutions"[\s\S]*?"description"/,
    );
  });

  it("preserves route.query in the URL→activeTab sync", () => {
    // The mobile router.push must forward the current query so that
    // switching tabs in a contest problem keeps `?contestId=...` alive
    // and the hidden Solutions tab stays hidden.
    const pushBlock = source.match(
      /router\s*\.\s*push\(\s*\{[\s\S]*?\}\s*\)/,
    )?.[0];
    expect(pushBlock).toBeDefined();
    expect(pushBlock!).toMatch(/query:\s*route\.query/);
  });
});

describe("LayoutHeaderLeft preserves contest context on prev/next nav", () => {
  const source = readSource("views/problems/headers/LayoutHeaderLeft.vue");

  it("forwards the current query to the prev/next problem route", () => {
    // Both the previous- and next-problem RouterLink `to` objects must
    // carry `query: $route.query` so that jumping between sibling
    // problems inside a contest keeps the contest context alive.
    const toBlockMatches = source.match(
      /:to="\{\s*name:\s*'problem-detail'[\s\S]*?\}\s*"/g,
    );
    expect(toBlockMatches).toBeTruthy();
    expect(toBlockMatches!.length).toBeGreaterThanOrEqual(2);
    for (const block of toBlockMatches!) {
      expect(block).toMatch(/query:\s*\$route\.query/);
    }
  });
});

describe("Problem detail contest context lives in the main toolbar", () => {
  const problemDetailSource = readSource(
    "views/problems/ProblemDetailView.vue",
  );
  const headerControlsSource = readSource(
    "views/problems/headers/LayoutHeaderControls.vue",
  );

  it("does not render the old full-width contest shell above the problem workspace", () => {
    expect(problemDetailSource).not.toMatch(/<ContestProblemShell\b/);
    expect(problemDetailSource).not.toMatch(/<ContestReviewPanel\b/);
  });

  it("mounts the compact contest dock in the main toolbar controls", () => {
    expect(headerControlsSource).toMatch(/ContestProblemDock/);
    expect(headerControlsSource).toMatch(/<ContestProblemDock\s*\/>/);
  });
});
