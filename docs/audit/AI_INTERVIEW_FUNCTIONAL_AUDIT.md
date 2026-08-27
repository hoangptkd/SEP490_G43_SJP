# AI Virtual Interview Functional Audit

## Pipeline Trace

Candidate selects interview
-> `AiInterviewPage` loads config/applications/question sets/sessions (`frontend/src/App.tsx:4462-4477`)
-> Candidate creates application/practice session (`frontend/src/App.tsx:4489-4524`)
-> API client posts to `/candidate/ai-interviews/sessions/application|practice` (`frontend/src/services/aiInterviewService.ts:27-45`)
-> `AiInterviewController` delegates (`backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:51-58`)
-> `AiInterviewService` validates enabled/config/quota/ownership and creates session/questions (`AiInterviewService.java:120-207`)
-> ShopAIKey generates AI question or fixed bank is copied (`AiInterviewService.java:459-502`, `ShopAiKeyClient.java:31-62`)
-> UI displays current unanswered question (`frontend/src/App.tsx:4771`, `4980-5109`)
-> Candidate records audio (`frontend/src/App.tsx:4790-4816`)
-> UI uploads audio to current-question STT endpoint (`frontend/src/App.tsx:4821-4826`)
-> Backend validates audio and calls Gladia (`AiInterviewService.java:234-264`, `668-706`, `GladiaTranscriptionClient.java:31-103`)
-> Transcript is saved and returned (`AiInterviewService.java:250-257`)
-> Candidate submits/confirm answer (`frontend/src/App.tsx:4836-4859`)
-> Backend saves answer, prevents duplicate, optionally creates next question (`AiInterviewService.java:314-342`)
-> Candidate finishes (`frontend/src/App.tsx:4866-4879`)
-> Backend evaluates answers and summary (`AiInterviewService.java:282-310`, `345-363`, `505-542`)
-> Response assembler returns feedback/result (`AiInterviewResponseAssembler.java:24-130`)
-> UI displays score, feedback, fallback retry options (`frontend/src/App.tsx:4948-5178`)

## Findings

| ID | Area | Current behavior | Failure point / problem | Evidence | Priority | Acceptance criteria |
|---|---|---|---|---|---|---|
| AI-FUNC-001 | Correct candidate | Sessions are created for current candidate and fetched by candidate ID. | Positive; needs integration tests for cross-candidate IDs. | `AiInterviewService.java:120-132`, `708-713`. | P2 | Cross-candidate session/question/answer IDs return 404/403. |
| AI-FUNC-002 | Correct job/JD | Application session uses `application.getJob()` and practice can use active job. | Prompt includes job title and requirements text, but not full description, benefits, work mode, seniority, or snapshot. | `AiInterviewService.java:135-140`, `ShopAiKeyClient.java:222-231`. | P1 | Question generation context includes the full relevant job snapshot used at application time. |
| AI-FUNC-003 | Eligibility | Application AI interview allowed for submitted/under-review/shortlisted and active jobs. | No UI pre-explanation of exact eligibility rules beyond empty state. | `AiInterviewService.java:651-657`, `frontend/src/App.tsx:4614`. | P2 | Empty/disabled state explains statuses that qualify. |
| AI-FUNC-004 | Question order | Questions are persisted with increasing `orderIndex`; fixed bank copies order. | Good invariant; needs duplicate/order tests under concurrent answer submission. | `AiInterviewService.java:459-482`, `484-502`, schema `V1__initial_schema.sql:291-294`. | P2 | Concurrent submissions do not create duplicate order indexes. |
| AI-FUNC-005 | Answer mapping | Backend validates question belongs to session and unique answer per session/question. | Current audio upload has no question ID and attaches transcript to backend "current question"; stale multi-tab UI can map transcript to unexpected question. | `AiInterviewService.java:234-252`, `730-733`; frontend upload `aiInterviewService.ts:51-60`. | P1 | Audio upload includes expected question ID/version and conflicts on stale state. |
| AI-FUNC-006 | Lost answer risk | Transcript and audio file are local state until transcribe/submit. | Refresh/back loses unsubmitted audio/transcript. | `frontend/src/App.tsx:4764-4779`, `4821-4879`. | P1 | Nonempty transcript/audio prompts before navigation or autosaves draft. |
| AI-FUNC-007 | Duplicate answer risk | Backend returns idempotent success if same transcript is already answered; different transcript conflicts. | Good backend behavior; UI should surface conflict as already answered and refresh. | `AiInterviewService.java:322-328`, `frontend/src/App.tsx:4836-4847`. | P2 | Duplicate submit does not create duplicate DB rows and UI recovers with latest session. |
| AI-FUNC-008 | Retry handling | Retry feedback and summary endpoints exist; fallback labels shown. | STT retry exists only by re-uploading audio; no preserved original audio after failure unless UI still has local file. | `frontend/src/App.tsx:4896-4917`, `AiInterviewService.java:400-447`, `234-264`. | P2 | STT failures let user retry safely or edit transcript without re-recording unexpectedly. |
| AI-FUNC-009 | API/AI timeout | Provider read timeout defaults to 20s, STT poll waits up to 60s. | Candidate sees generic busy text; no client-side timeout/cancel flow. | `AiInterviewProperties.java:25-26`, `GladiaTranscriptionClient.java:24-29`, `frontend/src/App.tsx:4821-4917`. | P2 | Long calls show elapsed state, timeout message, and retry. |
| AI-FUNC-010 | STT errors | Backend marks transcript failed and returns retry message. | Evaluation can proceed from manually edited transcript; no confidence/quality marker stored with transcript. | `AiInterviewService.java:258-264`, `AiInterviewResponseAssembler.java:102-113`. | P1 | Answer record stores transcript source/confidence/status and evaluation shows when transcript was manually edited or STT failed. |
| AI-FUNC-011 | Interview resume | Candidate can reopen sessions from history. | Selected session is local state; URL does not deep-link to session; browser Back does not restore selected room. | `frontend/src/App.tsx:4456`, `4531-4533`, `4748`. | P2 | `/candidate/ai-interviews?session=...` deep-links to an owned session. |
| AI-FUNC-012 | Refresh behavior | Persisted answers/questions survive refresh if session is reopened manually. | In-progress current local transcript is lost. | `frontend/src/App.tsx:4773-4779`. | P1 | Refresh preserves or warns about in-progress answer draft. |
| AI-FUNC-013 | Completion rules | Finish requires at least one non-skipped answer; completed sessions are idempotent. | UI allows path where all questions skipped then finish fails. | `AiInterviewService.java:286-302`, `frontend/src/App.tsx:5096-5099`. | P2 | UI disables/explains finish until one evaluable answer exists. |
| AI-FUNC-014 | Fallback completion | Provider evaluation failure saves fallback feedback/summary and completes session. | Candidate may receive a numeric score not produced by AI provider. | `AiInterviewService.java:345-363`, `564-585`, `frontend/src/App.tsx:4953-4965`. | P1 | Fallback result is explicitly non-final or non-scored unless benchmarked and accepted. |
| AI-FUNC-015 | Rate limits | Costly AI actions are rate limited per candidate/operation in memory. | In-memory limiter resets on restart and is not distributed. | `AiInterviewRateLimiter.java:24-55`. | P2 | Production limiter is distributed or documented as single-node only. |

## Manual Verification

- Multi-tab answer submission and audio upload stale question behavior.
- Refresh/back during recording, after transcript, while finish is evaluating.
- Provider timeout UX for STT, question generation, answer evaluation, summary.
- Browser support for MediaRecorder and Web Speech across Chrome/Edge/Safari/mobile.
