/**
 * @ulticode/domain-types — cross-stack DTO contract shared by console + management.
 *
 * The only contract proven to have callers in both apps is `PageResult<T>`. The
 * DTO/enum definitions (Problem / Contest / Comment / Forum / UserStats /
 * ProblemList) were removed because they were not consumed by production
 * callers — both apps keep parallel definitions in their own `types/` and
 * `api/admin/` trees (arch review 2026-07-10, candidate #3).
 *
 * Rule: only add a type here when a second adapter is proven to need the same
 * shape. Do not balloon this into a superset.
 *
 * Backend serves camelCase JSON (Spring Boot default Jackson, no snake_case
 * naming strategy), so DTO fields are camelCase. Database snake_case columns
 * are mapped via MyBatis mapUnderscoreToCamelCase and never leak into transport.
 */

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
  totalPages: number
}
