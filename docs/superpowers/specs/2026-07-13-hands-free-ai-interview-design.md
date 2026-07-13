# Hands-Free AI Interview With Deferred Evaluation

Date: 2026-07-13

## Goal

Turn the current continuous voice mode into a natural, hands-free interview. The candidate clicks once to start, speaks through each question without using other controls, and clicks once to end. The system saves answers during the interview but evaluates them only after the interview ends.

## Current Behavior

- The browser reads a question and starts continuous Vietnamese speech recognition.
- A silence timer submits the current transcript immediately.
- `submitAnswer` evaluates that answer before the backend creates the next question.
- Feedback and scores can therefore appear before the interview has ended.

## Chosen Approach

Keep the browser Web Speech API as the free speech layer and introduce an explicit conversation state machine in `useVoiceConversation`. Separate answer confirmation from answer evaluation in the backend.

This is preferred over a provider-backed audio WebSocket because it works with the existing architecture, keeps credentials out of the browser, and does not add a paid streaming dependency. Manual recording remains available when the browser does not support continuous speech recognition.

## Conversation State Machine

The voice controller owns these phases:

1. `idle`: the interview has not started.
2. `speaking-question`: speech synthesis reads the current question.
3. `listening-answer`: recognition streams the candidate answer into an answer buffer.
4. `asking-confirmation`: after five seconds without recognized speech, synthesis asks: “Bạn đã trả lời xong chưa?”
5. `listening-confirmation`: recognition writes to a separate confirmation buffer.
6. `saving-answer`: a positive confirmation saves the answer without evaluating it.
7. `speaking-question`: the next question is read automatically.
8. `ending`: the candidate clicked the end button and the backend is evaluating the session.
9. `completed` or `error`: the result is displayed or a recoverable error is shown.

Speech synthesis and recognition never run at the same time, preventing the microphone from transcribing the AI voice.

## Confirmation Rules

- Positive phrases include normalized variants such as “xong”, “đã xong”, “tôi đã trả lời xong”, “đúng”, and “rồi”.
- Negative phrases include “chưa”, “chưa xong”, “tôi muốn nói thêm”, and “tiếp tục”.
- Positive confirmation commits only the answer buffer. Confirmation speech is never appended to the answer.
- Negative confirmation resumes answer recognition and preserves the existing buffer.
- An unclear confirmation causes one short clarification prompt and listens again.
- Silence while waiting for confirmation repeats the confirmation prompt instead of committing an answer.
- An empty answer cannot be confirmed; the system asks the candidate to provide an answer and resumes listening.

Phrase matching is case-insensitive and diacritic-insensitive so common Vietnamese recognition variants are accepted.

## Frontend Behavior

- The primary voice UI exposes only `Bắt đầu phỏng vấn` before starting and `Kết thúc phỏng vấn` while active.
- Pause, auto-submit, skip, manual submit, transcript editing, and per-question feedback actions are hidden during hands-free mode.
- The current question, live transcript, progress, voice phase, microphone error, and evaluation progress remain visible.
- The start action requests microphone permission, speaks the current question, and begins the loop.
- The end action stops recognition and synthesis, then sends the current non-empty answer buffer with the finish request so recent speech is not lost.
- If all configured questions are confirmed, the AI announces that the question set is complete and waits for the candidate to click end.
- Leaving the room stops microphone tracks, recognition, synthesis, and all timers.
- Unsupported browsers receive the existing manual recording and transcript controls as a fallback.

## Backend API And Data Flow

### Confirm answer

Add a session-scoped endpoint that confirms one answer:

`POST /candidate/ai-interviews/sessions/{sessionId}/questions/{questionId}/confirm`

The request contains the transcript. The operation:

- validates ownership, mutable session state, question identity, and transcript length;
- stores the transcript and `answeredAt`;
- leaves feedback status as `pending` and creates no score or feedback;
- creates the next question when the configured question count has not been reached;
- is idempotent for an identical replay and rejects a conflicting replay.

The existing answer endpoint remains available for compatibility but the hands-free UI uses the confirmation endpoint.

### Finish interview

Add:

`POST /candidate/ai-interviews/sessions/{sessionId}/finish`

The request may include `questionId` and the current transcript. The operation first saves that partial answer when non-empty, then:

1. evaluates every confirmed, non-skipped answer that has no completed feedback;
2. stores feedback and score for each answer;
3. creates the session summary from all evaluated answers;
4. marks the session `completed` and returns the complete result.

The operation is idempotent: finishing an already completed session returns its current result. A session with no non-empty answers is rejected with a clear validation error. Provider failures use the existing fallback feedback so one failed provider call cannot prevent completion.

## Evaluation Visibility

- Before session completion, answer responses omit or leave feedback pending; the UI does not render scores or per-question comments.
- After completion, the existing result screen renders the overall score, summary, and feedback for every answered question.
- Skipped or unanswered generated questions receive no invented score. The overall score is calculated from answered questions only.

## Error Handling

- Microphone permission denial ends voice mode and presents the manual fallback.
- Recognition `no-speech` events keep the active loop alive.
- Duplicate recognition callbacks cannot save the same question twice.
- Failure to save a confirmed answer keeps the transcript buffer and offers a voice/UI retry state.
- Failure during final evaluation leaves the session resumable; a repeated finish request evaluates only remaining pending answers.
- Navigation and component unmount always release microphone and speech resources.

## Verification

Frontend tests cover:

- five-second answer silence triggers confirmation, not submission;
- positive, negative, unclear, and silent confirmation paths;
- confirmation speech is excluded from the answer transcript;
- next-question speech starts after a successful save;
- one start/one end control behavior and cleanup;
- finish includes an unsaved current transcript;
- unsupported-browser fallback.

Backend tests cover:

- confirming an answer persists no feedback or score;
- identical and conflicting confirmation replay;
- next-question progression without evaluation;
- finishing evaluates all pending answers and builds the summary;
- finishing with a current partial answer;
- repeated finish requests;
- provider fallback and a session with no answers.

The Maven test suite, frontend Vitest suite, TypeScript/Vite build, lint, and UTF-8 checks must pass.

## Explicit Limits

- Browser speech recognition support and accuracy depend on the browser vendor; Chromium-based browsers are the primary target.
- The confirmation intent detector is deterministic phrase matching, not a separate AI call.
- Final evaluation is performed after the end action and may take several seconds depending on the configured AI provider.
- Third-party API credentials remain backend-only and are supplied through `.env`.
