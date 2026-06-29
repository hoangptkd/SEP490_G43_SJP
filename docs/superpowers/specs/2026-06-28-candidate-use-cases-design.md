# Candidate Use Cases Design

Date: 2026-06-28
Project: Smart Recruitment Portal
Source: Report 3 - SRS - Smart Recruitment Portal.docx

## 1. Scope

This design covers Candidate-facing use cases close to the SRS scope, excluding the virtual AI interview feature.

Included:

- Email/password registration and login.
- Email verification through real SMTP.
- Google OAuth2 redirect login with role selection for new users.
- Candidate profile management.
- CV upload and CV builder basic versioning.
- Public job board and Candidate-aware job details.
- Saved jobs/bookmarks.
- Rule-based job recommendations.
- Job application submission.
- Candidate application tracking with timeline.
- Candidate notifications.
- Basic read-only subscription view.

Excluded from this phase:

- AI mock interview, interview history, and interview progress tracking.
- Payment checkout and payment gateway callback.
- Full Employer dashboard and Employer application review UI.
- Full Admin panel.
- Job category management.

## 2. Implementation Strategy

Use vertical slices so each flow can be tested and demoed independently:

1. Auth full-stack.
2. Candidate profile and CV full-stack.
3. Job board, saved jobs, and recommendations.
4. Apply flow, application tracking, and notifications.
5. Basic subscription view.

This keeps the product demoable throughout development and reduces the risk of building a large backend or UI surface before validating integration.

## 3. Auth And Account Verification

### Email Registration

The user registers with email, password, and role.

Backend behavior:

- Validate unique email.
- Hash password with BCrypt.
- Create user with `emailVerified=false` and `status=PENDING_VERIFICATION`.
- Create an email verification token with a configurable TTL.
- Send verification email through `EmailService`.
- Block login until the email is verified.

Email configuration starts with Gmail SMTP and is abstracted so SendGrid can replace it later.

Environment variables:

- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `EMAIL_VERIFICATION_TOKEN_TTL_MINUTES`

### Email Verification

The email link points to the frontend:

```text
FRONTEND_BASE_URL/verify-email?token=<token>
```

The frontend calls the backend verification API. Backend validates token existence, expiry, and unused state, then marks the user as active:

- `emailVerified=true`
- `status=ACTIVE`

### Email Login

Only active verified users can log in. Invalid credentials and unverified email must return user-safe errors with no technical details.

Successful login returns:

- JWT access token.
- Safe user DTO.

The API must never expose `passwordHash`.

### Google OAuth2 Redirect Login

Use Spring Security OAuth2 redirect flow.

Flow:

1. Frontend navigates to backend OAuth endpoint.
2. Backend redirects to Google.
3. Google redirects to backend callback.
4. Backend resolves Google profile email.
5. If email already belongs to an active user, backend issues JWT and redirects to frontend OAuth callback.
6. If email does not exist, backend creates a short-lived role-selection token and redirects to:

```text
FRONTEND_BASE_URL/select-role?token=<temporary-token>
```

7. User selects Candidate or Employer.
8. Frontend calls backend to complete account creation.
9. Google-created account is treated as email verified.

Environment variables:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `FRONTEND_BASE_URL`
- `BACKEND_BASE_URL`
- `OAUTH_ROLE_SELECTION_TOKEN_TTL_MINUTES`

In this Candidate phase, the Employer role may be created but Employer-specific flows remain out of scope.

## 4. Candidate Profile And CV

### Profile

Candidate can manage:

- Full name.
- Phone.
- Location.
- Skills.
- Education.
- Work experience.
- Projects.
- Certifications.
- Summary or bio.

Minimum profile requirement before applying:

- Full name.
- Phone.
- Location.
- At least one skill.
- At least one valid CV.

### CV Upload

Rules:

- PDF only.
- Maximum 5 MB.
- Store files through `StorageService`.
- Initial implementation uses local filesystem storage.
- Storage abstraction must allow future replacement with S3, MinIO, Cloudinary, or similar object storage.

Suggested local storage key:

```text
uploads/cvs/{candidateId}/{uuid}.pdf
```

CV metadata stored in DB:

- Candidate profile.
- Original file name.
- Storage key/path.
- Size.
- Content type.
- Upload timestamp.
- Default flag.
- Deleted flag if soft delete is needed.

Candidate actions:

- Upload CV.
- View CV list.
- Set default CV.
- Delete CV if unused.
- Soft-delete CV if already used by an application.

Deletion rule:

- If a CV has never been used in an application, it may be hard deleted.
- If a CV has already been submitted with an application, it must not be hard deleted. The CV is marked deleted/hidden for the Candidate's active CV list, while historical applications keep their submitted CV reference intact.

### CV Builder Basic

Candidate can create CV versions from profile data.

This phase includes:

- CV version title.
- One initial template key.
- HTML preview in frontend.
- Snapshot JSON of profile sections.
- Ability to select a builder CV version when applying.

This phase does not include:

- Drag-and-drop section reorder.
- Multiple polished templates.
- PDF export from builder.
- Automatic skill extraction from uploaded PDF.

## 5. Job Discovery, Saved Jobs, And Recommendation

### Public Job Board

Routes:

- `/jobs`
- `/jobs/:id`

Guest users can search and view public job postings.

Logged-in Candidates additionally see:

- Saved state.
- Applied state.
- Save/unsave action.
- Apply action.

### Search, Filter, Sort

Filters:

- Keyword.
- Location.
- Salary min/max.
- Experience level.
- Skills or tech stack.

Sort:

- Relevance or match score for logged-in Candidate.
- Newest.
- Salary high-to-low.
- Deadline soon.

Pagination is required.

Category is intentionally deferred.

### Saved Jobs

Add saved job support:

- Candidate can save a job.
- Candidate can unsave a job.
- Candidate can view saved jobs at `/candidate/saved-jobs`.
- Database enforces unique `(candidate_id, job_id)`.

### Rule-Based Job Recommendations

Endpoint:

```text
GET /candidate/recommendations/jobs
```

Recommendation score uses:

- Skill overlap between candidate skills and job requirements/skills.
- Location match.
- Experience fit.

Response includes:

- Job summary.
- Match score from 0 to 100.
- Matched skills.
- Missing skills.
- Short reason text.

If profile data is incomplete, the system still returns fallback jobs and warns that completing the profile improves recommendation quality.

### Seed Data

Because full Employer job management is out of scope, seed demo data for:

- Companies.
- Employer accounts.
- Job postings.
- Job skills/requirements.
- Salary ranges.
- Experience levels.
- Deadlines.
- Active/closed statuses.
- Candidate demo account with profile and CV metadata.
- Candidate application demo records across multiple statuses.

Seeded Candidate tracking data must include at least:

- Application 1: `SUBMITTED`
- Application 2: `UNDER_REVIEW`
- Application 3: `INTERVIEW_SCHEDULED`
- Application 4: `REJECTED`
- Application 5: `ACCEPTED`

## 6. Application Submission And Tracking

### Apply Flow

Candidate applies from job detail.

Backend must derive the Candidate from JWT, not from a client-provided `candidateId`.

Apply preconditions:

- User is authenticated.
- User has Candidate role.
- Email is verified and user is active.
- Candidate profile meets minimum apply requirement.
- Selected CV belongs to the current Candidate.
- Job is active/published.
- Job is not expired.
- Candidate has not already applied to the same job.

On success:

- Create application.
- Store selected CV reference or builder CV version reference.
- Set initial status to `SUBMITTED`.
- Create first timeline event.
- Create notification for the Candidate.

### Application Statuses

Use the SRS state-machine direction:

- `SUBMITTED`
- `UNDER_REVIEW`
- `INTERVIEW_SCHEDULED`
- `INTERVIEWED`
- `EVALUATED`
- `ACCEPTED`
- `REJECTED`
- `HIRED`

This replaces the earlier skeleton status set where needed.

Candidate permission rule:

- Candidate can submit an application.
- Candidate can view only their own applications and timelines.
- Candidate must not create, edit, or update application status after submission.
- Status updates are reserved for Employer/Admin/system flows in later phases or seed data for demo.

### Candidate Tracking UI

Routes:

- `/candidate/applications`
- `/candidate/applications/:id`

List item shows:

- Job title.
- Company.
- Applied date.
- CV used.
- Current status.
- Last updated.
- Next step if available.

Detail page shows:

- Job summary.
- Application metadata.
- Current status.
- Timeline.
- Public notes.

### Timeline

Add `application_status_history`.

Fields:

- Application.
- From status.
- To status.
- Actor user, nullable for system-created events.
- Public note, nullable.
- Created at.

Candidate sees only public notes. Employer internal notes are reserved for the future Employer phase.

## 7. Notifications

Add notifications for Candidate.

Fields:

- Recipient user.
- Type.
- Title.
- Message.
- Read/unread state.
- Related entity type.
- Related entity id.
- Created at.

Create notifications when:

- Application is submitted successfully.
- Application status changes.

Frontend route:

- `/candidate/notifications`

Candidate workspace navigation should show unread count.

## 8. Subscription View Basic

Route:

- `/candidate/subscription`

This phase is read-only and may use seed data.

Display:

- Current plan.
- Status.
- Expiration date if any.
- Benefits.
- Usage/quota.
- Premium or AI interview benefits marked as coming soon or disabled.

No payment, checkout, upgrade, cancel, or gateway callback is included in this phase.

Core Candidate features are not blocked by subscription in this phase.

## 9. Database Direction

The target database name is:

```text
smart_recruitment_portal
```

The database may be reset because no important data needs to be preserved.

Suggested table groups:

- `users`
- `email_verification_tokens`
- `oauth_role_selection_tokens`
- `candidate_profiles`
- `candidate_cvs`
- `cv_versions`
- `companies`
- `jobs`
- `saved_jobs`
- `applications`
- `application_status_history`
- `notifications`
- `plans`
- `subscriptions`

The migration must not contain secrets.

## 10. API Direction

Auth:

- `POST /auth/register`
- `POST /auth/verify-email`
- `POST /auth/login`
- `GET /auth/me`
- `POST /auth/logout`
- OAuth2 routes managed by Spring Security.
- `POST /auth/oauth/complete-role`

Candidate profile and CV:

- `GET /candidate/profile`
- `PUT /candidate/profile`
- `GET /candidate/cvs`
- `POST /candidate/cvs`
- `PATCH /candidate/cvs/{id}/default`
- `DELETE /candidate/cvs/{id}`
- `GET /candidate/cv-versions`
- `POST /candidate/cv-versions`
- `GET /candidate/cv-versions/{id}`
- `PUT /candidate/cv-versions/{id}`
- `DELETE /candidate/cv-versions/{id}`

Jobs:

- `GET /jobs`
- `GET /jobs/{id}`
- `GET /candidate/recommendations/jobs`

Saved jobs:

- `GET /candidate/saved-jobs`
- `POST /candidate/saved-jobs/{jobId}`
- `DELETE /candidate/saved-jobs/{jobId}`

Applications:

- `POST /candidate/applications`
- `GET /candidate/applications`
- `GET /candidate/applications/{id}`

Notifications:

- `GET /candidate/notifications`
- `PATCH /candidate/notifications/{id}/read`
- `PATCH /candidate/notifications/read-all`

Subscription:

- `GET /candidate/subscription`

## 11. Frontend Direction

Public routes:

- `/`
- `/login`
- `/register`
- `/verify-email`
- `/oauth/callback`
- `/select-role`
- `/jobs`
- `/jobs/:id`

Candidate routes:

- `/candidate`
- `/candidate/profile`
- `/candidate/cvs`
- `/candidate/saved-jobs`
- `/candidate/applications`
- `/candidate/applications/:id`
- `/candidate/notifications`
- `/candidate/subscription`

UI language is Vietnamese first. Text should be structured so future i18n is practical.

The public job board and Candidate workspace are separate but connected. Public job pages become Candidate-aware when the user is logged in.

## 12. Security And Error Handling

Security requirements:

- JWT required for protected APIs.
- RBAC enforced at API layer.
- Candidate APIs derive user identity from JWT.
- Passwords stored with BCrypt.
- Password hash never exposed in API responses.
- CV upload validates PDF content type and maximum 5 MB size.
- SMTP, OAuth, JWT, and database secrets stay in `.env`.

Error response shape:

```json
{
  "message": "Email chua duoc xac minh",
  "code": "EMAIL_NOT_VERIFIED",
  "timestamp": "2026-06-28T00:00:00Z"
}
```

Frontend displays human-readable messages and must not show stack traces, SQL details, or internal exception names.

## 13. Testing And Acceptance

Backend checks:

- `mvn test`

Frontend checks:

- `npm run build`

Manual smoke flows:

1. Register with email and password.
2. Verify email.
3. Log in.
4. Google OAuth login for existing user.
5. Google OAuth login for new user and role selection.
6. Update Candidate profile.
7. Upload valid PDF CV.
8. Reject non-PDF or oversized CV.
9. Search jobs.
10. Save and unsave job.
11. View recommendations.
12. Apply to active job with valid CV.
13. Prevent duplicate application.
14. Prevent apply with incomplete profile.
15. View application list and detail timeline.
16. View and mark notifications as read.
17. View subscription page.

Acceptance criteria:

- Candidate can complete the main flow from account creation to job application tracking.
- Unverified users cannot log in.
- Protected Candidate APIs reject missing or invalid JWT.
- Candidate cannot access or mutate another Candidate's data.
- Secrets are not committed to Git.

## 14. Open Decisions Deferred

These are intentionally deferred and not blockers for this phase:

- SendGrid implementation.
- AI provider integration.
- PDF export from CV builder.
- CV parsing/skill extraction.
- Job category management.
- Full Employer dashboard.
- Payment gateway integration.
- Virtual AI interview.
