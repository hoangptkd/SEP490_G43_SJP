# Screen Details

## Public and Auth Screens

### PUB-001 Root Redirect
- Target user: Anyone.
- Goal: Enter public job discovery.
- Main content/actions/forms/tables/dialogs: None.
- Data/loading/empty/error: None.
- Permissions: Public.
- Navigation: Redirects to `/jobs`.
- Mobile considerations: None.

### AUTH-001 User Login
- Target user: Guest or returning user.
- Goal: Login and enter the right portal.
- Main content: Email/password form, Google OAuth link when enabled, disabled OAuth message when not enabled, demo credential note.
- Main actions: Submit login; open Google OAuth.
- Form fields: email, password.
- Data shown: OAuth enabled state and error messages.
- Loading state: No explicit submit loading state.
- Empty state: Not applicable.
- Error state: Inline backend/OAuth error.
- Permissions: Public; redirect by returned role.
- Navigation: `ADMIN -> /admin`, `EMPLOYER -> /employer`, `CANDIDATE -> /candidate`, fallback `/jobs`.
- Mobile: Single-column auth panel.

### AUTH-002 Register
- Target user: Guest.
- Goal: Create a candidate or employer account.
- Main content/actions: Registration form and submit.
- Form fields: email, password, role select (`CANDIDATE`, `EMPLOYER`).
- Data shown: success or error message.
- Loading state: No explicit loading.
- Empty state: Not applicable.
- Error state: Inline backend error.
- Permissions: Public; admin registration not exposed.
- Navigation: Stays on page after success.
- Mobile: Single-column auth panel.

### AUTH-003 Verify Email
- Target user: User with verification token.
- Goal: Confirm account email.
- Main content: Verification status message and login link.
- Request data: `token` query parameter.
- Loading state: Initial verifying message.
- Error state: Missing token or backend error as message.
- Navigation: `/login`.

### AUTH-004 OAuth Callback
- Target user: OAuth return user.
- Goal: Complete Google login.
- Request data: `token` query parameter.
- Data shown: Processing or error message.
- Loading state: Processing message.
- Error state: Inline error and delayed redirect to `/login`.
- Navigation: candidate/employer routes by `/auth/me` role, otherwise `/jobs`.

### AUTH-005 OAuth Role Selection
- Target user: OAuth user without role.
- Goal: Choose candidate or employer role.
- Main actions: Select Candidate or Employer.
- Request data: query `token`, selected role.
- Error state: Missing token or backend error.
- Navigation: `/candidate` or `/employer`.

## Public Job Screens

### PUB-002 Public Job List
- Target user: Guest and authenticated users.
- Goal: Search and browse jobs.
- Main content: Filter sidebar and job card list.
- Main actions: Apply filters; open job detail.
- Filters: keyword, location, skills, experience level (`INTERN`, `FRESHER`, `JUNIOR`), sort (`newest`, `relevance`, `salary`, `deadline`).
- Cards/statistics: Job card with title, company, location, experience, salary range, match score, saved/applied chips.
- Dialogs: None.
- Data shown: `Job[]` from paged response.
- Loading state: No explicit loading indicator.
- Empty state: Empty list without message.
- Error state: Inline error in filter panel.
- Permissions: Public GET `/jobs`.
- Navigation: `/jobs/:id`.
- Mobile: filter/list grid collapses to one column under 900px.

### PUB-003 Job Detail
- Target user: Guest and candidate.
- Goal: Understand job, save it, and apply with a CV.
- Main content: company, title, location, experience, salary, description, requirements, match score, apply panel.
- Main actions: save/unsave, choose CV, apply, login to apply.
- Form fields: CV select.
- Data shown: `Job`, and `CvFile[]` if token exists.
- Loading state: loading text while job is null.
- Empty state: no explicit not-found state. Needs verification.
- Error state: apply/save errors shown as message; CV load failure silently produces empty CV list.
- Permissions: public can view; token required for save/apply UI. Role check for candidate apply UI needs verification.
- Navigation: `/login` for guest CTA.
- Mobile: detail/apply grid collapses to one column.

## Candidate Screens

### CAN-001 Candidate Dashboard
- Goal: See recommended jobs.
- Main content/actions: first four recommendation job cards; open job detail.
- Data shown: `Recommendation[].job`.
- Loading state: none explicit.
- Empty state: empty list without message.
- Error state: errors swallowed and list set empty.
- Permissions: intended candidate-only; frontend guard checks token only.
- Mobile: candidate shell stacks under 900px.

### CAN-002 Candidate Profile
- Goal: Update candidate profile.
- Main content: profile form and apply-ready status.
- Main actions: save profile.
- Form fields: fullName, phone, location, skills comma-separated, bio. Domain type also supports education, workExperience, projects, certifications; visible UI for those needs verification.
- Data shown: `CandidateProfile.applyReady`.
- Loading state: loading text while profile is null.
- Error state: save error handling needs verification.
- Permissions: intended candidate-only.
- Mobile: two-column form collapses.

### CAN-003 Candidate CVs
- Goal: Manage CV files and generated CV versions.
- Main actions: upload CV, set default CV, delete CV, create CV builder version.
- Form fields: file input.
- Table/list fields: original file name, default marker/action, delete action, version title/template/updated date.
- Data shown: `CvFile[]`, `CvVersion[]`.
- Loading/empty/error: minimal; exact empty text needs verification.
- Permissions: intended candidate-only.
- Mobile: table rows collapse.

### CAN-004 Saved Jobs
- Goal: Revisit saved jobs.
- Main content/actions: job cards, open job detail.
- Data shown: `Job[]`.
- Loading state: none explicit.
- Empty/error: no explicit states found.
- Permissions: intended candidate-only.

### CAN-005 Applications
- Goal: Track submitted applications.
- Main content/actions: application rows link to detail.
- Table fields: job, company, status pill, timestamps need verification.
- Data shown: `CandidateApplication[]`.
- Loading/empty/error: minimal; needs verification.
- Permissions: intended candidate-only.
- Navigation: `/candidate/applications/:id`.

### CAN-006 Application Detail
- Goal: View one application and timeline.
- Main content: application detail and timeline.
- Data shown: job, status, CV/CV version needs verification, timeline entries with `fromStatus`, `toStatus`, `publicNote`, `createdAt`.
- Loading state: loading text while application null.
- Empty/error: none explicit.
- Permissions: intended owner candidate-only; backend ownership needs verification.

### CAN-007 AI Interviews
- Goal: Create, run, and review AI interview practice.
- Main content: config/status notice, segmented creation modes, eligible applications, practice form, session history, interview room, result summary, question history.
- Main actions: create session from application, create practice session, open/resume session, delete/hide session, record audio, transcribe, submit answer, skip, retry feedback, retry summary, toggle continuous voice, toggle auto-submit.
- Form fields: target role, skills, optional active job, transcript textarea, auto-submit checkbox.
- Filters/tabs: application mode and practice mode.
- Data shown: `AiInterviewConfig`, eligible applications, active jobs, sessions, questions, answers, transcript, feedback, summary.
- Loading state: AI loading text; busy text during transcription/feedback/skip/retry.
- Empty state: no eligible applications and no sessions messages are implemented.
- Error state: inline role/error notices, microphone errors, fallback feedback/summary notices.
- Permissions: frontend token-only; backend `hasRole('CANDIDATE')`.
- Mobile: AI layout and room collapse to one column.

### CAN-008 Notifications
- Goal: Read notifications and mark them read.
- Main content/actions: notification list with mark-read button.
- Table fields: title, message, read state/action.
- Data shown: `NotificationItem[]`.
- Loading/empty/error: minimal; needs verification.
- Permissions: intended candidate-only.

### CAN-009 Subscription
- Goal: View plan and usage.
- Main content: plan, benefits, counts.
- Cards/statistics: saved jobs count, CV count, unread notification count.
- Data shown: `SubscriptionView`.
- Loading state: loading text while subscription null.
- Empty/error: none explicit.
- Permissions: intended candidate-only.

## Employer Screens

### EMP-001 Employer Dashboard
- Goal: Employer landing.
- Current status: placeholder/minimal content card.
- Main API/actions/data: none found.
- Permissions: intended employer-only; frontend guard checks token only.

### EMP-002 Company Profile
- Goal: Maintain company profile and logo.
- Main content: company banner, logo/avatar, verification badge, company form, branch preview.
- Main actions: upload logo, save profile, navigate to locations.
- Form fields: name required, website, industry/category, head office location, company size, tax code, description.
- Data shown: `Company`, `Category[]`, locations included on company.
- Loading state: loading text.
- Empty state: company missing error card.
- Error state: inline load/save/logo upload errors.
- Permissions: intended employer-only.
- Mobile: flex wrapping and form collapse.

### EMP-003 Company Locations
- Goal: Manage branches/work locations.
- Main actions: add, edit, delete non-headquarter location, cancel form.
- Form fields: branchName required, address, district, city, country, headquarter checkbox.
- List fields: branch name, HQ badge, full address, edit/delete.
- Dialogs: browser alert for HQ delete, confirm for delete.
- Data shown: `CompanyLocation[]`.
- Loading state: loading text.
- Empty state: dashed empty panel.
- Error state: inline and alert errors.
- Permissions: intended employer-only; HQ delete blocked in UI.

### EMP-004 Company Verification
- Goal: Upload legal verification documents and track status.
- Main actions: refresh, choose file, upload, view, download, delete pending document.
- Form fields: file input; PDF/JPG/PNG, max 10MB.
- List fields: file type, file name, upload time, status, reject reason, actions.
- Data shown: `Company`, `CompanyDocument[]`.
- Loading state: centered loading card.
- Empty state: no documents message.
- Error state: inline error/success banners.
- Permissions: intended employer-only; delete shown only for pending docs.

### EMP-005 Employer Jobs
- Goal: Manage company job postings and review submission.
- Main content: verification-block notice if unverified, job form, job list.
- Main actions: create, edit, delete, save draft, save and submit review, submit draft for review, update/resubmit rejected job.
- Form fields: title, jobType, workMode, experienceLevel, vacancies, salaryType, salaryMin, salaryMax, location/companyLocationId, deadline, workingTime, skills, description, requirements, benefits, status.
- Cards/list fields: title, status, location, salary, vacancies, views, deadline, skills, rejection reason, actions.
- Dialogs: browser confirm for delete/submit review; alerts for some errors.
- Data shown: `Company`, `Job[]`, `CompanyLocation[]`.
- Loading state: loading text.
- Empty state: dashed no jobs panel, create-first button if verified.
- Error state: inline banners and alerts.
- Permissions: create/edit gated by verified company in UI.
- Navigation: `/employer/verification`, `/employer/company-profile`.

## Admin Screens

### ADM-001 Admin Login
- Goal: Login admin only.
- Form fields: email, password.
- Error state: failed login, unverified account, or non-admin account.
- Permissions: stores session only when returned role is `ADMIN`.
- Navigation: `/admin`, `/jobs`.

### ADM-002 Admin Dashboard
- Current status: placeholder/minimal.
- Main content: four metric cards with `--` and welcome placeholder.
- API/actions: none.
- Mobile: metric grid collapses.

### ADM-003 Admin Company Review
- Goal: Review employer legal/company verification.
- Main content: filters, company list, selected company detail, owner info, documents, approve/reject.
- Main actions: filter, refresh, select, view/download documents, approve, reject with reason, cancel reject.
- Form fields: reject reason textarea.
- Filters: `pending`, `verified`, `rejected`.
- Detail fields: industry, taxCode, companySize, website, location, status, description, owner fullName/email/phone/position, legal documents.
- Loading state: separate list/detail loading.
- Empty state: no companies, no selection, no documents.
- Error state: inline error/success.
- Permissions: admin-only; approve/reject only for pending company.
- Mobile: list-detail collapses.

### ADM-004 Admin Users
- Current status: placeholder only.
- Main API/actions/forms/tables: none implemented.
- Missing information: user management behavior and endpoints.

### ADM-005 Admin Job Review
- Goal: Review employer job postings.
- Main content: filters, job list, selected job detail, approve/reject.
- Main actions: filter, refresh, select, approve, reject with reason, cancel reject.
- Form fields: reject reason textarea.
- Filters: `pending_review`, `published`, `rejected`.
- Detail fields: company, location, salary, experienceLevel, jobType, workMode, vacancies, deadline, description, requirements, skills, benefits, employer name/email/position, createdAt, rejectionReason.
- Loading state: separate list/detail loading.
- Empty state: no jobs or no selection.
- Error state: inline error/success.
- Permissions: admin-only; approve/reject only for pending_review job.
- Mobile: list-detail collapses.

### ADM-006 Admin Statistics
- Current status: placeholder only.
- Missing information: charts, metrics, APIs.

### ADM-007 Admin Settings
- Current status: placeholder only.
- Missing information: settings fields/actions/APIs.

### ADM-008 Admin Profile
- Goal: View own admin account.
- Data shown: email, role, status, emailVerified.
- Loading state: loading while user null.
- Error state: failed load sets user null; recovery needs verification.
- Permissions: admin-only.
