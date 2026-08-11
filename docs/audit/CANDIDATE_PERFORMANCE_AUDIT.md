# Candidate Performance Audit

| ID | Problem | Evidence | Impact | Recommended fix | Priority |
|---|---|---|---|---|---|
| PERF-001 | Public job search does server-side pagination, but saved jobs/applications/notifications/CVs return unpaged lists. | Job search `LIMIT/OFFSET`: `backend/src/main/java/com/sjp/recruitment/service/JobService.java:68-139`; unpaged methods: `ApplicationService.java:141`, `CandidateService.java:213-249`, `candidateService.ts:20-100`. | Large candidate histories can slow API, render, and mobile interaction. | Add paged DTOs and UI pagination/load-more for candidate-owned lists. | P1 |
| PERF-002 | Job search uses `ILIKE '%term%'` and `LOWER(...)` filters that may not use normal indexes. | `JobService.java:881-894`, `911-953`; indexes in `V1__initial_schema.sql:557-562`. | Search may degrade as jobs grow. | Add trigram/full-text indexes and query plan tests. | P2 |
| PERF-003 | Job search maps saved/applied per row with existence checks. | `JobService.java:1009-1010`. | Potential N+1 queries per job page for authenticated candidates. | Preload saved/applied job IDs for the page and map in memory. | P2 |
| PERF-004 | Candidate subscription computes profile ID multiple times and counts CVs by loading a list. | `CandidateService.java:279-286`. | Unnecessary queries/list loading on subscription page. | Resolve profile ID once and use repository count query for CVs. | P3 |
| PERF-005 | Recommendations load top 20 jobs then calculate/sort in memory. | `JobService.java:217-230`. | Acceptable small cap now; not personalized beyond skills/location and no caching. | Cache or move match scoring to query/service when recommendation volume grows. | P3 |
| PERF-006 | AI STT polling can block up to 60 seconds in a `Callable` with sleep polling. | `GladiaTranscriptionClient.java:24-29`, `74-103`; controller returns `Callable` at `AiInterviewController.java:79-84`. | Threads can be occupied by external provider latency. | Use async job queue/callback or nonblocking scheduler for long STT. | P1 |
| PERF-007 | AI answer evaluation and summary are synchronous during finish. | `AiInterviewService.java:282-310`, `345-363`, `505-542`. | Candidate may wait for multiple model calls; timeouts create fallback scores. | Queue evaluations and stream progress; allow candidate to leave and return. | P1 |
| PERF-008 | Frontend candidate UI is concentrated in one large `App.tsx` and not route-code-split. | Candidate/page functions all in `frontend/src/App.tsx:1248-5178`; Vite app imports all at top. | Initial bundle likely includes many pages and admin/employer imports. | Lazy-load route components and split candidate/admin/employer surfaces. | P2 |
| PERF-009 | Google fonts are imported through CSS `@import`. | `frontend/src/styles/global.css:1`. | CSS import can delay font loading and render path. | Use `<link rel=preconnect/preload>` and `font-display: swap`. | P3 |
| PERF-010 | Lists render all sessions/questions/history without virtualization. | `frontend/src/App.tsx:4704-4724`, `5134-5178`. | Large AI histories can render slowly. | Paginate sessions and collapse/virtualize long question history. | P2 |
| PERF-011 | MediaRecorder audio is held in memory and uploaded as one file. | `frontend/src/App.tsx:4790-4816`. | Large recordings can spike memory on low-end devices. | Enforce visible timer/size and consider chunk upload if long recordings are allowed. | P2 |
| PERF-012 | No stale request cancellation for job search or candidate list effects. | `frontend/src/App.tsx:1918-1931`, `3629-3635`, `3679-3685`, `4055-4061`. | Rapid navigation/filter changes can race and set stale state. | Use AbortController or request IDs in services/effects. | P2 |

## Needs Measurement

- Production bundle size and route chunks.
- Job search query plans with realistic rows.
- Candidate list render time with 100/1,000 applications, notifications, and saved jobs.
- STT and AI provider latency percentiles.
