# @ulticode/shared-types

Shared type definitions for UltiCode backend, console, and management modules.

## Installation

```bash
pnpm install
```

## Development

```bash
# Build the package
pnpm build

# Watch mode
pnpm dev

# Type check
pnpm type-check
```

## Usage

```typescript
// Import entity types
import { UserEntity, ProblemEntity, UserRole } from '@ulticode/shared-types'

// Import DTOs
import { PaginatedResponse, PaginationParams } from '@ulticode/shared-types'

// Import enums
import { Difficulty, SubmissionStatus } from '@ulticode/shared-types'
```

## Structure

- `entities/` - Core entity types (User, Problem, Contest, Submission)
- `dto/` - Common data transfer objects (Pagination, Search, etc.)
- `enums/` - Shared enums (Difficulty, Role, SubmissionStatus, etc.)
