# UltiCode Technical Stack

UltiCode is an online programming platform built with a microservices architecture spanning backend, frontend, and data processing layers.

## Backend - Spring Boot

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17 | Runtime language (LTS) |
| Spring Boot | 3.2.5 | Application framework |
| Spring Boot Starter Web | (managed) | REST API endpoints |
| Spring Boot Starter Validation | (managed) | Request validation |
| Spring Boot Starter Security | (managed) | Authentication/Authorization |
| Spring Boot Starter Data Redis | (managed) | Redis integration |
| Spring Boot Starter WebSocket | (managed) | Real-time communication |
| Spring Boot Starter AOP | 3.5.12 | Aspect-oriented programming |
| Spring Boot Starter Mail | (managed) | Email sending |
| Spring Boot Starter Cache | (managed) | Caching abstraction |

### Data Access

| Component | Version | Purpose |
|-----------|---------|---------|
| MyBatis-Plus | 3.5.16 | ORM framework |
| MyBatis-Plus JSQLParser | 3.5.16 | SQL parsing support |
| MySQL Connector/J | (managed by Spring Boot) | MySQL JDBC driver |

### Distributed Computing

| Component | Version | Purpose |
|-----------|---------|---------|
| Apache Dubbo | 3.2.14 | RPC framework for recommendation service |
| Dubbo Registry Nacos | 3.2.14 | Service discovery integration |

### Authentication & Security

| Component | Version | Purpose |
|-----------|---------|---------|
| jjwt-api | 0.13.0 | JWT token generation |
| jjwt-impl | 0.13.0 | JWT implementation |
| jjwt-jackson | 0.13.0 | JWT JSON serialization |
| Redisson | 4.3.1 | Distributed locks, Redis client |
| OWASP Encoder | 1.4.0 | XSS output encoding |

### API Documentation

| Component | Version | Purpose |
|-----------|---------|---------|
| springdoc-openapi-starter-webmvc-ui | 2.6.0 | OpenAPI/Swagger documentation |

### Utilities

| Component | Version | Purpose |
|-----------|---------|---------|
| Hutool | 5.8.44 | Java utility library |
| Lombok | 1.18.44 | Boilerplate reduction |
| MapStruct | 1.6.3 | Object mapping |

### Search

| Component | Version | Purpose |
|-----------|---------|---------|
| MeiliSearch Java SDK | 0.20.0 | Full-text search engine |
| OkHttp | 5.3.2 | HTTP client for MeiliSearch |

### Build Tools

| Component | Version | Purpose |
|-----------|---------|---------|
| Maven Compiler Plugin | (managed) | Java compilation |
| Jacoco Maven Plugin | 0.8.12 | Code coverage |

---

## Recommendation Service

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17 | Runtime language |
| Spring Boot | 3.2.5 | Application framework |
| Apache Dubbo | 3.2.14 | RPC framework |
| Apache Spark | 3.5.1 | Offline computation |
| Scala | 2.13.12 | Spark development language |
| MySQL Connector/J | 8.0.33 | Database connectivity |
| MyBatis Spring Boot Starter | 3.0.3 | ORM framework |
| Jedis | 5.1.0 | Redis client |
| Jackson | 2.17.0 | JSON processing |

### Recommendation Modules

| Module | Purpose |
|--------|---------|
| recommend-api | Dubbo service interface |
| recommend-core | Core recommendation algorithms |
| recommend-feature | Feature extraction engineering |
| recommend-provider | Dubbo service implementation |
| recommend-web | REST API layer |
| recommend-spark | Spark offline batch processing |

---

## Frontend - Console (User-facing Application)

| Component | Version | Purpose |
|-----------|---------|---------|
| Node.js | ^20.19.0 \|\| >=22.12.0 | Runtime |
| Vue | 3.5.25 | UI framework |
| Vue Router | 5.0.4 | Client-side routing |
| Pinia | 3.0.4 | State management |
| Vite | 8.0.8 | Build tool |
| Tailwind CSS | 4.1.17 | Utility-first CSS |

### UI Libraries

| Component | Version | Purpose |
|-----------|---------|---------|
| shadcn-vue / Reka UI | 2.6.1 | UI component library |
| Lucide Vue Next | 0.552.0 | Icon library |
| Tailwind CSS Vite Plugin | 4.1.17 | Tailwind integration |
| Tailwind CSS Typography | 0.5.19 | Prose styling |
| Class Variance Authority | 0.7.1 | Component variants |
| Tailwind Merge | 3.4.0 | Class merging utility |
| CLSX | 2.1.1 | Class name utility |

### Editor & Code Display

| Component | Version | Purpose |
|-----------|---------|---------|
| Monaco Editor | 0.52.2 | Code editor |
| Monaco Loader | 1.7.0 | Monaco loading |
| Highlight.js | 11.11.1 | Syntax highlighting |
| KaTeX | 0.16.25 | Math rendering |
| markdown-it | 14.1.0 | Markdown parsing |
| markdown-it-katex | 2.0.3 | KaTeX markdown plugin |
| DOMPurify | 3.3.3 | HTML sanitization |

### Charts & Visualization

| Component | Version | Purpose |
|-----------|---------|---------|
| ECharts | 6.0.0 | Chart library |
| Unovis TS | 1.6.2 | Data visualization |
| Unovis Vue | 1.6.2 | Vue bindings |

### Real-time Communication

| Component | Version | Purpose |
|-----------|---------|---------|
| STOMP.js | 7.3.0 | WebSocket messaging |
| SockJS Client | 1.6.1 | WebSocket fallback |

### Internationalization

| Component | Version | Purpose |
|-----------|---------|---------|
| Vue I18n | 11.3.2 | Internationalization |
| Internationalized Date | 3.10.0 | Date handling |

### Storage & Offline

| Component | Version | Purpose |
|-----------|---------|---------|
| idb | 8.0.3 | IndexedDB wrapper |
| Workbox Window | 7.4.0 | PWA service worker |

### Drag & Drop

| Component | Version | Purpose |
|-----------|---------|---------|
| Vue DND Kit Core | 1.7.0 | Drag and drop |

### Virtual Scrolling

| Component | Version | Purpose |
|-----------|---------|---------|
| TanStack Vue Virtual | 3.13.18 | Virtual list rendering |

### HTTP Client

| Component | Version | Purpose |
|-----------|---------|---------|
| Axios | 1.13.2 | HTTP client |

### Build & Development

| Component | Version | Purpose |
|-----------|---------|---------|
| TypeScript | ~6.0.3 | Type safety |
| Vue TSC | 3.1.5 | Type checking |
| ESLint | 9.30.1 | Linting |
| eslint-plugin-vue | 9.30.0 | Vue linting |
| Prettier | 3.8.3 | Code formatting |
| Vite Plugin PWA | 1.2.0 | PWA support |
| Vite Vue Devtools | 8.0.5 | Vue debugging |

### Testing

| Component | Version | Purpose |
|-----------|---------|---------|
| Vitest | 4.1.4 | Unit testing |
| Vue Test Utils | 2.4.6 | Vue component testing |
| JSDOM | 29.0.2 | DOM simulation |

---

## Frontend - Management (Admin Dashboard)

| Component | Version | Purpose |
|-----------|---------|---------|
| Node.js | ^20.19.0 \|\| >=22.12.0 | Runtime |
| Vue | 3.5.26 | UI framework |
| Vue Router | 5.0.4 | Client-side routing |
| Pinia | 3.0.4 | State management |
| Vite | 8.0.8 | Build tool |
| Tailwind CSS | 4.1.18 | Utility-first CSS |

### UI Libraries

| Component | Version | Purpose |
|-----------|---------|---------|
| Reka UI | 2.7.0 | UI component library |
| Lucide Vue Next | 0.562.0 | Icon library |
| Tailwind CSS Vite Plugin | 4.1.18 | Tailwind integration |
| Class Variance Authority | 0.7.1 | Component variants |
| Tailwind Merge | 3.4.0 | Class merging utility |
| CLSX | 2.1.1 | Class name utility |
| Vaul Vue | 0.4.1 | Drawer component |
| Embla Carousel Vue | 8.6.0 | Carousel component |
| Vue Input OTP | 0.3.2 | OTP input component |

### Form Handling

| Component | Version | Purpose |
|-----------|---------|---------|
| Vee Validate | 4.15.1 | Form validation |
| Vee Validate Zod | 4.15.1 | Zod schema validation |
| Zod | 3.25.76 | Schema validation |

### Drag & Drop

| Component | Version | Purpose |
|-----------|---------|---------|
| DND Kit Abstract | 0.1.21 | Drag and drop |
| DND Kit Modifiers | 9.0.0 | Drag modifiers |
| DND Kit Vue | 0.0.2 | Vue integration |

### Table Display

| Component | Version | Purpose |
|-----------|---------|---------|
| TanStack Vue Table | 8.21.3 | Table component |

### Charts & Visualization

| Component | Version | Purpose |
|-----------|---------|---------|
| Unovis TS | 1.6.2 | Data visualization |
| Unovis Vue | 1.6.2 | Vue bindings |
| Highlight.js | 11.11.1 | Syntax highlighting |
| markdown-it | 14.1.0 | Markdown parsing |
| markdown-it-katex | 2.0.3 | KaTeX markdown plugin |

### Internationalization

| Component | Version | Purpose |
|-----------|---------|---------|
| Vue I18n | 11.3.2 | Internationalization |
| Internationalized Date | 3.7.0 | Date handling |
| date-fns | 4.1.0 | Date utilities |

### HTTP Client

| Component | Version | Purpose |
|-----------|---------|---------|
| Axios | 1.13.2 | HTTP client |

### Testing

| Component | Version | Purpose |
|-----------|---------|---------|
| Vitest | 4.0.15 | Unit testing |
| Vitest Coverage V8 | 4.1.4 | Coverage reporting |
| JSDOM | 29.0.2 | DOM simulation |

### Build & Development

| Component | Version | Purpose |
|-----------|---------|---------|
| TypeScript | ~6.0.3 | Type safety |
| Vue TSC | 3.2.1 | Type checking |
| ESLint | 10.2.1 | Linting |
| eslint-plugin-vue | ~10.8.0 | Vue linting |
| Prettier | 3.8.3 | Code formatting |
| Vite Vue Devtools | 8.0.5 | Vue debugging |
| Jiti | 2.6.1 | TypeScript/JIT compilation |
| Tw Animate CSS | 1.4.0 | Animation utilities |

---

## Database

| Component | Version | Purpose |
|-----------|---------|---------|
| MySQL | 9.1 | Primary relational database |
| Flyway | 12.x | Database migration tool |

### Database Manager (db-manager)

| Component | Version | Purpose |
|-----------|---------|---------|
| Python | >=3.10 | Runtime |
| python-dotenv | >=1.0.0 | Environment variable loading |
| Click | >=8.0.0 | CLI framework |
| Rich | >=13.0.0 | Terminal formatting |

---

## DevOps & Containerization

| Component | Version | Purpose |
|-----------|---------|---------|
| Docker Compose | (latest) | Container orchestration |
| PM2 | (global) | Process manager |
| MySQL Docker Image | 9.1 | Database container |
| Redis Docker Image | 7-alpine | Cache container |
| Nacos Docker Image | v2.3.2 | Service discovery container |

### PM2 Services

| Service | Port | Working Directory | Script |
|---------|------|-------------------|--------|
| ulticode-9001 | 9001 | ./backend-spring | start.cjs |
| ulticode-9002 | 9002 | ./console | vite --port 9002 |
| ulticode-9003 | 9003 | ./management | vite --port 9003 |
| ulticode-9004 | 9004 | ./recommendation | start-provider.cjs |
| ulticode-9005 | 9005 | ./recommendation | start-web.cjs |

---

## Build Tools

| Component | Version | Purpose |
|-----------|---------|---------|
| Maven | 3.9+ | Backend build tool |
| pnpm | (workspace) | Frontend package manager |
| npm-run-all2 | 8.0.4 | Script orchestration |

---

## Migration Scripts

| Version | Description |
|---------|-------------|
| V1 | core_schema - Users, permissions, submissions |
| V2 | problem_schema - Problems, tags, lists |
| V3 | contest_schema - Contests, rankings |
| V4 | forum_schema - Forum discussions |
| V5 | subscription_schema - User subscriptions |
| V6 | moderation_schema - Content moderation |
| V7 | recommendation_schema - Recommendation data |
| V8 | collection_schema - Problem collections |
| V9 | solution_schema - Solution articles |
| V10 | edge_schema - Edge computing data |
