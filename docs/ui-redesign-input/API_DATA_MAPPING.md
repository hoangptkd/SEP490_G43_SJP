# API Data Mapping

## Shared API Client
- Base URL: `VITE_API_URL` or `/api`.
- Auth: `Authorization: Bearer <token>` from `localStorage.token`.
- 401 behavior: clears `token`, `user`, `role`; redirects admin paths to `/admin/login`, other paths to `/login`.

## Auth Screens
| Screen | Endpoint/service | Request data | Response data | Display fields | Data-changing actions |
|---|---|---|---|---|---|
| AUTH-001 | `GET /auth/config`, `POST /auth/login` | `{email,password}` | `{googleOAuthEnabled}`, `{token,user}` | OAuth availability, errors | Store token/user/role |
| AUTH-002 | `POST /auth/register` | `{email,password,role}` | `AuthResponse` | success/error | Create account |
| AUTH-003 | `POST /auth/verify-email` | `{token}` | `User` | verification message | Verify email |
| AUTH-004 | `GET /auth/me` | Bearer token from URL | `User` | processing/error | Store session |
| AUTH-005 | `POST /auth/oauth/complete-role` | `{token,role}` | `AuthResponse` | error | Store session |

Enums:
- Role: `CANDIDATE`, `EMPLOYER`, `ADMIN`.
- User status: `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`.

## Public Jobs
| Screen | Endpoint/service | Request data | Response data | Fields shown | Data-changing actions |
|---|---|---|---|---|---|
| PUB-002 | `GET /jobs` | query `page`, `size`, `search`, `location`, `minSalary`, `maxSalary`, `experienceLevel`, `skills`, `sort` | `JobApiResponse` | title, company, location, experience, salary, match/saved/applied | None |
| PUB-003 | `GET /jobs/:id`, `GET /candidate/cvs`, save/apply APIs | path `id`; apply `{jobId,cvId,cvVersionId}` | `Job`, `CvFile[]`, `CandidateApplication` | full job detail, CV selector | `POST/DELETE /candidate/saved-jobs/:jobId`, `POST /applications` |

Job fields include `id`, `title`, `description`, `salaryMin`, `salaryMax`, `salaryType`, `location`, `requirements`, `skills`, `benefits`, `vacancies`, `workingTime`, `jobType`, `workMode`, `experienceLevel`, `deadline`, `status`, `rejectionReason`, `saved`, `applied`, `matchScore`, `viewsCount`, `company`, `companyLocationId`.

Job statuses observed: `ACTIVE`, `PUBLISHED`, `PENDING_REVIEW`, `REJECTED`, `CLOSED`, `DRAFT`, `EXPIRED`, `ARCHIVED`, plus lowercase variants in employer UI.

## Candidate Screens
| Screen | Endpoint/service | Request data | Response data | Fields shown | Data-changing actions |
|---|---|---|---|---|---|
| CAN-001 | `GET /candidate/recommendations/jobs` | none | `Recommendation[]` | recommended job cards | None |
| CAN-002 | `GET/PUT /candidate/profile` | `CandidateProfileRequest` partial | `CandidateProfile` | fullName, phone, location, skills, bio, applyReady | Update profile |
| CAN-003 | `/candidate/cvs`, `/candidate/cv-versions` | multipart file; `{title,templateKey,snapshot}` | `CvFile[]`, `CvVersion[]` | file names, default, version title/template/date | Upload, default, delete, create version |
| CAN-004 | `GET /candidate/saved-jobs` | none | `Job[]` | job cards | None on screen |
| CAN-005 | `GET /applications/me` | none | `CandidateApplication[]` | job, company, status, timestamps Needs verification | None |
| CAN-006 | `GET /applications/me/:id` | path id | `CandidateApplication` | application detail and timeline | None |
| CAN-007 | `/candidate/ai-interviews/*` | see AI section | config, applications, sessions, transcript | AI config/session/question/feedback data | Create/delete sessions, upload audio, answer, skip, retry |
| CAN-008 | `GET /candidate/notifications`, `PATCH /candidate/notifications/:id/read` | notification id | `NotificationItem[]` | title, message, read state | Mark read |
| CAN-009 | `GET /candidate/subscription` | none | `SubscriptionView` | plan, status, price, benefits, usage counts | None |

Candidate profile request fields:
- `fullName`, `phone`, `location`, `bio`, `skills`, `education`, `workExperience`, `projects`, `certifications`.

Application statuses displayed:
- `SUBMITTED`, `UNDER_REVIEW`, `SHORTLISTED`, `INTERVIEW_SCHEDULED`, `INTERVIEWED`, `EVALUATED`, `ACCEPTED`, `REJECTED`, `HIRED`.

## AI Interview API
- `GET /candidate/ai-interviews/config-status` -> `AiInterviewConfig`: `enabled`, `message`, `questionCount`, `audioMaxSeconds`, `audioMaxSizeMb`, `voiceStreamingEnabled`, `voiceProvider`, `voiceSilenceMs`.
- `GET /candidate/ai-interviews/eligible-applications` -> application id, status, submittedAt, job.
- `GET /candidate/ai-interviews/sessions` and `GET /candidate/ai-interviews/sessions/:id` -> `AiInterviewSession`.
- `POST /candidate/ai-interviews/sessions/application` with `{applicationId}`.
- `POST /candidate/ai-interviews/sessions/practice` with `{targetRole,skills,jobId|null}`. Backend constraints: targetRole max 120, skills max 12 items, each skill max 80, jobId max 36.
- `DELETE /candidate/ai-interviews/sessions/:id`.
- `POST /candidate/ai-interviews/sessions/:sessionId/questions/current/audio` multipart `file` and `durationSeconds`.
- `POST /candidate/ai-interviews/sessions/:sessionId/questions/:questionId/answer` with `{transcript}` max 12000 chars.
- `POST /candidate/ai-interviews/sessions/:sessionId/questions/:questionId/skip`.
- `POST /candidate/ai-interviews/sessions/:sessionId/questions/:questionId/feedback/retry`.
- `POST /candidate/ai-interviews/sessions/:sessionId/summary/retry`.

AI enums/status:
- Context: `application`, `practice`.
- Session: `created`, `in_progress`, `completed`, `cancelled`.
- Feedback source: `provider`, `fallback`, `skipped`.
- Voice phases: `idle`, `speaking`, `listening`, `processing`, `paused`, `error`.

## Employer Screens
| Screen | Endpoint/service | Request data | Response data | Fields shown | Data-changing actions |
|---|---|---|---|---|---|
| EMP-001 | None | none | none | placeholder | None |
| EMP-002 | `GET/PUT /employer/company`, `POST /employer/company/logo`, `GET /categories` | company JSON; logo multipart | `Company`, `Category[]` | profile fields, logo, verification, locations | Save profile, upload logo |
| EMP-003 | `/employer/company/locations` | `CompanyLocationRequest` | `CompanyLocation[]` or item | branch/address/HQ | Add/edit/delete |
| EMP-004 | `/employer/company`, `/employer/company/documents` | document multipart | `Company`, `CompanyDocument[]` | verification status, document metadata | Upload/delete document |
| EMP-005 | `/employer/jobs`, `/employer/company`, `/employer/company/locations` | job payload | `Company`, `Job[]`, `CompanyLocation[]`, `Job` | job detail/list fields | Create/update/delete/submit review |

Company request fields:
- `name` required, `description`, `website`, `industry`, `location`, `companySize`, `taxCode`, `logoUrl`.

Location request fields:
- `branchName` required, `address`, `city`, `district`, `country`, `headquarter`.

Employer job request fields:
- `title` required, `description`, `requirements`, `skills`, `benefits`, `salaryMin`, `salaryMax`, `salaryType`, `location`, `companyLocationId`, `vacancies`, `workingTime`, `jobType`, `workMode`, `experienceLevel`, `deadline`, `status`, `employerId`.

Employer UI enums:
- `jobType`: `full_time`, `part_time`, `contract`, `internship`, `freelance`.
- `workMode`: `onsite`, `remote`, `hybrid`.
- `experienceLevel`: `intern`, `fresher`, `junior`, `middle`, `senior`, `manager`.
- `salaryType`: `range`, `fixed`, `negotiable`.
- Job status: `draft`, `pending_review`, `published`, `active`, `rejected`, `closed`.

Company/document statuses observed:
- Company: `verified`, `pending`, `rejected`, `unverified`, plus uppercase variants.
- Document: `approved`, `verified`, `rejected`, `pending`.

## Admin Screens
| Screen | Endpoint/service | Request data | Response data | Fields shown | Data-changing actions |
|---|---|---|---|---|---|
| ADM-001 | `POST /auth/login` | `{email,password}` | `AuthResponse` | errors | Store admin session |
| ADM-002 | None | none | none | placeholder metrics | None |
| ADM-003 | `/admin/companies` | filter status; reject `{reason}` | `AdminCompanySummary[]`, `AdminCompanyDetail` | company, owner, documents | Approve/reject company |
| ADM-004 | None | none | none | placeholder | None |
| ADM-005 | `/admin/jobs` | filter status; reject `{reason}` | `AdminJobSummary[]`, `AdminJobDetail` | job, company, employer | Approve/reject job |
| ADM-006 | None | none | none | placeholder | None |
| ADM-007 | None | none | none | placeholder | None |
| ADM-008 | `GET /auth/me` | none | `User` | email, role, status, emailVerified | None |

Admin filters/status:
- Company filters: `pending`, `verified`, `rejected`.
- Job filters: `pending_review`, `published`, `rejected`.
- Company review actions show only when company `verificationStatus` is pending.
- Job review actions show only when job `status` is pending_review.
