# Fixed AI Interview Question Bank Design

Date: 2026-07-16

## Goal

Add a seeded, reusable question-bank mode for AI interview practice sessions so testers can run a stable interview with known questions. The existing AI-generated practice flow must keep working unchanged when no fixed question set is selected.

## Current Behavior

Practice sessions currently create an `InterviewSession`, then call ShopAIKey through `ShopAiKeyClient.generateQuestion(...)` to create the first question. Later questions are generated after the candidate answers, confirms, or skips the current question.

Generated questions are stored in `interview_questions`, which is session-scoped history. This table is not a reusable question bank because each row requires a `session_id`.

The existing fallback questions in `AiInterviewService.fallbackQuestion(...)` are only used when the AI provider fails. They are not selectable by candidates and are not stored as a reusable dataset.

## Chosen Approach

Use a separate question bank and copy selected questions into a practice session.

New fixed questions will live in two new tables:

- `ai_question_sets`: one row per reusable question set.
- `ai_question_bank`: one row per question within a set.

When a candidate creates a practice session with a `questionSetId`, the backend copies the active questions from `ai_question_bank` into `interview_questions` for that session. The session then continues through the existing answer, skip, finish, feedback, and summary workflows.

Copying questions creates a stable session snapshot. If the question bank changes later, existing sessions keep the exact questions they started with.

## Data Model

### `ai_question_sets`

Stores reusable question-set metadata.

Fields:

- `id`: UUID primary key.
- `code`: unique stable code, for example `backend_java_spring_test`.
- `title`: display title.
- `description`: optional explanation.
- `target_role`: optional role hint.
- `is_active`: hides disabled sets from candidate selection.
- `created_at`: creation timestamp.
- `updated_at`: last update timestamp.

### `ai_question_bank`

Stores questions that belong to a reusable set.

Fields:

- `id`: UUID primary key.
- `question_set_id`: foreign key to `ai_question_sets`.
- `order_index`: order within the set.
- `question_type`: `behavioral`, `technical`, `situational`, or `general`.
- `difficulty`: `easy`, `medium`, or `hard`.
- `skill_tag`: optional skill label.
- `content`: question text.
- `time_limit_seconds`: suggested answer limit.
- `is_active`: allows disabling one question without deleting it.
- `created_at`: creation timestamp.
- `updated_at`: last update timestamp.

Constraints:

- `ai_question_sets.code` is unique.
- `(question_set_id, order_index)` is unique.
- Candidate-facing queries only return active sets that have at least one active question.

### Existing `interview_questions`

Keep using the existing session question table.

For fixed question sets:

- `ai_generated = false`
- `question_type`, `difficulty`, `skill_tag`, `content`, and `time_limit_seconds` are copied from `ai_question_bank`.

For AI-generated practice:

- `ai_generated = true`
- behavior remains unchanged.

## Seed Data

Add one initial seeded set for testing:

- `code`: `backend_java_spring_test`
- `title`: `Backend Java Spring Boot - Bộ test`
- `target_role`: `Backend Developer`
- `description`: `Bộ câu hỏi test cố định cho luồng phỏng vấn Backend Java Spring Boot.`

Seed five questions:

1. Hãy giới thiệu ngắn gọn về kinh nghiệm backend Java/Spring Boot của bạn và dự án gần nhất bạn tham gia.
2. Trong Spring Boot, bạn thường tổ chức Controller, Service, Repository như thế nào để code dễ bảo trì?
3. Bạn xử lý validation, exception và response lỗi trong REST API như thế nào?
4. Khi một API bị chậm, bạn sẽ kiểm tra và tối ưu từ database đến application như thế nào?
5. Hãy mô tả cách bạn thiết kế transaction cho một nghiệp vụ có nhiều bước ghi dữ liệu.

All five questions use `time_limit_seconds = 180`.

## Backend API

### List Question Sets

Add:

```http
GET /api/candidate/ai-interviews/question-sets
```

Authorization:

- Candidate role required.

Response:

```json
[
  {
    "id": "uuid",
    "code": "backend_java_spring_test",
    "title": "Backend Java Spring Boot - Bộ test",
    "description": "Bộ câu hỏi test cố định cho luồng phỏng vấn Backend Java Spring Boot.",
    "targetRole": "Backend Developer",
    "questionCount": 5
  }
]
```

Only active sets with active questions are returned.

### Create Practice Session

Extend the existing request:

```http
POST /api/candidate/ai-interviews/sessions/practice
```

Request body:

```json
{
  "targetRole": "Backend Developer",
  "skills": ["Java", "Spring Boot", "SQL"],
  "jobId": null,
  "questionSetId": "uuid"
}
```

`questionSetId` is optional.

If `questionSetId` is absent:

- Preserve current AI-generated flow.
- Create the session.
- Generate the first question through ShopAIKey.

If `questionSetId` is present:

- Validate the question set exists and is active.
- Load active questions ordered by `order_index`.
- Reject the request if the set has no active questions.
- Create a practice session.
- Store `questionMode = fixed`, `questionSetId`, and `questionSetCode` in `practiceContext`.
- Copy all active set questions into `interview_questions`.
- Set `ai_generated = false` for copied questions.
- Set `totalQuestions` to the number of copied questions.
- Set session status to `in_progress`.
- Return the normal `AiInterviewSessionResponse`.

The response shape does not need a breaking change. Existing frontend interview-room code can continue reading `session.questions`.

## Practice Session Flow

### AI-Generated Mode

This mode is unchanged:

1. Candidate enters `targetRole` and `skills`.
2. Backend creates session.
3. Backend generates the first question with ShopAIKey.
4. Each answered, confirmed, or skipped question may trigger the next generated question until `questionCount` is reached.

### Fixed Question Set Mode

New flow:

1. Candidate selects a seeded question set.
2. Backend creates session.
3. Backend copies all active questions from the selected set into the session.
4. Candidate answers, confirms, or skips questions using existing endpoints.
5. No ShopAIKey question generation occurs for that session.
6. Feedback and final summary still use the existing ShopAIKey evaluation flow when the interview is finished.

Question progression should use existing open-question logic. Since all questions already exist, answering question 1 should expose question 2 without creating another question.

Fixed mode uses the selected set's active question count as the session total. It must not use `app.ai-interview.question-count` to generate additional questions after the copied set is exhausted.

## Frontend UX

In the AI interview practice creation area, add a question mode selector:

- `AI tự tạo câu hỏi`
- `Bộ câu hỏi có sẵn`

Default remains `AI tự tạo câu hỏi`.

When `Bộ câu hỏi có sẵn` is selected:

- Fetch question sets from `GET /candidate/ai-interviews/question-sets`.
- Show selectable question-set options with title, target role, description, and question count.
- Keep `targetRole` and `skills` inputs visible so the session context remains explicit and useful for feedback.
- Send `questionSetId` in the create-practice request.

When `AI tự tạo câu hỏi` is selected:

- Do not send `questionSetId`.
- Existing behavior remains unchanged.

No interview-room layout changes are required for the first implementation.

## Error Handling

Question-set list:

- If no active sets exist, frontend shows an empty state and keeps AI-generated mode available.

Practice creation:

- Invalid UUID returns `400 QUESTION_SET_ID_INVALID`.
- Missing or inactive set returns `404 QUESTION_SET_NOT_FOUND`.
- Active set with no active questions returns `400 QUESTION_SET_EMPTY`.
- Other existing validation for `targetRole`, `skills`, profile/CV presence, job status, and rate limiting remains unchanged.

Fixed mode must not fall back to AI-generated questions silently. If a selected set is invalid or empty, return an explicit error so test sessions remain predictable.

## Testing

Backend tests:

- Listing question sets returns only active sets with active question counts.
- Creating practice without `questionSetId` keeps the current AI-generated behavior.
- Creating practice with `questionSetId` copies all questions into `interview_questions`.
- Copied questions have `ai_generated = false`.
- Fixed-mode creation does not call `ShopAiKeyClient.generateQuestion(...)`.
- Invalid, inactive, and empty question sets return the expected API errors.

Frontend tests or manual verification:

- Practice page loads question sets when fixed mode is selected.
- Creating fixed practice sends `questionSetId`.
- Returned session displays the seeded questions.
- AI-generated mode still creates a normal AI-generated practice session.

## Out Of Scope

This design does not include admin CRUD for question banks.

This design does not include importing question sets from files.

This design does not change answer evaluation, speech recognition, Gladia transcription, or final summary generation.

This design does not reuse old `interview_questions` rows across sessions.
