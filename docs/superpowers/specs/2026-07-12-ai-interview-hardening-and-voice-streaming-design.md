# AI Interview Hardening And Continuous Voice Design

Date: 2026-07-12

## Goal

Turn the current AI interview prototype into a production-safe candidate feature and add a hands-free Vietnamese voice conversation mode without exposing provider credentials to the browser.

## Chosen Approach

Use the browser Web Speech API as the default free voice layer. `SpeechRecognition` produces continuous interim/final transcript events and `speechSynthesis` reads each AI question. The existing upload-to-Gladia flow remains as a fallback for browsers without continuous recognition. Provider API keys remain backend-only and continue to be supplied through `.env`.

This approach avoids a new paid streaming dependency and works immediately on supported Chromium browsers. Because browser speech recognition is not supported uniformly, the UI must feature-detect it and retain the manual recorder/transcription workflow.

## Runtime Flow

1. Backend returns the current question and voice configuration.
2. Voice mode speaks the question in Vietnamese.
3. Recognition starts only after synthesis ends, preventing the microphone from transcribing the AI voice.
4. Interim results update the transcript live; final results are appended to the answer draft.
5. After a configurable silence interval and a non-empty final transcript, the client submits the answer once.
6. Backend claims the answer in a short locked transaction, evaluates it outside the transaction, persists feedback, and advances the session.
7. The next question is spoken and recognition resumes. The loop ends after the summary is available or the user pauses voice mode.

## Backend Hardening

- Add a Flyway V2 migration containing the interview tables/extensions and all unique/check/index constraints.
- Add optimistic versioning to interview sessions and pessimistic session locking for mutating workflows.
- Enforce one answer per `(session_id, question_id)` and one question per `(session_id, order_index)`.
- Make answer submission and skipping idempotent: an identical replay returns current state; conflicting replay returns `409`.
- Move Gladia and ShopAIKey network calls outside database transactions.
- Add explicit connect/read timeouts and bounded STT polling.
- Preserve provider failure as `fallback` evaluation metadata instead of presenting heuristic feedback as provider output.
- Validate role, skill count/length, transcript length, audio MIME, file size, and declared recording duration.
- Apply per-candidate request quotas to costly endpoints.
- Batch-load questions, answers, and feedback for session history and paginate history.

## Frontend

- Add a dedicated `useVoiceConversation` hook that owns recognition, synthesis, silence detection, cleanup, and feature detection.
- Show explicit states: unsupported, idle, AI speaking, listening, processing, paused, and error.
- Provide start/pause controls, live interim transcript, auto-submit toggle, and a manual submit escape hatch.
- Keep all controls keyboard accessible and connect status/error text through ARIA live regions.
- Preserve the current manual MediaRecorder flow as fallback.

## Data And API Changes

- Config response adds `voiceStreamingEnabled`, `voiceProvider`, and `voiceSilenceMs`.
- Audio upload accepts `durationSeconds` as multipart metadata.
- Feedback response adds `source` and `fallback`.
- Session history accepts `page` and `size`, while retaining a list response for the current frontend contract during this iteration.

## Failure Handling

- Recognition permission denial pauses voice mode and offers manual recording.
- Recognition restarts automatically only while voice mode is active and the AI is not speaking.
- Provider failures never leave an answer permanently in `processing`.
- Summary failures keep the session resumable and expose retry.
- Duplicate browser callbacks cannot submit the same answer twice.

## Verification

- Backend unit tests cover locking/idempotency, validation, provider fallback, session progression, and rate limiting.
- Repository/migration checks verify constraints on PostgreSQL-compatible SQL.
- Frontend tests cover unsupported browsers, transcript streaming, pause/resume, silence submit, and cleanup.
- Maven tests, TypeScript/Vite build, lint where configured, and UTF-8 checks must pass.

## Explicit Limits

- Web Speech recognition may use the browser vendor's online recognition service and is not uniformly supported across browsers.
- The application does not expose third-party API keys to the frontend.
- Multi-node distributed quotas require Redis or a gateway later; the included limiter protects a single backend instance.
