# Smart Recruitment Portal UI Redesign Brief for Google Stitch

## Product Overview
Smart Recruitment Portal is a role-based recruitment platform with public job discovery, a candidate portal, an employer portal, and an admin review console. Candidates find jobs, manage CVs, apply, track applications, and practice interviews with AI feedback. Employers manage company profiles, legal verification, work locations, and job postings. Admins review company verification and job postings before approval.

The redesign should cover the current implemented product only. Do not invent routes or features that are not implemented. Placeholder screens must remain clearly marked as placeholder or future work.

## Target Users
- Guests: browse jobs and decide whether to register or login.
- Candidates: manage job search, profile, CVs, applications, saved jobs, notifications, subscription, and AI interview practice.
- Employers: manage company trust information and job postings.
- Admins: perform review and moderation in dense list-detail workflows.

## User Roles
- Guest: public job browsing, auth, registration, email verification, OAuth callback, OAuth role selection.
- Candidate: dashboard, profile, CVs, saved jobs, applications, AI interviews, notifications, subscription.
- Employer: dashboard placeholder, company profile, work locations, legal verification, jobs after verification.
- Admin: admin login, dashboard placeholder, company review, job review, admin profile, placeholder users/statistics/settings.

Implementation note: candidate and employer protected routes currently check only for token in frontend. Admin routes check token plus stored user role `ADMIN`. AI interview is explicitly candidate-only in backend.

## Main Modules
1. Public job discovery: search/filter jobs and view job detail/apply panel.
2. Authentication: login, register, email verification, OAuth callback, role selection.
3. Candidate portal: recommendations, profile, CVs, saved jobs, applications, AI interviews, notifications, subscription.
4. Employer portal: dashboard placeholder, company profile, locations, verification documents, job management.
5. Admin portal: admin login, side nav, company review, job review, read-only profile, placeholder dashboard/users/statistics/settings.

## Navigation Structure
### Public Shell
- Brand: `/jobs`
- Jobs: `/jobs`
- Candidate: `/candidate` when token and stored role is `CANDIDATE`
- Employer: `/employer` when token and stored role is `EMPLOYER`
- Login: `/login` when no token

### Candidate Side Navigation
- `/candidate` dashboard
- `/candidate/profile`
- `/candidate/cvs`
- `/candidate/saved-jobs`
- `/candidate/applications`
- `/candidate/ai-interviews`
- `/candidate/notifications`
- `/candidate/subscription`
- `/jobs`
- Logout

### Employer Side Navigation
- `/employer`
- Company submenu: `/employer/company-profile`, `/employer/locations`, `/employer/verification`
- `/employer/jobs`
- Logout

### Admin Side Navigation
- `/admin`
- `/admin/companies`
- `/admin/users` placeholder
- `/admin/jobs`
- `/admin/statistics` placeholder
- `/admin/settings` placeholder
- `/admin/profile`
- Logout

## Complete Screen List
- `PUB-001` `/` Root redirect to `/jobs`.
- `AUTH-001` `/login` User login.
- `AUTH-002` `/register` Register candidate/employer.
- `AUTH-003` `/verify-email` Email verification.
- `AUTH-004` `/oauth/callback` OAuth callback processing.
- `AUTH-005` `/select-role` OAuth role selection.
- `PUB-002` `/jobs` Public job list.
- `PUB-003` `/jobs/:id` Job detail and apply panel.
- `CAN-001` `/candidate` Candidate dashboard/recommendations.
- `CAN-002` `/candidate/profile` Candidate profile.
- `CAN-003` `/candidate/cvs` CV file/version management.
- `CAN-004` `/candidate/saved-jobs` Saved jobs.
- `CAN-005` `/candidate/applications` Application list.
- `CAN-006` `/candidate/applications/:id` Application detail/timeline.
- `CAN-007` `/candidate/ai-interviews` AI interview dashboard/room/result.
- `CAN-008` `/candidate/notifications` Notifications.
- `CAN-009` `/candidate/subscription` Subscription summary.
- `EMP-001` `/employer` Employer dashboard placeholder.
- `EMP-002` `/employer/company-profile` Company profile.
- `EMP-003` `/employer/locations` Company locations.
- `EMP-004` `/employer/verification` Legal verification documents.
- `EMP-005` `/employer/jobs` Employer job management.
- `ADM-001` `/admin/login` Admin login.
- `ADM-002` `/admin` Admin dashboard placeholder.
- `ADM-003` `/admin/companies` Admin company review.
- `ADM-004` `/admin/users` User management placeholder.
- `ADM-005` `/admin/jobs` Admin job review.
- `ADM-006` `/admin/statistics` Statistics placeholder.
- `ADM-007` `/admin/settings` Settings placeholder.
- `ADM-008` `/admin/profile` Admin profile.

## Important User Flows
### Guest to Candidate Application
1. Browse `/jobs`.
2. Open `/jobs/:id`.
3. Login or register/verify if unauthenticated.
4. Choose CV in job detail.
5. Apply.
6. Track status in `/candidate/applications` and `/candidate/applications/:id`.

### Candidate AI Practice
1. Open `/candidate/ai-interviews`.
2. Load config, eligible applications, sessions, and active jobs.
3. Start from eligible application or create free-practice session with target role, skills, and optional job.
4. Answer questions by continuous voice, manual recording/transcription, or edited transcript.
5. Submit answer, skip question, or retry failed feedback.
6. Review score, summary, strengths, weaknesses, improvement plan, and question history.

### Employer Verification to Job Posting
1. Employer updates company profile and locations.
2. Employer uploads legal documents.
3. Admin approves or rejects company verification.
4. Verified employer creates draft or submits job for review.
5. Admin approves or rejects job.
6. Rejected jobs show rejection reason and can be edited/resubmitted.

### Admin Review
1. Admin logs in at `/admin/login`.
2. Opens company or job review.
3. Uses status filters.
4. Selects item from list.
5. Reviews detail and documents/job data.
6. Approves or rejects with reason.
7. List refreshes and detail updates.

## Common Components
- Role-specific shells with side navigation.
- Public topbar.
- Job cards.
- Status badges for company, document, job, application, AI fallback states.
- Admin list-detail review layouts.
- Responsive table/list rows.
- Form grids.
- File upload panels.
- Empty, loading, success, error, and notice states.
- Browser confirmation actions for destructive/review actions in current UI.

## Required Information on Each Screen
### Job Discovery
- Filters: search, location, skills, experience, sort.
- Job card: title, company, location, experience, salary, match/saved/applied chips.
- Job detail: description, requirements, apply panel, CV selector, save/unsave, apply disabled when already applied, login CTA for guests.

### Candidate
- Profile: full name, phone, location, skills, bio, apply readiness.
- CVs: uploaded file list, default action, delete action, CV version list.
- Applications: job, company, status, timestamps/timeline.
- AI interviews: config status, eligible apps, practice form, sessions, question, recording/transcription states, transcript editor, feedback, summary, fallback notices, retry actions.
- Notifications: title, message, read state/action.
- Subscription: plan, price, status, benefits, saved jobs count, CV count, unread notifications count.

### Employer
- Company profile: logo, name, website, industry, HQ/location, size, tax code, description, verification badge, branch preview.
- Locations: branch name, address, district, city, country, headquarter flag, edit/delete.
- Verification: status banner, upload constraints, document rows, view/download/delete, reject reason.
- Jobs: verification block if unverified, all job form fields, job cards with status/salary/location/vacancies/views/deadline/skills/rejection reason/actions.

### Admin
- Company review: filters, company list, owner, pending document count, company detail, legal documents, approve/reject with reason.
- Job review: filters, job list, job detail, employer identity, approve/reject with reason.
- Dashboard/users/statistics/settings: mark as not implemented/placeholder; do not invent real data or controls.
- Profile: email, role, status, email verification.

## Responsive Requirements
- Desktop admin and employer screens should be dense and scannable.
- Below tablet/mobile widths, side navs and list-detail layouts should stack cleanly.
- Job filters should stack above the list on mobile.
- Job detail apply panel should remain easy to access on mobile.
- Large forms should collapse from two columns to one column.
- Tables should avoid horizontal overflow; use stacked rows/cards.
- AI interview room must keep question, controls, transcript, and history readable without overlap.

## Accessibility Requirements
- Use semantic headings.
- All form fields need visible labels.
- Required fields must be indicated with text or clear label, not color only.
- Error/status messages should be near the relevant form or action.
- Buttons need clear action labels.
- Status badges need text labels, not color only.
- File upload panels must expose accepted formats and selected file state.
- Destructive/review actions need clear confirmation.
- Keyboard access must work for nav, filters, forms, upload controls, dialogs/panels, and review actions.

## Design Constraints
- Keep current routes and backend data contracts.
- Do not add real `/interviews` or `/assessments` screens; services exist but routes are not declared.
- Do not turn admin placeholders into real tools unless marked as not implemented.
- Do not invent live admin metrics.
- Preserve employer verification gating.
- Preserve admin approval/rejection with required reason.
- Preserve candidate AI disclaimer: AI feedback is for practice, not a hiring decision.
- Preserve file constraints and upload states.
- Preserve role separation and admin-only admin console behavior.

## Realistic Example Content
Jobs:
- Senior Java Spring Boot Developer, FPT Software, Hanoi, 20,000,000 - 35,000,000 VND.
- Frontend React Developer, SmartTech Vietnam, Ho Chi Minh City, 12,000,000 - 22,000,000 VND.
- Data Analyst Intern, EduTech Labs, Hybrid, Negotiable.

Candidate:
- Nguyen Minh Anh.
- Skills: Java, Spring Boot, React, PostgreSQL, Docker.
- Application statuses: Submitted, Under Review, Shortlisted, Interview Scheduled, Accepted, Rejected.
- AI practice target role: Backend Developer.

Employer:
- FPT Software.
- Industry: Information Technology.
- Tax code: 0101601092.
- Branches: Hanoi Head Office, Ho Chi Minh Branch, Da Nang Office.
- Verification statuses: Unverified, Pending Review, Verified, Rejected.

Admin review:
- Business registration certificate.pdf.
- Tax certificate.png.
- Example rejection reason: Document is blurred; please upload a clearer business registration certificate.

## Explicitly Not Implemented or Needs Verification
- Admin dashboard real metrics are not implemented.
- Admin users is a placeholder.
- Admin statistics is a placeholder and no chart library is installed.
- Admin settings is a placeholder.
- `/interviews` and `/assessments` routes are not declared even though services/header links exist.
- Candidate/employer route guards check only token in current frontend.
- Backend/service role checks outside AI interview need verification.
- Some source Vietnamese strings appear encoding-corrupted; final UI copy should be verified separately.
