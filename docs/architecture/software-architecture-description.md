# 1.1 Software Architecture

## 1.1.1 Architecture Overview

The Smart Recruitment Portal is implemented as a modular monolith. The user-facing application is a single React, Vite, and TypeScript SPA that serves public, Candidate, Employer, and Admin workflows through role-based routes. It communicates with the backend through HTTP REST APIs using JSON payloads, multipart form uploads, and bearer JWT authentication.

The backend is a Spring Boot 3.2 application organized around presentation controllers, security filters and handlers, application services, data access repositories, domain entities, and shared configuration or utility components. The backend persists business data in PostgreSQL through Spring Data JPA repositories and direct JDBC queries where reporting or billing operations require SQL-oriented access. Flyway manages schema evolution.

External integrations are isolated behind Spring services or gateway components. Google OAuth is handled through Spring Security OAuth2, email is sent through Spring Mail, AI interview and ranking logic calls ShopAIKey and Gladia clients, media upload for company assets uses Cloudinary, and subscription checkout uses PayOS, MoMo, VNPay, or a bank-transfer/VietQR flow. Candidate CV and avatar files are stored on the local filesystem through a storage abstraction.

The main security mechanism is stateless JWT authentication with role-based authorization rules enforced by Spring Security. Google OAuth accounts are linked to internal users and receive the same JWT-based session model after login.

## 1.1.2 Architecture Components

### Web Browser

- Type: client runtime
- Responsibility: Runs the SPA, captures user input, microphone audio, and browser speech events.
- Main functions: Render UI, perform navigation, use MediaRecorder and Web Speech APIs for the AI interview experience.
- Technologies used: Modern browser APIs.
- Communicates with: Web Frontend and backend speech/audio endpoints.
- Communication protocol or message: User interaction, media capture, HTTP requests through the frontend.
- Relevant source code: `frontend/src/App.tsx`, `frontend/src/hooks/useVoiceConversation.ts`, `frontend/src/types/webSpeech.d.ts`.

### Smart Recruitment Portal Web Frontend

- Type: frontend module
- Responsibility: Provides the public site and role-based Candidate, Employer, and Admin user interfaces.
- Main functions: Authentication screens, job search, candidate profile and CV management, applications, employer company and job management, admin review and billing screens, AI interview UI.
- Technologies used: React 18, Vite, TypeScript, React Router, Redux Toolkit, Axios, Tailwind CSS, Framer Motion.
- Communicates with: Backend API / Presentation Layer.
- Communication protocol or message: HTTPS REST, JSON, multipart file uploads, bearer JWT tokens.
- Relevant source code: `frontend/package.json`, `frontend/src/App.tsx`, `frontend/src/services/api.ts`, `frontend/src/store/store.ts`, `frontend/src/pages/admin`, `frontend/src/pages/Employer`, `frontend/src/pages/billing`.

### API / Presentation Layer

- Type: backend layer
- Responsibility: Exposes REST endpoints and translates HTTP requests into service calls.
- Main functions: Auth, public jobs/categories/settings, candidate APIs, employer APIs, applications, AI interview APIs, billing APIs, admin APIs, payment callbacks.
- Technologies used: Spring Web MVC, validation annotations, multipart support.
- Communicates with: Security layer, application services, frontend, payment gateway callbacks.
- Communication protocol or message: REST JSON, multipart/form-data, callback query/body payloads.
- Relevant source code: `backend/src/main/java/com/sjp/recruitment/controller`.

### Authentication and Authorization

- Type: security component
- Responsibility: Enforces stateless authentication, role-based access, CORS, OAuth login, and maintenance restrictions.
- Main functions: JWT extraction and validation, Spring Security route rules, Google OAuth success handling, password hashing.
- Technologies used: Spring Security, OAuth2 Client, JJWT.
- Communicates with: Frontend, API controllers, Google OAuth, AuthService.
- Communication protocol or message: Bearer JWT, OAuth 2.0 / OIDC profile data.
- Relevant source code: `backend/src/main/java/com/sjp/recruitment/config/SecurityConfig.java`, `JwtAuthenticationFilter.java`, `OAuth2LoginSuccessHandler.java`, `GoogleOAuthConfig.java`, `PasswordConfig.java`, `backend/src/main/java/com/sjp/recruitment/util/JwtUtil.java`.

### Application / Service Layer

- Type: backend layer
- Responsibility: Implements the portal's business use cases.
- Main functions: Account lifecycle, candidate profiles and CVs, employer companies and jobs, job search and recommendations, application workflow, interview scheduling and offers, admin review, settings, subscriptions, notifications, AI interview sessions, CV ranking.
- Technologies used: Spring services, transactions, Spring Mail, Apache Tika, RestClient, RestTemplate, Java HTTP client.
- Communicates with: API controllers, data access layer, storage components, external integrations.
- Communication protocol or message: Java service calls, transactions, AI prompts, email messages, payment requests.
- Relevant source code: `backend/src/main/java/com/sjp/recruitment/service`, `backend/src/main/java/com/sjp/recruitment/payment`, `backend/src/main/java/com/sjp/recruitment/scheduler`.

### Data Access Layer

- Type: backend layer
- Responsibility: Reads and writes persistent application data.
- Main functions: Repository-based CRUD and query methods, direct SQL for billing, admin reporting, settings, feature limits, and scheduled updates.
- Technologies used: Spring Data JPA, Hibernate, NamedParameterJdbcTemplate.
- Communicates with: Service layer and PostgreSQL.
- Communication protocol or message: SQL through JPA/Hibernate and JDBC.
- Relevant source code: `backend/src/main/java/com/sjp/recruitment/repository`, JDBC usage in `BillingService.java`, `AdminService.java`, `AdminOpsService.java`, `JobService.java`, `JobReportService.java`, `SystemSettingsService.java`, `FeatureLimitService.java`.

### Shared / Common Components

- Type: backend shared components
- Responsibility: Provides cross-cutting behavior used by several modules.
- Main functions: DTO mapping, centralized exception handling, storage abstraction, configuration properties, async configuration, data seeding.
- Technologies used: Spring configuration, Lombok, Spring validation, Spring resource abstraction.
- Communicates with: Controllers, services, local file storage.
- Communication protocol or message: Java object calls and filesystem access.
- Relevant source code: `backend/src/main/java/com/sjp/recruitment/service/DtoMapper.java`, `backend/src/main/java/com/sjp/recruitment/exception`, `backend/src/main/java/com/sjp/recruitment/config`, `backend/src/main/java/com/sjp/recruitment/service/storage`.

### PostgreSQL

- Type: database / data store
- Responsibility: Stores users, profiles, jobs, applications, interviews, AI feedback, ranking results, subscriptions, payments, settings, notifications, audit logs, and token records.
- Main functions: Transactional persistence and reporting queries.
- Technologies used: PostgreSQL 15, Flyway migrations.
- Communicates with: Data access layer.
- Communication protocol or message: SQL / JDBC / JPA.
- Relevant source code: `docker-compose.yml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration`, `database/migrations/V1__initial_schema.sql`.

### Local File Storage

- Type: data store
- Responsibility: Stores uploaded candidate CV files and user avatars.
- Main functions: Save uploaded files under owner-specific directories and load CV files for download or AI ranking.
- Technologies used: Java NIO filesystem, Spring Resource.
- Communicates with: StorageService, CandidateService, AuthService, AiRankingService.
- Communication protocol or message: Filesystem read/write.
- Relevant source code: `backend/src/main/java/com/sjp/recruitment/service/storage/LocalStorageService.java`, `backend/src/main/java/com/sjp/recruitment/service/storage/StorageService.java`, `backend/src/main/resources/application.yml`.

### External Systems

- Type: external systems
- Responsibility: Provide identity, email, AI, speech-to-text, media storage, payment, and QR generation capabilities.
- Main functions: Google login, SMTP email delivery, AI interview/ranking responses, audio transcription, company media upload, subscription checkout and callbacks, VietQR image generation.
- Technologies used: Google OAuth 2.0 / OIDC, SMTP, ShopAIKey API, Gladia API, Cloudinary API, PayOS, MoMo, VNPay, VietQR.
- Communicates with: Security layer, AuthService, EmailService, AI services, EmployerService, BillingService, payment callback controllers.
- Communication protocol or message: OAuth 2.0 / OIDC, SMTP, HTTPS JSON, multipart upload, payment webhooks and IPN callbacks.
- Relevant source code: `GoogleOAuthConfig.java`, `OAuth2LoginSuccessHandler.java`, `EmailService.java`, `ShopAiKeyClient.java`, `GladiaTranscriptionClient.java`, `EmployerService.java`, `PayOsPaymentGateway.java`, `MomoPaymentGateway.java`, `VnPayPaymentGateway.java`, `BillingService.java`.

## 1.1.3 Communication and Data Flow

1. User authentication flow: Users authenticate through `/auth/login` or Google OAuth. Local login validates credentials in `AuthService` and returns a JWT. Google OAuth uses Spring Security OAuth2, links or creates an OAuth account, then redirects to the frontend with a JWT or a role-selection token.

2. Standard frontend-to-backend API request flow: React service modules call Axios. The Axios interceptor attaches `Authorization: Bearer <token>` when a token exists. Spring Security validates the token, resolves the role, and forwards the request to REST controllers and services. Responses are returned as JSON.

3. Database access flow: Controllers call services, services call Spring Data JPA repositories or JDBC helpers, and those components execute SQL against PostgreSQL. Flyway migrations define the database schema.

4. File or CV upload flow: Candidate CV and avatar uploads arrive as multipart requests. `CandidateService` or `AuthService` validates the files and uses `StorageService` / `LocalStorageService` to write them to local upload directories. Employer company documents and logos are uploaded through `EmployerService` to Cloudinary, which returns media URLs.

5. AI processing flow: AI interview sessions and AI ranking use internal services to build prompts from job, candidate, CV, and interview context. ShopAIKey receives the prompt and returns JSON analysis, generated questions, answer feedback, session summaries, ranking results, or TTS audio.

6. Speech-to-text flow: The frontend can use browser Web Speech APIs for hands-free interaction. For uploaded interview audio, the backend sends the audio file to Gladia, starts a pre-recorded transcription job, polls for completion, and stores the returned transcript in the interview session.

7. Email or notification flow: Internal notifications are stored in PostgreSQL through repositories. EmailService sends verification, password reset, interview, rejection, reschedule, and offer emails through the configured SMTP provider.

## 1.1.4 External Systems

### Google OAuth 2.0 / OIDC

- Purpose: Enables Google-based sign-in and account linking.
- Data sent to it: OAuth authorization requests.
- Data received from it: Google user profile attributes such as email, subject identifier, and display name.
- Communication protocol: OAuth 2.0 / OpenID Connect through Spring Security OAuth2 Client.
- Internal component: `GoogleOAuthConfig`, `OAuth2LoginSuccessHandler`, `AuthService`.

### SMTP Email Provider

- Purpose: Sends account and recruitment workflow emails.
- Data sent to it: Recipient email, subject, and plain-text message body.
- Data received from it: Delivery success or JavaMail exceptions.
- Communication protocol: SMTP with STARTTLS by default.
- Internal component: `EmailService`.

### ShopAIKey AI / TTS API

- Purpose: Generates AI interview questions, evaluates answers, summarizes sessions, ranks CVs, and streams TTS audio.
- Data sent to it: Prompts containing job, candidate, interview, CV, and answer context.
- Data received from it: JSON analysis, generated questions, summaries, ranking scores, and audio streams.
- Communication protocol: HTTPS JSON API and HTTPS audio stream.
- Internal component: `ShopAiKeyClient`, `AiInterviewService`, `AiInterviewSpeechService`, `AiRankingService`.

### Gladia Speech-to-Text API

- Purpose: Converts uploaded interview audio into Vietnamese transcript text.
- Data sent to it: Multipart audio upload and transcription start request.
- Data received from it: Upload URL, transcription job id, transcription status, final transcript.
- Communication protocol: HTTPS multipart upload and JSON polling.
- Internal component: `GladiaTranscriptionClient`, `AiInterviewService`.

### Cloudinary Media Storage

- Purpose: Stores employer company documents and logos.
- Data sent to it: Multipart file bytes and upload metadata.
- Data received from it: Secure URL and public id.
- Communication protocol: Cloudinary Java SDK over HTTPS.
- Internal component: `CloudinaryConfig`, `EmployerService`.

### Payment and QR Systems

- Purpose: Supports paid subscription checkout and payment confirmation.
- Data sent to it: Payment amount, order id, return URL, IPN or webhook URL, signed parameters.
- Data received from it: Checkout URL, payment status, transaction id, webhook or IPN payloads, QR image URL.
- Communication protocol: HTTPS JSON, gateway redirect URLs, webhook/IPN callbacks, signed query parameters.
- Internal component: `BillingService`, `PayOsPaymentGateway`, `MomoPaymentGateway`, `VnPayPaymentGateway`, `PayOsCallbackController`, `MomoCallbackController`, `VnPayCallbackController`.

## 1.1.5 Architectural Rationale

The modular monolith architecture is suitable for the Smart Recruitment Portal because the main business capabilities share a single domain model, database, authentication model, and deployment unit. Separating controllers, services, data access, and shared infrastructure improves maintainability and testability while keeping development and deployment simple for an academic project.

Security is centralized in Spring Security so Candidate, Employer, and Admin role boundaries are enforced consistently. External integrations are wrapped by dedicated services and gateway classes, which makes payment providers, AI providers, SMTP settings, and media storage easier to configure or replace. PostgreSQL provides transactional consistency for recruitment workflows, while scheduled jobs handle recurring background maintenance. The frontend remains focused on user experience and delegates sensitive business operations to the backend.

## Traceability Table

| Diagram Component | Responsibility | Technology | Source Code Evidence |
|---|---|---|---|
| Web Browser | Runs SPA and browser audio/speech features | Browser APIs | `frontend/src/App.tsx`, `frontend/src/hooks/useVoiceConversation.ts`, `frontend/src/types/webSpeech.d.ts` |
| Web Frontend | Public, Candidate, Employer, Admin UI | React, Vite, TypeScript, React Router, Redux Toolkit, Axios | `frontend/package.json`, `frontend/src/App.tsx`, `frontend/src/services`, `frontend/src/pages` |
| API / Presentation Layer | Exposes REST endpoints | Spring Web MVC | `backend/src/main/java/com/sjp/recruitment/controller` |
| Authentication and Authorization | JWT, roles, OAuth, CORS, maintenance filtering | Spring Security, JJWT, OAuth2 Client | `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `JwtUtil.java`, `GoogleOAuthConfig.java`, `OAuth2LoginSuccessHandler.java` |
| User and Auth Management | Register, login, account, password reset, email verification, OAuth linking | Spring Service, JPA, JDBC, Mail | `AuthService.java`, `UserRepository.java`, `EmailVerificationTokenRepository.java`, `PasswordResetTokenRepository.java`, `OauthAccountRepository.java` |
| Candidate Management and CV | Candidate profile, CV upload/download, saved jobs, recommendations, notifications | Spring Service, JPA, local storage | `CandidateService.java`, `CandidateController.java`, `CandidateProfileRepository.java`, `CandidateCvRepository.java`, `StorageService.java` |
| Employer and Company Management | Company profile, locations, documents, logo, employer job/applicant views | Spring Service, JPA, Cloudinary | `EmployerService.java`, `EmployerController.java`, `CompanyRepository.java`, `CompanyDocumentRepository.java`, `CloudinaryConfig.java` |
| Job Management and Search | Public job search, employer job lifecycle, admin review, reports | Spring Service, JPA, JDBC | `JobService.java`, `JobController.java`, `AdminJobController.java`, `JobReportService.java`, `JobRepository.java` |
| Job Application and Interview Workflow | Application submission, status timeline, interview scheduling, offers | Spring Service, JPA | `ApplicationService.java`, `ApplicationWorkflowService.java`, `ApplicationController.java`, `ApplicationWorkflowController.java`, `InterviewScheduleRepository.java`, `JobOfferRepository.java` |
| AI Interview Coach | AI sessions, questions, transcript, answer feedback, summaries, TTS tickets | Spring Service, ShopAIKey, Gladia | `AiInterviewService.java`, `AiInterviewController.java`, `AiInterviewSpeechService.java`, `ShopAiKeyClient.java`, `GladiaTranscriptionClient.java` |
| Candidate Matching and Ranking | Rule-based matching and AI CV ranking | Spring Service, Apache Tika, ShopAIKey | `MatchingService.java`, `AiRankingService.java`, `AiRankingScheduler.java`, `AiRankingResultRepository.java` |
| Billing and Subscription | Plans, checkout, feature limits, payment callbacks | Spring Service, JDBC, PayOS, MoMo, VNPay, VietQR | `BillingService.java`, `BillingController.java`, `AdminBillingController.java`, `PayOsPaymentGateway.java`, `MomoPaymentGateway.java`, `VnPayPaymentGateway.java`, `FeatureLimitService.java` |
| Notification and Email | Internal notifications and external email messages | JPA, Spring Mail | `NotificationRepository.java`, `EmailService.java`, `ApplicationService.java`, `ApplicationWorkflowService.java`, `JobService.java` |
| Admin and System Settings | Admin dashboard, user/company/job review, billing administration, settings, audit logs | Spring Service, JDBC, JPA | `AdminService.java`, `AdminOpsService.java`, `SystemSettingsService.java`, `AdminDashboardController.java`, `AdminUserController.java`, `AdminController.java`, `AdminSettingsController.java` |
| Scheduled Processing | Expire jobs/subscriptions, fix job reports, process AI ranking queue | Spring Scheduling | `RecruitmentPortalApplication.java`, `JobExpirationScheduler.java`, `SubscriptionExpirationScheduler.java`, `JobReportFixDeadlineScheduler.java`, `AiRankingScheduler.java` |
| Data Access Layer | Persistent data access | Spring Data JPA, Hibernate, NamedParameterJdbcTemplate | `backend/src/main/java/com/sjp/recruitment/repository`, JDBC usage in service classes |
| PostgreSQL | Primary transactional database | PostgreSQL 15, Flyway | `docker-compose.yml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration` |
| Local File Storage | Candidate CV and avatar file storage | Java NIO filesystem, Spring Resource | `LocalStorageService.java`, `StorageService.java`, `backend/uploads/cvs`, `application.yml` storage settings |
| External Systems | OAuth, email, AI, STT, media storage, payments, QR generation | Google OAuth, SMTP, ShopAIKey, Gladia, Cloudinary, PayOS, MoMo, VNPay, VietQR | `application.yml`, `.env.example`, integration service and gateway classes |

## Notes on Verification and Uncertainties

No WebSocket, cache, search engine, or independent microservice implementation was found in the current source tree. The frontend contains `assessmentService.ts` and `interviewService.ts`, but matching backend controllers for `/assessments` and non-`/v1` `/interviews` endpoints were not found, so those endpoints are not represented as implemented architectural components in the diagram.
