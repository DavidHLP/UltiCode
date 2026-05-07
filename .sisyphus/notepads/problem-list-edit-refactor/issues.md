# Issues

## Database
- problem_lists table missing version column (needs migration)

## Backend
- No module-specific update methods in ProblemListService
- No OptimisticLockException handling in GlobalExceptionHandler
- Controller lacks MANAGE_PROBLEMS role support

## Frontend
- GeneralInfo.vue is monolithic (all fields in one component)
- No auto-save implementation
- No MANAGE_PROBLEMS permission constant
- API client has no module-specific methods
