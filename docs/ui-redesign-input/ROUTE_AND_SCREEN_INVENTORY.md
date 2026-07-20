# Route and Screen Inventory

| Screen ID | Route | Screen name | Role | Purpose | Current status | Main API | Parent layout |
|---|---|---|---|---|---|---|---|
| PUB-001 | `/` | Root redirect | Guest | Redirect to public jobs | Implemented | None | None |
| AUTH-001 | `/login` | User login | Guest | Login and route by role | Implemented | `GET /auth/config`, `POST /auth/login` | `Shell` |
| AUTH-002 | `/register` | Register | Guest | Create candidate/employer account | Implemented | `POST /auth/register` | `Shell` |
| AUTH-003 | `/verify-email` | Verify email | Guest | Verify email by token query param | Implemented | `POST /auth/verify-email` | `Shell` |
| AUTH-004 | `/oauth/callback` | OAuth callback | Guest | Store OAuth token and route user | Implemented | `GET /auth/me` | `Shell` |
| AUTH-005 | `/select-role` | OAuth role selection | Guest | Choose candidate/employer after OAuth | Implemented | `POST /auth/oauth/complete-role` | `Shell` |
| PUB-002 | `/jobs` | Public job list | Guest/all | Browse, search, filter jobs | Implemented | `GET /jobs` | `Shell` |
| PUB-003 | `/jobs/:id` | Job detail | Guest/Candidate | View job and apply/save when logged in | Implemented | `GET /jobs/:id`, candidate CV/save/apply APIs | `Shell` |
| CAN-001 | `/candidate` | Candidate dashboard | Candidate | Show recommendations | Implemented | `GET /candidate/recommendations/jobs` | `CandidateLayout` |
| CAN-002 | `/candidate/profile` | Candidate profile | Candidate | Edit profile and skills | Implemented | `GET/PUT /candidate/profile` | `CandidateLayout` |
| CAN-003 | `/candidate/cvs` | Candidate CVs | Candidate | Manage CV files and versions | Implemented | `/candidate/cvs`, `/candidate/cv-versions` | `CandidateLayout` |
| CAN-004 | `/candidate/saved-jobs` | Saved jobs | Candidate | View saved jobs | Implemented | `GET /candidate/saved-jobs` | `CandidateLayout` |
| CAN-005 | `/candidate/applications` | Applications | Candidate | List own applications | Implemented | `GET /applications/me` | `CandidateLayout` |
| CAN-006 | `/candidate/applications/:id` | Application detail | Candidate | View status and timeline | Implemented | `GET /applications/me/:id` | `CandidateLayout` |
| CAN-007 | `/candidate/ai-interviews` | AI interviews | Candidate | Create/run/review AI interview sessions | Implemented | `/candidate/ai-interviews/*` | `CandidateLayout` |
| CAN-008 | `/candidate/notifications` | Notifications | Candidate | View and mark notifications read | Implemented | `GET /candidate/notifications`, `PATCH /candidate/notifications/:id/read` | `CandidateLayout` |
| CAN-009 | `/candidate/subscription` | Subscription | Candidate | View plan, benefits, usage | Implemented | `GET /candidate/subscription` | `CandidateLayout` |
| EMP-001 | `/employer` | Employer dashboard | Employer | Employer landing placeholder | Placeholder/minimal | None | `EmployerLayout` |
| EMP-002 | `/employer/company-profile` | Company profile | Employer | Edit company profile and logo | Implemented | `GET/PUT /employer/company`, `POST /employer/company/logo`, `GET /categories` | `EmployerLayout` |
| EMP-003 | `/employer/locations` | Company locations | Employer | Manage branches/work locations | Implemented | `/employer/company/locations` | `EmployerLayout` |
| EMP-004 | `/employer/verification` | Company verification | Employer | Upload and view legal documents | Implemented | `/employer/company`, `/employer/company/documents` | `EmployerLayout` |
| EMP-005 | `/employer/jobs` | Employer jobs | Employer | Create/edit/delete/submit jobs | Implemented | `/employer/jobs`, `/employer/company`, `/employer/company/locations` | `EmployerLayout` |
| ADM-001 | `/admin/login` | Admin login | Admin/Guest | Login admin only | Implemented | `POST /auth/login` | `admin-auth-shell` |
| ADM-002 | `/admin` | Admin dashboard | Admin | Placeholder metrics and welcome | Placeholder/minimal | None | `AdminLayout` |
| ADM-003 | `/admin/companies` | Admin company review | Admin | Review company verification | Implemented | `/admin/companies` | `AdminLayout` |
| ADM-004 | `/admin/users` | Admin users | Admin | User management placeholder | Placeholder | None | `AdminLayout` |
| ADM-005 | `/admin/jobs` | Admin job review | Admin | Review job postings | Implemented | `/admin/jobs` | `AdminLayout` |
| ADM-006 | `/admin/statistics` | Admin statistics | Admin | Analytics placeholder | Placeholder | None | `AdminLayout` |
| ADM-007 | `/admin/settings` | Admin settings | Admin | Settings placeholder | Placeholder | None | `AdminLayout` |
| ADM-008 | `/admin/profile` | Admin profile | Admin | View admin account details | Implemented | `GET /auth/me` | `AdminLayout` |

## Services Without Current Frontend Routes
- `interviewService.ts` exposes `/interviews/*`, but no `/interviews` route is declared in `App.tsx`. Needs verification.
- `assessmentService.ts` exposes `/assessments/*`, but no `/assessments` route is declared in `App.tsx`. Needs verification.
- `Header.tsx` links to `/interviews` and `/assessments`, but `Layout/Header` is not used by the active route tree in `App.tsx`. Needs verification.
