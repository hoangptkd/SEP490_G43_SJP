# AI Interview Design

Date: 2026-06-29

## Goal

Build an MVP for a virtual AI interview room focused on candidate practice. The feature supports both application-based interviews and free practice sessions. AI output is only training feedback and must not affect job applications or hiring decisions.

## Confirmed Decisions

- MVP is candidate-only. Admin and employer views are deferred.
- The Candidate sidebar gets a dedicated AI Interview section.
- The AI Interview section has two tabs: application-based and free practice.
- Application mode requires the application to belong to the current candidate.
- Application mode allows application statuses `applied`, `reviewed`, and `shortlisted`.
- The related job must be active: `status = published` and `deadline >= today`.
- Practice mode requires the candidate to have a basic profile or at least one CV.
- Practice mode uses target role, skills, and an optional active job.
- Each session has 5 questions by default.
- Questions are generated dynamically one at a time, using prior answers.
- The default language is Vietnamese.
- Candidate answers by audio.
- Audio is uploaded after recording; transcription is not realtime.
- STT provider is Gladia.
- AI question generation, answer feedback, and session summary use ShopAIKey.
- ShopAIKey uses OpenAI-compatible API format.
- Default ShopAIKey model is `gpt-5-mini-2025-08-07`, configured through `.env`.
- Provider keys/config live only in backend `.env`.
- Backend returns complete provider responses to the frontend; no streaming in MVP.
- Audio is not stored long-term. Backend may process it temporarily, then discard it.
- Only transcript and structured feedback are stored.
- Candidate may edit transcript before submitting it for AI feedback.
- A question is locked only after candidate clicks submit answer for feedback.
- Candidate cannot answer the same question again after submission.
- Candidate may skip a question.
- Skipped questions are treated as unanswered and score 0.
- Overall score is the average of question scores, with skipped questions counted as 0.
- Candidate sees progress and per-question feedback during the session.
- Overall score appears only at the final result.
- Candidate can resume in-progress sessions.
- Candidate can soft delete sessions from history.
- If provider config is missing, AI Interview is disabled and the UI reports that it is not configured.
- Gladia or ShopAIKey failures affect only the current interview operation, not the application/apply flow.
- Raw provider request/response bodies are not stored in MVP.

## Current Schema Context

The final database schema already contains core interview tables:

- `interview_sessions`
- `interview_questions`
- `interview_answers`
- `ai_answer_feedbacks`
- `ai_session_feedbacks`

The MVP should extend these tables instead of replacing them.

## Database Design

### `interview_sessions` additions

```sql
alter table interview_sessions
  add column application_id uuid null references applications(id) on delete set null,
  add column context_type text not null default 'practice'
    check (context_type in ('application', 'practice')),
  add column practice_context_json jsonb not null default '{}'::jsonb,
  add column deleted_at timestamptz null;
```

Purpose:

- `application_id` provides an auditable link for application-based sessions.
- `context_type` distinguishes application mode from practice mode.
- `practice_context_json` stores target role, skills, and optional job context for free practice.
- `deleted_at` supports candidate soft delete.

### `interview_answers` additions

```sql
alter table interview_answers
  add column transcript_status text not null default 'pending'
    check (transcript_status in ('pending', 'processing', 'completed', 'failed')),
  add column feedback_status text not null default 'pending'
    check (feedback_status in ('pending', 'processing', 'completed', 'failed')),
  add column error_message text null;
```

Purpose:

- Track STT and feedback state for retry/resume.
- Store a short sanitized error reason for current operation state.
- Do not store raw Gladia or ShopAIKey request/response payloads.

### Existing Tables

Use existing tables as follows:

- `interview_questions`: stores each dynamically generated question.
- `interview_answers`: stores transcript, skip flag, and answer state.
- `ai_answer_feedbacks`: stores per-answer feedback and score fields.
- `ai_session_feedbacks`: stores final session summary.

## Domain Model

### Session

Session types:

- Application session: `context_type = application`, `application_id` is required.
- Practice session: `context_type = practice`, `application_id` is null, `practice_context_json` stores role/skills/job data.

Session lifecycle:

- `created`: session created and first question being prepared.
- `in_progress`: candidate is answering questions.
- `completed`: 5 questions are resolved and final summary is saved.
- `cancelled`: reserved for future use.

If final summary generation fails after question 5, keep session `in_progress` and allow retry summary. Transition to `completed` only after summary succeeds.

### Question

Each session has up to 5 questions.

Question fields use existing schema:

- `order_index`
- `question_type`: `behavioral`, `technical`, `situational`, `general`
- `content`
- `difficulty`: AI chooses based on job/application/profile/CV/context.
- `skill_tag`
- `time_limit_seconds`
- `ai_generated = true`

Questions are generated one at a time. The prompt includes job/application data, candidate profile/CV context, and prior question-answer-feedback history.

### Answer

An answer is not final when audio is uploaded. It becomes final only after the candidate submits the reviewed transcript.

Answer states:

- Audio submitted for STT.
- Transcript draft returned by Gladia.
- Candidate edits transcript.
- Candidate submits transcript.
- Backend creates answer and feedback.

Skip behavior:

- Create an answer row with `is_skipped = true`.
- Store empty or short skipped transcript text.
- Create feedback with overall score 0.
- Move to next question or summary.

## Backend Configuration

Add backend `.env` variables:

```properties
GLADIA_API_KEY=
GLADIA_BASE_URL=https://api.gladia.io
SHOPAIKEY_API_KEY=
SHOPAIKEY_BASE_URL=https://api.shopaikey.com/v1
SHOPAIKEY_MODEL=gpt-5-mini-2025-08-07
AI_INTERVIEW_AUDIO_MAX_SECONDS=180
AI_INTERVIEW_AUDIO_MAX_SIZE_MB=25
AI_INTERVIEW_QUESTION_COUNT=5
```

Frontend must never receive provider keys.

If required provider config is missing, backend returns a config status showing AI Interview is unavailable.

## Backend Services

### `AiInterviewService`

Coordinates business flow:

- Validate candidate access.
- Create sessions.
- Generate first/current/next question.
- Upload audio for transcription.
- Submit answer transcript.
- Skip answer.
- Generate per-answer feedback.
- Generate final summary.
- Resume existing sessions.
- Soft delete sessions.

### `GladiaTranscriptionClient`

Responsibilities:

- Accept audio from backend service.
- Call Gladia pre-recorded transcription API.
- Return transcript text.
- Convert provider errors into sanitized internal exceptions.
- Never persist audio or raw response.

### `ShopAiKeyClient`

Responsibilities:

- Call `POST /chat/completions` using OpenAI-compatible format.
- Generate the next interview question.
- Generate structured per-answer feedback.
- Generate structured session summary.
- Validate that responses parse into the expected JSON shape.
- Convert provider errors into sanitized internal exceptions.

### Prompt Contracts

Question generation response:

```json
{
  "questionType": "behavioral|technical|situational|general",
  "difficulty": "easy|medium|hard",
  "skillTag": "string or null",
  "question": "string",
  "timeLimitSeconds": 180
}
```

Answer feedback response:

```json
{
  "score": 0,
  "feedback": "string",
  "strengths": ["string"],
  "weaknesses": ["string"],
  "suggestions": ["string"]
}
```

Session summary response:

```json
{
  "overallScore": 0,
  "summary": "string",
  "strengths": ["string"],
  "weaknesses": ["string"],
  "improvementPlan": ["string"]
}
```

The backend should clamp scores to 0-100 and reject malformed provider JSON with a retryable provider error.

## Candidate API Design

Base path:

```http
/api/candidate/ai-interviews
```

### Config

```http
GET /config-status
```

Response:

```json
{
  "enabled": true,
  "message": null,
  "questionCount": 5,
  "audioMaxSeconds": 180,
  "audioMaxSizeMb": 25
}
```

### Eligible Applications

```http
GET /eligible-applications
```

Returns applications owned by the candidate where:

- status is `applied`, `reviewed`, or `shortlisted`
- job is `published`
- job deadline is today or later

### Create Application Session

```http
POST /sessions/application
Content-Type: application/json

{
  "applicationId": "uuid"
}
```

Creates a session and first question.

### Create Practice Session

```http
POST /sessions/practice
Content-Type: application/json

{
  "targetRole": "Java Backend Developer",
  "skills": ["Spring Boot", "PostgreSQL"],
  "jobId": "uuid or null"
}
```

Creates a session and first question.

### Session History

```http
GET /sessions
GET /sessions/{sessionId}
DELETE /sessions/{sessionId}
```

`DELETE` performs soft delete using `deleted_at`.

### Upload Audio For Current Question

```http
POST /sessions/{sessionId}/questions/current/audio
Content-Type: multipart/form-data

file=<audio>
```

Returns transcript draft:

```json
{
  "questionId": "uuid",
  "transcript": "string",
  "transcriptStatus": "completed"
}
```

### Submit Answer For Feedback

```http
POST /sessions/{sessionId}/questions/{questionId}/answer
Content-Type: application/json

{
  "transcript": "string"
}
```

Creates answer and feedback. If this was question 5, also attempts final summary.

### Skip Question

```http
POST /sessions/{sessionId}/questions/{questionId}/skip
```

Creates skipped answer with score 0. If this was question 5, also attempts final summary.

### Retry Summary

```http
POST /sessions/{sessionId}/summary/retry
```

Allowed when 5 questions are resolved but final summary is missing.

## Frontend UX

Add `AI Interview` to Candidate sidebar.

### AI Interview Home

Sections:

- Config unavailable state if backend reports disabled.
- Tabs:
  - `Theo application`
  - `Practice tu do`
- Recent sessions list.
- Resume action for `created` or `in_progress` sessions.
- Delete action for old sessions.

### Application Tab

Displays eligible applications.

Each item shows:

- job title
- company
- application status
- deadline
- button to start session

### Practice Tab

Form fields:

- target role
- skills
- optional active job

Validation:

- candidate must have profile or CV
- target role required
- at least one skill required

### Interview Room

Layout:

- session context header
- disclaimer before starting
- progress: `Cau 1/5`
- current question
- record/stop audio controls
- transcript preview/edit after STT
- `Submit answer for feedback`
- `Skip`
- retry state for provider failures
- feedback panel after answer feedback completes
- next question action or automatic next question creation

Final result:

- overall score
- summary
- strengths
- weaknesses
- improvement plan
- disclaimer repeated

## Error Handling

Candidate-facing errors:

- Missing config: `AI Interview chua duoc cau hinh.`
- Provider failure: `He thong chua xu ly duoc cau tra loi nay, vui long thu lai.`
- Permission failure: `Ban khong co quyen truy cap phien phong van nay.`
- Invalid application/job state: explain the application or job is no longer eligible.

Backend behavior:

- Log provider failures.
- Do not expose provider stack traces.
- Do not change application state because of AI Interview.
- Keep session retryable when provider fails.

## Security And Privacy

- Only authenticated candidates can access MVP endpoints.
- Candidate can only access own sessions.
- Candidate can only create application sessions for own applications.
- Employer/admin cannot view transcripts or feedback in MVP.
- Audio is discarded after transcription.
- No raw provider request/response storage in MVP.
- API keys remain backend-only.

## Testing Plan

Backend tests:

- Candidate-only access.
- Reject unauthenticated requests.
- Reject non-candidate users.
- Reject application owned by another candidate.
- Allow statuses `applied`, `reviewed`, `shortlisted`.
- Reject statuses `accepted`, `rejected`, `withdrawn`.
- Reject inactive jobs.
- Practice mode requires profile or CV.
- Optional practice job must be active.
- Create session creates first question.
- Dynamic next question uses prior answers.
- Upload audio validates size and duration config.
- Submit answer locks the question.
- Cannot answer same question twice.
- Skip creates score 0 feedback.
- Overall score averages 5 question scores.
- Summary failure keeps session in progress.
- Summary retry completes session.
- Soft delete hides session from history.
- Provider missing config disables module.
- Provider failure does not affect application records.

Frontend tests:

- AI Interview nav appears for candidate.
- Disabled state appears when config missing.
- Application tab lists eligible applications.
- Practice tab validates role/skills.
- Interview room shows progress.
- Transcript preview can be edited.
- Submit locks answered question.
- Skip moves forward and counts as unanswered.
- Provider error shows retry.
- In-progress session can resume.
- Completed session shows final result.
- Soft delete removes session from history.

## Out Of Scope For MVP

- Employer access to AI Interview data.
- Admin dashboard/config UI.
- Realtime transcription.
- Streaming AI feedback.
- Video interview.
- Long-term audio storage.
- Multiple attempts for the same question.
- Subscription or quota limits.
- Raw provider audit storage.

## Future Extensions

- Admin provider health/config status.
- Employer-safe aggregate indicators without transcript/feedback content.
- Quotas by subscription plan.
- Realtime transcription.
- Optional sharing of a practice report by candidate.
- Multi-language selection.
- Progress analytics across sessions.
