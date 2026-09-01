# P2-APP-004 app-api Deletion Test & Consumer Slice Review

> status: DECISION COMPLETE
> head: c344f6268084a893f0bde871da21e5130a331207 (contract baseline)
> current interface source: `services/api/app-api/src/main/java`

## Deletion-test result

The deletion test removes a candidate declaration in a disposable working copy and compiles the listed consumers. It is a source/consumer exercise, not a production deployment test. Results:

| Candidate | Production consumers | Consumer slice | Result | Decision |
|---|---|---|---|---|
| `ProblemAdminReadPort` (19 methods) | Admin | `DubboProblemAdminReadAdapter`, `AdminProblemServiceImpl`, `ProblemCutoverService`, `ProblemExportServiceImpl`, `ProblemImportServiceImpl`, `AdminTestCaseService`, `ProblemTagHandler`, `DefaultAdminSolutionProjection`, `DefaultAdminSubmissionProjection` | Removal breaks all listed slices; methods group into bounded row/tab/list/test-case/tag reads but one provider RPC avoids N+1 | KEEP as deep App/Problem owner contract |
| `ProblemOwnerPort` (write + import batch) | Admin | `DubboProblemOwnerAdapter`, `ProblemImportServiceImpl`, `ImportProblemsRequestDTO`, `AdminProblemServiceImpl` | Removal breaks moderation/import write slices; contract already owns max batch and affected-row semantics | KEEP; no God aggregate created |
| `ProblemListSearchReadPort` | Admin | `DubboProblemListSearchReadAdapter`, Admin list projection | Removal breaks one bounded list use-case | KEEP; single use-case seam |
| `SolutionOwnerPort` | Admin | solution moderation/cutover services | Removal breaks owner-only write boundary | KEEP |
| `ContestAdminReadPort` | Admin | contest projection/cutover | Removal breaks owner read boundary | KEEP |

## Method-to-consumer slices (`ProblemAdminReadPort`)

| Method family | Consumer/use-case | RPC shape | Error/freshness |
|---|---|---|---|
| `findProblem`, `findBySlug`, `findBySlugs`, `findProblemsByIds` | Admin problem list, read-back, submission/admin enrichment | bounded single/batch | null for missing row; lists non-null |
| `findDescription`, `findCode`, `findCases` | Admin problem tabs | one composed bounded payload per tab | null for missing row |
| `listProblems`, `listAllProblems`, `listFlaggedProblems`, `searchProblemIdsByTitle` | Admin list/export/moderation/search | page or bounded export | list/page non-null |
| `listTestCases`, `getTestCase`, `findTestCasesByIds`, `exportTestCases` | Admin test-case CRUD/reorder/export | page/batch bounded by provider caps | null only for missing single row |
| `listTags`, `getTagById`, `tagNameExists`, `tagSlugExists` | Admin tag list/conflict checks | page/single/boolean | provider owns conflict and row semantics |

## Leverage and locality evaluation

- **Leverage**: Each retained interface prevents foreign entity/mapper imports and keeps App-owned SQL local to the provider.
- **Locality**: The provider implementation stays in App Problem/ProblemList/Solution modules; Admin adapters are transport-only.
- **RPC count**: Existing batch/tab methods intentionally reduce N+1. Splitting every method into a new interface would increase reference count and create more retries/timeout surfaces.
- **Compatibility cost**: Existing contracts are public and consumed by Admin. No safe deletion without a major contract retirement; no deletion evidence exists for retained interfaces.
- **Method count is not the decision**: wide interfaces are retained only where method groups correspond to one owner and bounded read use-case; no new aggregate interface is added.

## Decision

`ProblemAdminReadPort` and `ProblemOwnerPort` remain stable provider-owned contracts. The four confirmed misplaced interfaces were internalized by P2-APP-003. No additional `app-api` split or God interface is justified by current evidence.

## Verification

- Graph path: `search_graph` for `ProblemAdminReadPort`/`ProblemOwnerPort`, `trace_path` inbound, and direct source reads because graph generation predates current edits.
- Coverage check: `services/api/app-api/src/main/java/com/ulticode/app/api/service/ProblemAdminReadPort.java` and `ProblemOwnerPort.java` had no recorded parse gap; direct reads are authoritative.
- Source inventory: `grep` found 33 references/consumer files across production and tests; test-only rows were not counted as production consumers.
- No contract implementation or DTO was changed by this decision task.

## Evidence Level

Repository Implemented. Decision evidence only; no production SLO claim.
