# Candidate Deep Functional And Data Field Review

Scope: second-pass Candidate review for functional completeness, business logic, forms, fields, data structures, job search, job detail, application flow, and saved jobs.

Out of scope: AI interview quality. The route `/candidate/ai-interviews` is inventoried only as an existing Candidate route.

Primary evidence reviewed:

- `frontend/src/App.tsx`
- `frontend/src/services/candidateService.ts`
- `frontend/src/services/jobService.ts`
- `frontend/src/types/candidateDomain.ts`
- `frontend/src/types/job.ts`
- `backend/src/main/java/com/sjp/recruitment/controller/*`
- `backend/src/main/java/com/sjp/recruitment/service/*`
- `backend/src/main/java/com/sjp/recruitment/model/dto/request/*`
- `backend/src/main/java/com/sjp/recruitment/model/dto/response/*`
- `backend/src/main/java/com/sjp/recruitment/model/entity/*`
- `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- candidate migrations through `V26__normalize_application_job_snapshot_json.sql`

## Candidate Screen Inventory

| Route | Screen | Purpose | Current Functions | Missing Functions | Problems | Priority |
|---|---|---|---|---|---|---|
| `/` | Public home | Entry point and general discovery | Shows site entry content and links into auth/jobs | Candidate-specific next step could be clearer after login because Candidate login redirects here | Candidate may need extra navigation to reach dashboard or applications after login | P3 |
| `/login` | Login | Authenticate Candidate or other roles | Email/password login, Google OAuth when enabled, forgot-password link | Inline account recovery for inactive/unverified states | Frontend relies mostly on API errors; successful Candidate login goes to `/` rather than Candidate workspace | P2 |
| `/register` | Register | Create Candidate or Employer account | Email, password, confirm password, role select | Candidate-only registration path or clearer role-specific paths | Role selector can create wrong-role accounts from a Candidate flow; password rule copy is not fully enforced client-side | P2 |
| `/forgot-password` | Forgot password | Start password reset | Email form, generic success message | None material | Basic form is adequate | P3 |
| `/reset-password` | Reset password | Complete reset from token | New password, confirm password, missing-token submit block | Better invalid-link state before form interaction | Password rule copy is backend-enforced, not mirrored as rule checklist | P2 |
| `/verify-email` | Verify email | Activate newly registered account | Token verification from URL | Resend verification link | Candidate can get stuck if link expired and no resend path is exposed here | P2 |
| `/oauth/callback` | OAuth callback | Finish OAuth login | Stores token/user and routes by role | None verified in Candidate scope | Depends on external OAuth outcome | P3 |
| `/select-role` | OAuth role selection | Select Candidate/Employer after OAuth | Role selection for OAuth-created user | Stronger role explanation | Wrong-role selection is hard to recover from in Candidate journey | P3 |
| `/jobs` | Job search | Search public jobs | Keyword, location, skills text, salary min/max, experience, sort, pagination, URL state, result count, loading and no-result states | Category filter, job type filter, work mode filter, salary range validation | API already accepts category; job type/work mode exist in job data but are not filterable | P2 |
| `/jobs/:id` | Job detail | Decide whether to save, report, or apply | Loads job, save/unsave, apply modal, report form, login CTA | Benefits, skills, job type, work mode, deadline, working time, vacancies, richer company details, direct application link after success | API returns many fields the page does not show; no dedicated load error state | P1 |
| `/candidate` | Candidate home | Candidate dashboard landing | Profile/applications/saved/recommendation summary and shortcuts | Stronger "next best action" based on incomplete profile/no CV/no applications | Completeness depends on data shape from unpaged endpoints | P2 |
| `/candidate/profile` | Profile | Maintain job-seeking profile | Full name, phone, DOB, location, skills comma input, bio, education/work/projects/certifications generic sections | Professional headline, experience years/level, LinkedIn, portfolio, structured sections, job preferences if product uses them | Entity/schema support several fields that UI/API hide; section data is untyped JSON | P1 |
| `/candidate/account` | Account/security | Manage account, avatar, password, deactivation | Account view, avatar upload, password change, deactivate | Token/session invalidation after password change/deactivation; password rule checklist | Current browser session clears on deactivation, but already issued tokens are not revoked | P1 |
| `/candidate/cvs` | CV management | Upload/manage uploaded PDFs and builder snapshots | Upload PDF, list, view/download, set default, delete, create/rename/refresh/delete builder version | Preview builder snapshot content; version size/shape validation; pagination for many CVs | Builder snapshot is generated from loose profile JSON and only title is editable | P2 |
| `/candidate/saved-jobs` | Saved jobs | Review saved jobs | Lists saved jobs and opens detail | Unsave directly from list, pagination, error state, expired/deleted labeling | Candidate must open detail to unsave; backend list is unpaged | P2 |
| `/candidate/applications` | Applications | Track submitted applications | Lists applications with job, company, status, submitted date; links to detail | Pagination, search/filter, error state | Backend uses `Pageable.unpaged()` and UI has no fetch-error state | P1 |
| `/candidate/applications/:id` | Application detail | Understand submission, timeline, interviews, offers | Shows job/company/status, submitted preferred location/cover letter, CV title/name, timeline, interview responses, offer responses | Submitted CV download/snapshot, richer immutable job snapshot display, status coverage for `WITHDRAWN`, offer response state guard | Candidate cannot fully verify the exact submitted CV content | P1 |
| `/candidate/ai-interviews` | AI interviews | AI practice/application interview area | Existing route and UI | Not reviewed | AI quality intentionally out of scope | N/A |
| `/candidate/notifications` | Notifications | Read system and employer updates | List notifications, mark one/all read, link to related job/application | Pagination, error/retry state, optimistic mark-read rollback | Backend and UI are unpaged | P2 |
| `/candidate/subscription` | Subscription | View plan and usage | Shows current plan, benefits, saved-job/CV/unread counts | Usage-limit explanation where Candidate hits limits | Low direct job-seeking risk | P3 |
| `/candidate/subscription/plans` | Plans | Upgrade/downgrade subscription | Shared plan purchase UI | Candidate-specific feature impact could be clearer | Not central to application correctness | P3 |
| `/payment/result`, `/payment/checkout`, `/payment/bank/:paymentId` | Payment flow | Complete subscription payments | Protected payment pages | Candidate-specific recovery is outside this audit | Billing is not core Candidate functional flow | P3 |

## Candidate Form And Field Review

| Form | Field | Current type | Required/optional | Frontend validation | Backend validation | Database representation | Assessment |
|---|---|---|---|---|---|---|---|
| Login | email | Email input | Required | HTML `required` and `type=email` | `@NotBlank`, `@Email` | `users.email` citext unique | Necessary and appropriate |
| Login | password | Password input | Required | HTML `required` | `@NotBlank`, password match in service | `users.password_hash` | Necessary and appropriate |
| Registration | email | Email input | Required | HTML `required` and `type=email` | `@NotBlank`, `@Email`, uniqueness | `users.email` citext unique | Necessary |
| Registration | password | Password input | Required | HTML `required`; placeholder says min 8 | Service requires length >= 8, upper, lower, digit | `users.password_hash` | Needs frontend parity with backend rule |
| Registration | confirmPassword | Password input | UI-only | Client equality check | Not sent | None | Necessary UI helper |
| Registration | role | Select | Required | Enum options in UI | `@NotNull UserRole` | `users.role` | Necessary globally, but risky on Candidate-specific signup |
| Forgot password | email | Email input | Required | HTML email/required | `@NotBlank`, `@Email` | `users.email` lookup | Appropriate |
| Reset password | token | URL query string | Required | Submit blocked when missing | `@NotBlank`, reset token lookup/expiry/used check | `password_reset_tokens` | Needs clearer invalid-link page state |
| Reset password | password | Password input | Required | Required plus confirm equality | `@NotBlank`, service strength rule | `users.password_hash` | Needs password rule parity |
| Reset password | confirmPassword | Password input | UI-only | Equality check | Not sent | None | Appropriate |
| Profile | fullName | Text input | Apply-ready required, DTO optional | None | None on DTO; apply-ready checks nonblank | `users.full_name` | Needs trim/size validation |
| Profile | phone | Text input | Apply-ready required, DTO optional | None | None on DTO; apply-ready checks nonblank | `users.phone` | Needs format/length validation |
| Profile | dateOfBirth | Date input | Optional | Browser date input only | None | `job_seekers.date_of_birth` | Needs future-date and plausible-age validation if retained |
| Profile | location | Text input | Apply-ready required | None | None on DTO; apply-ready checks nonblank | `job_seekers.location` | Useful, but matching would benefit from normalized/autocomplete location |
| Profile | skills | Comma-separated text | Apply-ready required | None beyond manual parsing | Dedup/canonicalization in service via skills table, but no DTO constraints | `skills`, `candidate_skills` | Needs tokenized multiselect/autocomplete and per-skill limits |
| Profile | bio | Textarea | Optional | None | None | `job_seekers.summary` | Useful, needs max length and guidance |
| Profile sections | title | Text input | Optional | None | Stored as generic JSON object | JSONB section columns | Too generic across education/work/projects/certifications |
| Profile sections | organization | Text input | Optional | None | Stored as generic JSON object | JSONB section columns | Too generic and ambiguous |
| Profile sections | time | Text input | Optional | None | Stored as generic JSON object | JSONB section columns | Should be structured dates/current flag |
| Profile sections | description | Textarea | Optional | None | Stored as generic JSON object | JSONB section columns | Useful, but needs length limits |
| Account avatar | avatarFile | File input | Required for submit | Accepts png/jpeg/webp; no client size enforcement | MIME allowlist and 2 MB max | `users.avatar_url` | Mostly adequate; frontend should enforce same size |
| Change password | currentPassword | Password input | Required | Required; disabled for OAuth-only accounts | `@NotBlank`, current-password check | `users.password_hash` | Appropriate |
| Change password | newPassword | Password input | Required | Required and confirm equality | `@NotBlank`, service strength and changed-password check | `users.password_hash` | Needs frontend strength parity |
| Change password | confirmPassword | Password input | UI-only | Equality check | Not sent | None | Appropriate |
| Deactivate account | currentPassword | Password input | Required | Required; disabled for OAuth-only accounts | `@NotBlank`, current-password check | `users.status` set suspended | Needs token/session invalidation |
| CV upload | file | File input | Required | Accept PDF in CV page; apply modal checks extension/MIME and 5 MB | Extension/MIME PDF check and 5 MB max | `resumes.file_name/file_type/file_size/file_url/source_type` | Needs actual PDF signature scanning and consistent frontend validation on CV page |
| CV builder | title | Text input | Required | UI blocks blank while renaming | `@NotBlank` only | `resumes.title` | Needs max length |
| CV builder | snapshot | Generated JSON | Required-ish | Built from profile object | No schema | `resumes.content_json` | Depends on loose profile JSON; should be typed |
| Job search | search | Text input | Optional | None | Used in title/description/requirements/company search | Query only | Appropriate |
| Job search | location | Text input | Optional | None | ILIKE job/company location | Query only | Appropriate but could use autocomplete |
| Job search | skills | Text input | Optional | None | Comma parser, requires all selected skills | Query only | Needs tokenized multiselect |
| Job search | minSalary | Number input | Optional | `min=0` | BigDecimal param, range semantics in SQL | Query only | Needs min <= max validation |
| Job search | maxSalary | Number input | Optional | `min=0` | BigDecimal param, range semantics in SQL | Query only | Needs min <= max validation |
| Job search | experienceLevel | Select | Optional | Fixed UI options | Lowercase equality against job data | Query only | Values should be centralized with backend/DB enum |
| Job search | sort | Select | Optional | Fixed options | `salary`, `deadline`, default newest; `relevance` falls back to newest | Query only | `relevance` is misleading unless implemented |
| Job report | reason | Select | Required | Required select | Service allowlist | `job_reports.reason` | Correctly validated |
| Job report | description | Textarea | Optional | None | Trim only, no length | `job_reports.description` | Needs max length |
| Job application | resume | Radio/upload | Required by UI | Requires selected resume or file | Service falls back to default uploaded CV if neither id is supplied | `applications.resume_id`, `applications.cv_version_id` | API contract should require exactly one resume reference unless default fallback is intentional |
| Job application | preferredLocation | Text input | Required by UI | Required and trim | Optional in DTO/service | `applications.preferred_location` | Requiredness mismatch |
| Job application | coverLetter | Textarea | Optional | Slices to 2000 chars | Service enforces 2000 after trim | `applications.cover_letter` | Mostly good; add `maxLength` attribute |
| Job application | allowAi | Checkbox | Required by UI submit | Blocks submit if unchecked | Not sent | None | Remove or persist consent metadata |
| Job application | agreePolicy | Checkbox | Required by UI submit | Blocks submit if unchecked | Not sent | None | Remove or persist acknowledgement |
| Interview response | response | Button action string | Required | Hardcoded buttons | `@Pattern(confirmed|request_reschedule|declined)` | `interview_schedules.candidate_response/status` | Backend validation exists; state transition rules still need review |
| Interview reschedule | rescheduleNote | Textarea | Required by modal for reschedule | Confirm disabled if blank | Optional string in DTO | `interview_schedules.candidate_reschedule_note` | Backend should require note when response is request_reschedule |
| Offer response | accepted | Boolean query param | Required by API call | Button actions | No request DTO/state guard for initial response | `job_offers.status` | Needs explicit status command and state validation |
| Offer note | note | Query param text | Optional | None | Optional string | `job_offers.candidate_note` | Needs length limits |
| Final offer response | accepted | Boolean query param | Required | Button actions | Requires offer status `employer_declined_negotiation` | `job_offers.status` | State guard exists, but enum request would be clearer |
| Notifications | mark read | Button action | Optional | No rollback/error handling | Ownership check by recipient | `notifications.read` | Functional but lacks recovery |

## Profile Data Review

Current profile support is useful for a basic application flow, but it is not yet a reliable job-seeking profile model. The UI/API expose only `fullName`, `phone`, `dateOfBirth`, `location`, `bio`, `skills`, `education`, `workExperience`, `projects`, and `certifications`. The entity and initial schema already include `headline`, `years_of_experience`, `experience_level`, `linkedin_url`, and `portfolio_url`, but the request/response DTOs and UI do not expose them.

| User problem | Why current system is insufficient | Proposed field | Field type | Required/optional | Validation | Where displayed | Database/API impact | Priority |
|---|---|---|---|---|---|---|---|---|
| Candidate needs to quickly communicate professional identity | Bio is long-form and job title is not editable even though schema has `headline` | Professional headline/title | Text | Optional | Trim, 120 char max | Profile header, CV builder, application detail, employer application view | Expose existing `job_seekers.headline` in request/response/UI | P1 |
| Candidate matching and recruiter screening need experience signal | Entity has `years_of_experience` and `experience_level`, but Candidate cannot edit them | Experience years and level | Number plus select | Optional, but recommended | Years 0-60; level enum aligned to DB | Profile, CV builder, recommendations, application summary | Expose existing columns, add DTO validation | P1 |
| Candidate needs to prove professional presence | LinkedIn/portfolio columns exist but are hidden | LinkedIn URL | URL input | Optional | HTTPS URL, LinkedIn host warning or generic URL policy | Profile, CV builder, employer candidate view | Expose existing `linkedin_url` | P2 |
| Candidate needs to showcase projects/design/code | Portfolio column exists but is hidden | Portfolio URL | URL input | Optional | HTTPS URL, max length | Profile, CV builder, employer candidate view | Expose existing `portfolio_url` | P2 |
| Language-dependent roles need language signal | No language field and no candidate language model | Languages with proficiency | Structured list or table | Optional | Language enum/free text plus proficiency enum | Profile, CV builder, job matching if employers use it | New table or typed JSON if product needs language matching | P2 |
| Candidate applies repeatedly with similar preferences | Preferred location is per-application only and job preference fields do not exist | Job preferences | Structured profile subdocument | Optional | Desired roles, locations, work mode, salary range with min <= max | Candidate home/profile, recommendations/search defaults | New DTO/columns/table; only add if used by search/recommendations | P3 |

## Business Rule Enforcement Matrix

| Rule | Frontend | Backend | Database | Assessment |
|---|---|---|---|---|
| Candidate route access | Protected route checks stored role | Candidate APIs require candidate/auth context | N/A | Good backend enforcement; frontend can briefly trust stale local role |
| Email verification before candidate actions | Login blocks unverified users via backend error | `requireCandidate` checks email verified and active | User status/email columns | Good |
| Duplicate application | Apply button disabled after loaded `job.applied` | `existsByCandidateIdAndJobId` conflict | `applications_job_seeker_unique` | Strong |
| Expired/closed job apply | Public search hides expired jobs | Submit rejects non-published or expired jobs | Job status/deadline | Strong |
| Candidate CV ownership | UI only lists own CVs | Lookup by candidate id | Resume ownership trigger | Strong |
| Application requires CV | UI requires selected resume/file | Service can default to candidate default uploaded CV | Resume FK nullable | Ambiguous API contract |
| Profile completeness before apply | Profile badge and apply errors | `isApplyReady` checks name/phone/location/skills/CV | None | Backend-only is safe but UX could guide earlier |
| Saved-job uniqueness | Detail toggles saved state | Duplicate save is no-op | `saved_jobs_job_seeker_job_unique` | Strong |
| Report public job only | UI on public detail | Service rejects non-public and duplicate pending reports | Job reports table | Strong |
| Reporter identity completeness | UI disables submit without name/phone | Service requires snapshot name/phone | Snapshot columns in reports | Strong |
| Offer initial response state | Buttons depend on shown status | Service accepts boolean and rewrites status without requiring `sent` | Status checks in job offer schema only | Weak |
| Interview candidate response enum | Button actions | DTO regex validates response values | Schedule status fields | Enum validation good; state/note rules can improve |
| Password change/deactivation token invalidation | Current tab clears after deactivation | No token revocation/version check found | N/A | Weak for already issued tokens |

## Critical Missing Functionality

No P0 Candidate functionality was confirmed missing in this second pass. Core Candidate actions exist: register/login, profile, CV management, job search/detail, save, apply, applications, notifications, and subscription. The highest-risk gaps are P1 because they affect trust, data quality, or scaling, not because the main flow is entirely absent.

## Important Missing Functionality

The important missing capabilities are:

- F-001: structured and validated Candidate profile sections.
- F-002: exposed professional profile fields already present in the database.
- F-006: complete job detail information from existing API fields.
- F-009: pagination/error recovery for Candidate-owned lists.
- F-011: immutable submitted CV visibility in application detail.
- F-016: token invalidation after sensitive account changes.

## Missing Fields

Confirmed missing fields from Candidate UI/API despite existing schema/entity support:

- `headline`
- `years_of_experience`
- `experience_level`
- `linkedin_url`
- `portfolio_url`

Fields worth adding only if they power a Candidate-facing decision or matching feature:

- languages/proficiency
- job preferences, such as desired roles, preferred locations, work mode, and desired salary
- GitHub or additional professional links for technical portfolios

## Incorrect Fields

- Profile section fields use generic `title`, `organization`, `time`, and `description` for education, work experience, projects, and certifications.
- Application `preferredLocation` is required in the modal but optional in the backend request/service.
- Application resume reference is ambiguous because the API permits `cvId`, `cvVersionId`, both, or neither, while the UI behaves like exactly one is chosen.
- Job search exposes a `relevance` sort option, but backend ordering only implements salary/deadline/newest behavior.

## Unnecessary Fields

- `allowAi` and `agreePolicy` are unnecessary as blocking application fields unless consent version/timestamp is persisted and used.
- Candidate registration role select may be unnecessary on a Candidate-specific registration path. It is valid for a shared signup path, but it creates wrong-role risk for Candidate acquisition.

## Validation Problems

- `CandidateProfileRequest` has no bean validation and uses `List<Object>` sections.
- `CandidateController.updateProfile` does not apply `@Valid`.
- CV builder `title` has `@NotBlank` only and no maximum length.
- Job report DTO has no bean validation, though service validates reason and profile completeness.
- Application submit request has `@NotNull jobId` but no `@NotBlank`, no UUID constraint, and no one-of resume validation.
- Interview response status is correctly regex-validated; the remaining issue is conditional note/state validation.
- Offer responses use boolean query params and lack initial-response state validation.

## Business Logic Problems

- Initial offer response can change offer status without checking the offer is still in `sent`.
- Password change and account deactivation do not revoke or invalidate existing JWTs.
- Application CV fallback is implicit: if no `cvId` or `cvVersionId` is supplied, backend uses the default uploaded CV. This conflicts with the UI's explicit resume-selection behavior and can surprise API clients.
- Profile apply-readiness is enforced on backend, but the UI does not guide the Candidate to each missing requirement at the apply CTA.

## Data Model Problems

- `CvVersion` and `CandidateCv` are separate JPA entities mapped to the same `resumes` table. `ApplicationService.submit` looks up a builder `CvVersion`, then also looks up a `CandidateCv` by the same id. This works only because both map the same table and is fragile.
- Profile structured sections are JSONB arrays of arbitrary objects, so validation, CV rendering, matching, and employer display cannot rely on stable keys.
- `CandidateProfile` contains useful professional fields that are not present in `CandidateProfileRequest` or `CandidateProfileResponse`.
- Job response company data is limited to id/name/website/location/logo while frontend types expect richer company metadata.

## Search/Filter Problems

- Category is accepted by API and represented in frontend types/service, but not parsed from URL or shown in the `/jobs` UI.
- Job type and work mode are returned by `JobResponse` and stored on jobs, but cannot be filtered by Candidates.
- Salary inputs allow `minSalary > maxSalary`, creating avoidable no-result confusion.
- `relevance` sort is offered in UI but backend currently falls back to newest ordering.
- Skills filter is a comma string and requires exact skill-name matches after parsing; a tokenized autocomplete would reduce mismatch.

## Job Detail Problems

- Job detail omits `skills`, `benefits`, `jobType`, `workMode`, `deadline`, `workingTime`, `vacancies`, `salaryType`, `viewsCount`, and company location detail although `JobResponse` includes these fields.
- Company information is only a compact summary. `CompanyResponse` itself is limited, so the API may also need richer public company data.
- Detail loading has no explicit error/not-found state.
- After successful application, Candidate sees a success message but no direct link to the created application record.

## Application Problems

- Application submit DTO does not enforce exactly one resume reference.
- UI-only consent checkboxes are not persisted.
- Application list is unpaged and lacks error state.
- Application detail shows CV file name or builder title, but not the exact submitted uploaded file/download or builder snapshot content.
- `WITHDRAWN` is mapped by backend but not labeled/colored in `statusLabels`/`statusColors`.
- Offer response initial transition lacks backend state validation.

## Saved Job Problems

- Saved jobs list has no direct unsave action.
- Saved jobs list is unpaged and has no error state.
- Saved jobs are mapped from whatever saved job records return; the UI has no explicit stale, expired, closed, or deleted job state.
- There is no load-more/search/filter capability for large saved lists.

## Recommended Improvements

| ID | Feature | Current behavior | Problem | Evidence | Recommended behavior | Candidate benefit | Priority | Effort estimate | Database impact | API impact | UI impact |
|---|---|---|---|---|---|---|---|---|---|---|---|
| F-001 | Profile sections | Education/work/projects/certifications are generic object arrays with `title`, `organization`, `time`, `description` | Data is hard to validate, render, search, or reuse in CVs | `frontend/src/App.tsx:2999-3028`; `CandidateProfileRequest.java:11-14`; `V15__persist_candidate_profile_sections.sql:1-5` | Replace `List<Object>` with typed DTOs or JSON schema per section | Candidate creates reusable profile/CV data instead of ambiguous notes | P1 | M | Optional if JSONB typed; larger if normalized tables | Add typed request/response and validation | Section-specific inputs for school/degree/company/role/dates/current/etc. |
| F-002 | Professional profile fields | Entity/schema has headline, years, level, LinkedIn, portfolio but UI/API omit them | Candidate cannot present key recruiting signals | `CandidateProfile.java:34-54`; `V1__initial_schema.sql:83-96`; `CandidateProfileResponse.java:6-17`; `frontend/src/App.tsx:2925-2973` | Expose these fields in DTOs and profile UI | Better employer screening and matching | P1 | M | Use existing columns | Add fields and validation to request/response | Add compact professional profile section |
| F-003 | Profile validation | Backend accepts loose strings and arbitrary section arrays | Invalid dates, overlong text, empty sections, and malformed URLs can persist | `CandidateController.java:34-36`; `CandidateProfileRequest.java:7-16`; `CandidateService.java:65-78` | Add `@Valid`, `@Size`, URL/date/phone/section constraints and mirror them in UI | Fewer failed applications and cleaner candidate profile | P1 | M | Possibly constraints after data cleanup | Request validation and error codes | Inline validation and character counts |
| F-004 | Application resume contract | UI requires explicit resume/file; backend permits no id and falls back to default CV | API behavior can submit an unintended CV | `frontend/src/App.tsx:368-383`; `ApplicationSubmitRequest.java:6-10`; `ApplicationService.java:91-105` | Enforce exactly one of `cvId` or `cvVersionId`, or document/default visibly | Candidate controls which CV is submitted | P1 | S | None | Add one-of validation or explicit default flag | Keep modal explicit and align errors |
| F-005 | Application consent | `allowAi` and `agreePolicy` gate submit in browser only | Candidate consent/acknowledgement cannot be audited | `frontend/src/App.tsx:337-391`; `frontend/src/App.tsx:538-544`; `candidateService.ts:65-79`; `ApplicationSubmitRequest.java:6-10` | Persist consent version/timestamp or remove blocking fields | Avoids false trust and compliance ambiguity | P1 | M | Add application consent columns/table if kept | Add consent fields to submit DTO | Show current consent policy and submitted state |
| F-006 | Job detail completeness | Detail shows description, requirements, location, salary, experience, company name | Candidate lacks enough information to decide whether to apply | `frontend/src/App.tsx:2353-2413`; `JobResponse.java:7-35`; `DtoMapper.java:156-185` | Display benefits, skills, type, work mode, deadline, working time, vacancies, salary type, company location | Better apply decisions and fewer low-fit applications | P1 | S | None | None if response already enough | Add sections and hierarchy to detail page |
| F-007 | Company information | Job detail uses limited company summary | Candidate cannot evaluate employer credibility from job detail | `DtoMapper.java:93-96`; `CompanyResponse.java:3-9`; `frontend/src/App.tsx:2360-2361` | Return/display verified status, website, location branches, industry, size where public | Higher trust and scam avoidance | P2 | M | None if existing company columns suffice | Expand public company response or add company detail endpoint | Add company panel/link |
| F-008 | Job search filters | UI lacks category/job type/work mode controls | Candidate cannot narrow by available job data | `JobController.java:25-37`; `jobService.ts:8-23`; `job.ts:28-34,101-106`; `frontend/src/App.tsx:1963-2058` | Add category select and job type/work mode filters; parse/persist URL params | Faster discovery of relevant jobs | P2 | M | None for category; add API params for job type/work mode | Add filters in controller/service where missing | Add controls and URL state |
| F-009 | Candidate list pagination | Saved jobs, applications, notifications, CVs are unpaged | Large histories can become slow and unusable | `CandidateService.java:92-97`; `CandidateService.java:213-250`; `ApplicationService.java:139-145`; `frontend/src/App.tsx:3625-4155` | Use paged endpoints or capped "load more" responses | Candidate can manage long-term history | P1 | M | Indexes likely already mostly present | Return page metadata | Add pagination/load-more/error states |
| F-010 | Job search validation/sort | Salary min/max not cross-validated; `relevance` sort falls back to newest | Candidate may see confusing no-result or misleading sort behavior | `frontend/src/App.tsx:2008-2058`; `JobService.java:963-970` | Validate min <= max and implement/remove relevance sort | Search results feel predictable | P2 | S | None | Maybe add relevance implementation | Inline validation and sort label fix |
| F-011 | Application detail submitted CV | Detail shows CV name/title only | Candidate cannot confirm exact submitted CV content later | `ApplicationResponse.java:10-13`; `DtoMapper.java:204-218`; `frontend/src/App.tsx:3834-3864` | Expose download for submitted uploaded CV and read-only builder snapshot/version timestamp | Candidate has confidence in what employer received | P1 | M | Existing snapshot/file fields may help; may need immutable CV snapshot references | Add submitted resume representation | Add submitted CV panel |
| F-012 | Application preferred location | UI requires preferred location, backend stores optional | Direct API can bypass required Candidate data | `frontend/src/App.tsx:374-382`; `ApplicationSubmitRequest.java:9`; `ApplicationService.java:112` | Align requiredness across UI/API or make it optional in both | Consistent application record | P2 | S | Optional `NOT NULL` only after migration if required | DTO validation if required | Match UI indicator/error |
| F-013 | Application status labels | Backend maps `withdrawn` to `WITHDRAWN`; UI labels omit it | Status may show raw enum without intended color/copy | `DtoMapper.java:277-289`; `frontend/src/App.tsx:73-95` | Add `WITHDRAWN` label/color and audit all statuses from workflows | Candidate understands application state | P2 | S | None | None | Add constants |
| F-014 | Offer response state | Candidate initial offer response accepts boolean query params and rewrites status | Repeated/stale requests can mutate non-sent offers | `ApplicationWorkflowController.java:91-99`; `ApplicationWorkflowService.java:267-288` | Use request DTO with command enum and require current status `sent` | Prevents accidental or stale offer decisions | P1 | S | None | Add DTO validation/state errors | Disable/refresh stale actions |
| F-015 | Interview response rules | Response enum is validated, but reschedule note is optional server-side and state is not guarded | Candidate can request reschedule without actionable detail; stale responses can overwrite status | `InterviewCandidateResponseRequest.java:7-10`; `ApplicationWorkflowService.java:108-121`; `frontend/src/App.tsx:3993-4010` | Require note for `request_reschedule` and validate allowed current states | Clearer scheduling and fewer invalid transitions | P2 | S | None | Conditional validation | Keep existing modal requirement and handle backend errors |
| F-016 | Account security | Password change/deactivation update password/status but do not revoke issued JWTs | Old tokens may remain valid until expiry | `AuthService.java:290-327`; `AuthController.java:73-76` | Add token version, revocation list, or short-lived access/refresh tokens | Better account control after compromise or deactivation | P1 | M | Add token version/revocation storage if chosen | JWT validation changes | Re-login messaging after sensitive changes |
| F-017 | CV upload validation | CV upload checks extension/MIME and size | Renamed or active-content PDFs can pass basic checks | `CandidateService.java:357-368`; `frontend/src/App.tsx:345-352`; `frontend/src/App.tsx:3482-3487` | Verify PDF signature, normalize/private storage, consider malware scanning | Safer CV storage and employer viewing | P1 | M | None to moderate for scan metadata | Validation errors | Same constraints in CV page and apply modal |
| F-018 | Saved jobs UX | Saved list only opens detail | Candidate cannot quickly remove stale saved jobs | `frontend/src/App.tsx:3625-3668`; `CandidateService.java:213-241` | Add direct unsave, stale/expired labels, and retry/pagination | Easier saved-job cleanup | P2 | S | None | Maybe paged saved-job response | Add actions and state handling |
| F-019 | Job report description | Service validates reason/profile but not description length | Very long descriptions can be stored and displayed poorly | `JobReportRequest.java:3-6`; `JobReportService.java:51-60`; `frontend/src/App.tsx:2503-2521` | Add DTO/service length limit and UI character count | Cleaner reports and admin review | P2 | S | Optional DB constraint | Add validation annotation/service check | Add `maxLength` and count |
| F-020 | Search category URL state | Type/service support category but URL parser omits it | Shared/search URLs lose category even if added by external links | `job.ts:101-106`; `jobService.ts:19`; `frontend/src/App.tsx:782-791` | Parse and persist `category` in `jobFiltersFromParams` | Back navigation and shared links work | P2 | S | None | None | URL parser/update only |
