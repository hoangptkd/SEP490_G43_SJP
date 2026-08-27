# Design Constraints

## Logic Not To Change
- Do not change current route paths.
- Preserve JWT auth storage: `token`, `user`, `role` in `localStorage`.
- Preserve Axios Bearer token behavior and 401 redirects.
- Preserve login redirects by role.
- Preserve admin login restriction: only returned `ADMIN` users enter `/admin`.
- Preserve employer verification gating before job creation/edit workflows.
- Preserve admin approve/reject workflows and required rejection reason.
- Preserve candidate AI interview flow: config, eligible applications, practice/application sessions, question room, audio upload/transcription, transcript editing, answer submit, skip, retry feedback, retry summary, fallback notices.
- Preserve backend endpoint names and payload shapes.
- Preserve file constraints shown in UI: legal docs PDF/JPG/PNG up to 10MB; multipart CV/logo/docs/audio uploads.

## Fields That Must Remain Visible
- Public job card: title, company, location, experience, salary, match/saved/applied indicators.
- Job detail: company, title, location, experience, salary, description, requirements, match score, save/apply controls.
- Candidate profile: full name, phone, location, skills, bio, apply readiness.
- Candidate CVs: original file name, default status/action, delete action, CV version title/template/updated date.
- Candidate applications: job, company, status, timeline on detail.
- Candidate AI: question text, progress, transcript, feedback score/text, strengths, weaknesses, suggestions, summary score, AI practice disclaimer.
- Candidate subscription: plan code/name/status/price, benefits, saved jobs count, CV count, unread notification count.
- Company profile: name, logo, website, industry, location/HQ, size, tax code, description, verification status, branches.
- Company verification: verification status, document name/type/status/uploadedAt/reject reason, view/download/delete actions.
- Employer jobs: all job form fields, title, status, location, salary, vacancies, views, deadline, skills, rejection reason.
- Admin company review: company list, owner identity, pending document count, company fields, owner fields, document rows, approve/reject reason.
- Admin job review: job list, company, location, salary, job detail, employer identity, approve/reject reason.
- Admin profile: email, role, status, email verified.

## Actions That Must Remain
- Login, register, verify email, complete OAuth role.
- Browse/filter jobs and open job detail.
- Save/unsave job and apply with CV.
- Candidate profile update, CV upload/default/delete, CV version creation.
- View applications and details.
- Mark notification read.
- AI session create/open/delete, record/transcribe audio, edit transcript, submit, skip, retry feedback, retry summary.
- Employer logo/profile update, location CRUD, document upload/view/download/delete, job CRUD, draft save, submit review, resubmit rejected job.
- Admin approve/reject companies and jobs with rejection reason.
- Logout from candidate/employer/admin layouts.

## Role Permissions
- Guest: public jobs and auth only.
- Candidate: intended candidate-only; AI interview is explicitly backend candidate-only.
- Employer: intended employer-only; company/job data tied to current employer.
- Admin: frontend requires token plus stored role `ADMIN`.
- Candidate/employer route guards currently check token only. Do not imply stronger implemented frontend enforcement.

## Backend Limitations
- Public backend access is auth/OAuth/health plus GET jobs/categories.
- Admin dashboard metrics, users, statistics, and settings have no frontend API implementation.
- No chart library or chart API is wired.
- `interviewService` and `assessmentService` exist but no routes use them.
- Backend role enforcement outside AI/admin frontend guard needs verification.

## Frontend Technical Limitations
- Many screens are inline in `App.tsx`.
- Common primitives exist but are not widely used.
- No icon or chart library installed.
- Installed form/validation libraries are not used in reviewed screens.
- Responsive behavior mainly uses one breakpoint at 900px plus flex wrapping.
- Employer/Admin pages contain substantial inline styles.
- Some source Vietnamese text appears mojibake; source copy cleanup is outside this documentation task.

## Can Be Fully Redesigned Visually
- Public job list/detail presentation.
- Auth page presentation.
- Candidate dashboard/profile/CV/saved/application/notification/subscription/AI layouts.
- Employer profile/location/verification/jobs presentation.
- Admin company/job review presentation.
- Status badges, cards, tables, loading, error, empty states.
- Placeholder admin/employer screens, while clearly keeping them placeholder.

## Presentation Only
- API contracts and endpoint usage.
- Status enum meaning.
- Verification and review workflow logic.
- Required fields and file validation.
- Auth storage keys and redirect behavior.
- AI interview state machine and browser microphone dependency.
