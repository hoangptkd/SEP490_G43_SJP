# Candidate System Context

Audit basis: `docs/quality/CANDIDATE_SYSTEM_QUALITY_STANDARD.md` and static review of the implemented React/Spring Boot/PostgreSQL code. Evidence references use repository paths and line numbers.

## 1. Product Purpose

The system is a recruitment platform where candidates discover jobs, maintain profile/CV data, apply to jobs, track application workflow updates, receive notifications, manage a candidate subscription view, and practice AI interviews.

Implementation evidence:
- React routes are registered in `frontend/src/App.tsx:120-154`.
- Candidate API client methods are centralized in `frontend/src/services/candidateService.ts:9-113`.
- Candidate backend routes are implemented in `backend/src/main/java/com/sjp/recruitment/controller/CandidateController.java:19-126`.
- Candidate-only security is configured for `/candidate/**` and `/applications/**` in `backend/src/main/java/com/sjp/recruitment/config/SecurityConfig.java:52-53`.

## 2. Candidate Capabilities

Implemented candidate capabilities include:
- Register, login, forgot password, reset password, email verification, OAuth role completion: `frontend/src/App.tsx:1248`, `1472`, `1610`, `1691`, `1790`, `1815`.
- Candidate dashboard, profile, account, CVs, saved jobs, applications, application detail, AI interviews, notifications, subscription: `frontend/src/App.tsx:2571-2581`.
- Public job search and job detail: `frontend/src/App.tsx:1905`, `2194`.
- Job application with uploaded CV, builder CV, preferred location, and cover letter: `frontend/src/App.tsx:311-553`, `2262-2275`.
- Application tracking, interview response, offer response: `frontend/src/App.tsx:3747-4039`.
- AI interview practice/application sessions, STT, answer submission, summary/feedback: `frontend/src/App.tsx:4444-5178`.

Not implemented as first-class candidate modules:
- Languages are not present in the candidate profile DTO/entity/UI.
- Structured job preferences are not present; only `preferredLocation` is collected at application time.

## 3. Candidate User Journey

1. Candidate registers or signs in.
   - Frontend calls `authService.register/login` in `frontend/src/services/authService.ts:25-32`.
   - Backend validates email/password and creates candidate profile on candidate registration in `backend/src/main/java/com/sjp/recruitment/service/AuthService.java:63-83`.
2. Candidate completes profile and uploads/builds CVs.
   - Profile update is `candidateService.updateProfile` to `/candidate/profile` in `frontend/src/services/candidateService.ts:15-18`.
   - Backend writes profile fields and generic JSON profile sections in `backend/src/main/java/com/sjp/recruitment/service/CandidateService.java:65-78`.
   - CV upload/download/default/delete are exposed in `frontend/src/services/candidateService.ts:20-44` and backend `CandidateController.java:40-67`.
3. Candidate searches jobs and views job detail.
   - Frontend persists search/filter/sort/page in URL query params in `frontend/src/App.tsx:782-807`, then calls `jobService.getAll` at `frontend/src/App.tsx:1920`.
   - Backend returns paginated published, unexpired jobs in `backend/src/main/java/com/sjp/recruitment/service/JobService.java:68-139`.
4. Candidate saves or applies to a job.
   - Save/unsave calls `/candidate/saved-jobs/{jobId}` in `frontend/src/services/candidateService.ts:56-63`.
   - Apply calls `/applications` in `frontend/src/services/candidateService.ts:65-79`.
   - Backend enforces profile/CV readiness, job open status, duplicate prevention, and CV ownership in `backend/src/main/java/com/sjp/recruitment/service/ApplicationService.java:70-104`.
5. Candidate tracks application updates, interviews, offers, and notifications.
   - Application list/detail use `/applications/me` and `/applications/me/{id}` in `frontend/src/services/candidateService.ts:81-88`.
   - Candidate notification list/read methods use `/candidate/notifications` in `frontend/src/services/candidateService.ts:90-100`.
6. Candidate starts AI interview practice or application session.
   - Frontend loads AI config/applications/question sets/sessions at `frontend/src/App.tsx:4462-4477`.
   - Backend creates application/practice sessions at `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:120-207`.
7. Candidate records/speaks answers, transcribes, submits, finishes, and reviews results.
   - Manual recording/STT/submission/finish logic is in `frontend/src/App.tsx:4790-4917`.
   - Backend transcribes, persists answers, evaluates answers, and creates summary in `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:234-310`.

## 4. Main Candidate Routes

Public/auth:
- `/`, `/login`, `/register`, `/forgot-password`, `/reset-password`, `/verify-email`, `/oauth/callback`, `/select-role`, `/jobs`, `/jobs/:id`: `frontend/src/App.tsx:120-132`.

Protected candidate:
- `/candidate`
- `/candidate/profile`
- `/candidate/account`
- `/candidate/cvs`
- `/candidate/saved-jobs`
- `/candidate/applications`
- `/candidate/applications/:id`
- `/candidate/ai-interviews`
- `/candidate/notifications`
- `/candidate/subscription`
- `/candidate/subscription/plans`

Evidence: `frontend/src/App.tsx:142-154`.

## 5. Main Candidate-Related Entities

- `User`: authentication, role, status, profile identity.
- `CandidateProfile` mapped to `job_seekers`, with user-backed full name/phone and JSON sections: `backend/src/main/java/com/sjp/recruitment/model/entity/CandidateProfile.java:21-76`.
- `CandidateCv` / `CvVersion` mapped to `resumes`, split by `sourceType`: `backend/src/main/java/com/sjp/recruitment/model/entity/CandidateCv.java:15-69`, `backend/src/main/java/com/sjp/recruitment/model/entity/CvVersion.java:15-51`.
- `Application`: job/candidate/CV, status, preferred location, cover letter, job snapshot: `backend/src/main/java/com/sjp/recruitment/model/entity/Application.java:19-73`.
- `SavedJob`, `Notification`, `Subscription`.
- AI interview: `InterviewSession`, `InterviewQuestion`, `InterviewAnswer`, `AiAnswerFeedback`, `AiSessionFeedback` defined in schema `backend/src/main/resources/db/migration/V1__initial_schema.sql:261-346`.

## 6. Important APIs

Auth:
- `POST /auth/register`, `POST /auth/login`, `POST /auth/forgot-password`, `POST /auth/reset-password`, `POST /auth/verify-email`: `backend/src/main/java/com/sjp/recruitment/controller/AuthController.java:32-55`.

Candidate:
- `GET/PUT /candidate/profile`
- `GET/POST/PATCH/DELETE/GET download /candidate/cvs`
- `GET/POST/PUT/DELETE /candidate/cv-versions`
- `GET/POST/DELETE /candidate/saved-jobs`
- `GET/PATCH /candidate/notifications`
- `GET /candidate/subscription`

Evidence: `backend/src/main/java/com/sjp/recruitment/controller/CandidateController.java:23-126`.

Jobs/applications:
- `GET /jobs`, `GET /jobs/{id}`, `POST /jobs/{id}/reports`: `backend/src/main/java/com/sjp/recruitment/controller/JobController.java:24-49`.
- `POST /applications`, `GET /applications/me`, `GET /applications/me/{id}`: `backend/src/main/java/com/sjp/recruitment/controller/ApplicationController.java:20-31`.

AI interview:
- `/candidate/ai-interviews/config-status`
- `/eligible-applications`
- `/question-sets`
- `/sessions`, `/sessions/{sessionId}`
- `/sessions/application`, `/sessions/practice`
- `/questions/current/audio`
- `/speech`
- `/questions/{questionId}/answer`, `/confirm`, `/skip`, `/feedback/retry`
- `/finish`, `/summary/retry`

Evidence: `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:35-140`.

## 7. Authentication Flow

Registration:
- Frontend submits email/password/role in `frontend/src/App.tsx:1485-1492`.
- Backend checks duplicate email and password strength, stores password hash, sets `PENDING_VERIFICATION`, creates candidate profile for candidate users, and sends verification email in `backend/src/main/java/com/sjp/recruitment/service/AuthService.java:63-83`.
- Backend returns `AuthResponse` with `token = null` after registration in `backend/src/main/java/com/sjp/recruitment/service/AuthService.java:82`.

Login:
- Frontend calls `authService.login` and stores token/user in `frontend/src/App.tsx:1277-1289`.
- Backend verifies password and active verified status, updates `last_login_at`, and returns a JWT in `backend/src/main/java/com/sjp/recruitment/service/AuthService.java:107-130`.
- Axios attaches `Authorization: Bearer` from local storage in `frontend/src/services/api.ts:13-22`.

Expired/unauthorized:
- Axios removes token/user/role and redirects to login on non-login `401` in `frontend/src/services/api.ts:27-45`.

## 8. Candidate Authorization Flow

Frontend:
- `Protected` checks local stored token/user role and redirects mismatches to role home in `frontend/src/App.tsx:811-823`.

Backend:
- Spring Security restricts `/candidate/**` and `/applications/**` to `ROLE_CANDIDATE` in `backend/src/main/java/com/sjp/recruitment/config/SecurityConfig.java:52-53`.
- Application controller also has `@PreAuthorize("hasRole('CANDIDATE')")` in `backend/src/main/java/com/sjp/recruitment/controller/ApplicationController.java:14-17`.
- AI interview controller has candidate pre-authorization in `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:27-31`.
- Service-level ownership exists for CVs, notifications, applications, and sessions, for example `CandidateService.downloadCv` filters by candidate ID at `backend/src/main/java/com/sjp/recruitment/service/CandidateService.java:164-169`, and `ApplicationService.myApplication` filters by candidate ID at `backend/src/main/java/com/sjp/recruitment/service/ApplicationService.java:149-155`.

## 9. Virtual Interview Architecture

Frontend:
- `AiInterviewPage` loads config, eligible applications, recent sessions, jobs, and question sets: `frontend/src/App.tsx:4444-4483`.
- `AiInterviewRoom` manages current question, recording, transcript, answer submission, finish, retry feedback, retry summary, and hands-free voice conversation: `frontend/src/App.tsx:4748-5178`.

Backend:
- `AiInterviewController` exposes candidate AI endpoints: `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:29-140`.
- `AiInterviewService` creates sessions/questions, validates ownership, validates audio, transcribes, confirms answers, evaluates, and summarizes: `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:57-746`.
- `AiInterviewResponseAssembler` batches questions, answers, feedback, and summaries into DTO responses: `backend/src/main/java/com/sjp/recruitment/service/AiInterviewResponseAssembler.java:24-130`.
- AI provider configuration is in `backend/src/main/java/com/sjp/recruitment/config/AiInterviewProperties.java:10-46`.

## 10. AI Question Generation Flow

Candidate action -> frontend:
- Candidate creates application or practice session in `frontend/src/App.tsx:4489-4524`.

API -> backend:
- Frontend calls `createApplicationSession` or `createPracticeSession` in `frontend/src/services/aiInterviewService.ts:27-45`.
- Backend endpoint calls `AiInterviewService.createApplicationSession/createPracticeSession` in `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:51-58`.

Backend -> AI:
- Service creates session, then calls `createNextQuestion` at `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:142` or `200`.
- `createNextQuestion` calls `shopAiKeyClient.generateQuestion` unless fixed question mode is used; fallback questions are used on provider error: `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:459-482`.
- Prompt and JSON schema are in `backend/src/main/java/com/sjp/recruitment/service/ai/ShopAiKeyClient.java:31-62`.

Database -> response:
- Question is persisted as `InterviewQuestion` with order index and total question update at `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:474-481`.
- Assembler returns session DTO with questions at `backend/src/main/java/com/sjp/recruitment/service/AiInterviewResponseAssembler.java:70-96`.

## 11. Speech-To-Text Flow

Candidate action -> frontend:
- Manual recording uses `navigator.mediaDevices.getUserMedia` and `MediaRecorder` in `frontend/src/App.tsx:4790-4816`.
- Frontend posts audio through `aiInterviewService.uploadAudio` in `frontend/src/App.tsx:4821-4826` and `frontend/src/services/aiInterviewService.ts:51-60`.

API -> backend:
- Backend endpoint `/sessions/{sessionId}/questions/current/audio` returns a `Callable` for async processing in `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:79-84`.

Backend -> STT:
- Backend validates candidate/session/current question and audio constraints in `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:234-264` and `668-706`.
- Gladia upload/start/poll is implemented in `backend/src/main/java/com/sjp/recruitment/service/ai/GladiaTranscriptionClient.java:31-103`.
- Language configuration is hard-coded to Vietnamese in `backend/src/main/java/com/sjp/recruitment/service/ai/GladiaTranscriptionClient.java:57-60`.

Response -> UI:
- Transcript is stored on draft answer and returned to UI as `AiInterviewTranscriptResponse`; UI sets transcript text in `frontend/src/App.tsx:4824-4825`.

## 12. AI Answer Evaluation Flow

Candidate action -> frontend:
- Candidate submits transcript with current question ID: `frontend/src/App.tsx:4836-4846`.
- Candidate finishes interview; frontend can include current unsaved transcript/question: `frontend/src/App.tsx:4866-4875`.

API -> backend:
- `POST /questions/{questionId}/answer` and `/confirm` call `confirmAnswer`: `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:96-108`.
- `POST /finish` calls `finishInterview`: `backend/src/main/java/com/sjp/recruitment/controller/AiInterviewController.java:112-120`.

Backend -> DB/AI:
- `confirmAnswer` persists transcript, answered timestamp, and optionally creates next question: `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:314-342`.
- `finishInterview` evaluates each pending answer and generates a summary: `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:282-310`.
- `evaluateAnswer` calls ShopAIKey, saves provider feedback, or saves fallback feedback on provider failure: `backend/src/main/java/com/sjp/recruitment/service/AiInterviewService.java:345-363`.
- Evaluation prompt/schema are in `backend/src/main/java/com/sjp/recruitment/service/ai/ShopAiKeyClient.java:65-93`.

## Flow Map

| Candidate Action | Frontend | API | Backend Service | Database / AI Service | Response | Candidate UI |
|---|---|---|---|---|---|---|
| Register | `RegisterPage` | `POST /auth/register` | `AuthService.register` | `users`, `job_seekers`, verification token/email | `AuthResponse` without token | Success message, verify email |
| Login | `LoginPage` | `POST /auth/login` | `AuthService.login` | `users`, JWT | token + user | Stored session, role redirect |
| Update profile | `ProfilePage` | `PUT /candidate/profile` | `CandidateService.updateProfile` | `users`, `job_seekers`, `candidate_skills` | profile DTO | Success/error message |
| Upload CV | `CvPage` / apply modal | `POST /candidate/cvs` | `CandidateService.uploadCv` | local/remote storage, `resumes` | CV DTO | CV list / selected apply CV |
| Search jobs | `JobsPage` | `GET /jobs` | `JobService.search` | SQL search over jobs/company/skills | page response | Cards + pagination |
| Save job | `JobDetailPage` | `POST/DELETE /candidate/saved-jobs/{id}` | `CandidateService.saveJob/unsaveJob` | `saved_jobs` | no content | Saved state toggle |
| Apply job | `ApplyJobModal` | `POST /applications` | `ApplicationService.submit` | `applications`, history, notifications | application DTO | Success message/applied state |
| Track application | `ApplicationsPage` / detail | `GET /applications/me` | `ApplicationService.myApplications` | applications/history/interviews/offer | list/detail DTO | Table/detail/timeline |
| Notifications | `NotificationsPage` / menu | `GET/PATCH /candidate/notifications` | `CandidateService.getNotifications/markRead` | `notifications` | notification DTOs | List and unread count |
| Start AI interview | `AiInterviewPage` | `POST /candidate/ai-interviews/sessions/...` | `AiInterviewService.create...Session` | `interview_sessions`, questions, ShopAIKey | session DTO | Interview room |
| Transcribe answer | `AiInterviewRoom` | `POST .../audio` | `AiInterviewService.transcribeCurrentQuestion` | Gladia, `interview_answers` | transcript DTO | Transcript editor |
| Submit answer | `AiInterviewRoom` | `POST .../answer` | `AiInterviewService.confirmAnswer` | `interview_answers`, next question generation | session DTO | Next question/progress |
| Finish interview | `AiInterviewRoom` | `POST .../finish` | `AiInterviewService.finishInterview` | ShopAIKey feedback/summary, fallback, DB | completed session DTO | Score, feedback, history |
