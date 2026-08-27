# Candidate Form And Field Audit

Trace basis: UI fields in `frontend/src/App.tsx`, API clients in `frontend/src/services/*`, backend request DTOs in `backend/src/main/java/com/sjp/recruitment/model/dto/request`, entities, and schema/migrations.

| Entity/Form | Field | Current Type | Required | Problem | Proposed Change | Reason | Priority |
|---|---|---:|---|---|---|---|---|
| Registration | email | UI string, DTO `@Email @NotBlank`, DB `citext unique` | Yes | Frontend relies mostly on HTML/email/backend. | Add inline frontend validation and autocomplete/name attributes. | Better form guidance and WCAG/form guideline parity. | P2 |
| Registration | password | UI password string, backend custom strength | Yes | Frontend copy only, not full enforced rule before submit. | Add rule checklist matching `AuthService.validatePassword`. | Prevent avoidable API failures. | P2 |
| Registration | role | UI enum CANDIDATE/EMPLOYER, backend `UserRole` | Yes | Candidate registration page also allows employer role. | If this is a candidate-focused registration path, separate role-specific entry points. | Reduces accidental wrong-role account creation. | P3 |
| Login | email/password | String | Yes | No session recovery if account is inactive except generic mapped errors. | Expose verified/inactive recovery state. | Improves auth recovery. | P2 |
| Reset password | token | Query string -> DTO string | Yes | Missing token is not blocked before submit. | Validate token presence on page load and show reset-link recovery. | Avoid dead-end form. | P2 |
| Candidate Profile | fullName | UI string -> `CandidateProfileRequest.fullName` -> `users.full_name` | Apply-ready yes, DTO no | No max length or nonempty validation. | `@Size`, trim, frontend limit. | Prevent invalid/overlong names. | P1 |
| Candidate Profile | phone | UI string -> `users.phone` | Apply-ready yes, DTO no | No phone format/length validation. | Store E.164 or country-aware normalized phone; validate frontend/backend. | Needed for employer contact/report identity. | P1 |
| Candidate Profile | dateOfBirth | UI date string -> `LocalDate` -> `job_seekers.date_of_birth` | No | No age/future-date validation. | Reject future dates and implausible ages; optional unless product needs it. | Privacy and data quality. | P2 |
| Candidate Profile | location | UI string -> DB text | Apply-ready yes, DTO no | Free text only; no autocomplete/structure. | Consider structured city/country or autocomplete. | Improves matching/search. | P2 |
| Candidate Profile | skills | Comma string -> `List<String>` -> `candidate_skills` + transient list | Apply-ready yes | UI uses comma text; backend normalizes duplicates but level fixed to `intermediate`. | Use tokenized multiselect backed by `skills`; support level/years if needed. | Avoid inconsistent skill strings and lost proficiency. | P1 |
| Candidate Profile | bio | Textarea string -> `job_seekers.summary` | No | No max length, no content guidance beyond placeholder. | Add `@Size`, character count, helper text. | Prevent huge profile payloads and improve readability. | P2 |
| Candidate Profile | headline | Entity field exists | No UI/DTO | Candidate cannot edit professional title/headline although entity and AI context use it. | Add field or remove from AI context/entity if not used. | Missing profile signal for job matching/AI. | P2 |
| Candidate Profile | linkedinUrl | Entity field exists | No UI/DTO | Missing UI/API despite schema support. | Add validated URL field. | Useful professional profile data. | P2 |
| Candidate Profile | portfolioUrl | Entity field exists | No UI/DTO | Missing UI/API despite schema support. | Add validated URL field. | Useful for tech/design candidates. | P2 |
| Candidate Profile | experienceYears | Entity int default 0 | No UI/DTO | Missing from profile form; job matching uses skills/location only. | Add structured experience years with min/max. | Common recruitment filter/signal. | P2 |
| Candidate Profile | experienceLevel | Entity enum-like DB check | No UI/DTO | Missing from UI despite DB supports levels. | Add enum select aligned to DB values. | Improves search/recommendation. | P2 |
| Candidate Profile | education | UI object list with title/org/time/description -> `List<Object>` JSON | No | No schema, dates, degree, field of study, validation. | Define typed DTO/schema: school, degree, field, start/end, current, description. | Prevent inconsistent JSON and unusable CV data. | P1 |
| Candidate Profile | workExperience | UI object list -> `List<Object>` JSON | No | No company/title/start/end/current validation; generic keys. | Define typed DTO/schema and date validation. | Required for CV and AI context quality. | P1 |
| Candidate Profile | projects | UI object list -> `List<Object>` JSON | No | No URL/role/tech stack structure. | Define typed schema with project name, role, technologies, URL, description. | Better CV and AI context. | P2 |
| Candidate Profile | certifications | UI object list -> `List<Object>` JSON | No | No issuer/date/credential URL structure. | Define typed schema. | Recruiter verification and CV consistency. | P2 |
| Candidate Profile | languages | Missing | N/A | Quality scope mentions languages, but no implemented field. | Add only if product needs language skill matching; use enum/proficiency. | Important for recruitment and AI interview language support. | P2 |
| CV Upload | file | File -> MultipartFile -> `resumes.file_*` | Yes | Frontend/backend check PDF extension/MIME; backend lacks PDF magic/AV. | Validate PDF signature, scan, store private. | Security/privacy. | P1 |
| CV Upload | defaultCv | Boolean | No direct UI field | First upload default; candidate can set default. | Keep, but clarify default CV in apply modal. | Candidate should know which CV applies. | P2 |
| CV Builder | title | String -> `CvVersionRequest.title` | Backend `@NotBlank @Size` expected by `@Valid` request | Need verify constraints in `CvVersionRequest`; current UI title editing only. | Enforce visible length/count in UI. | Prevent API rejection. | P2 |
| CV Builder | snapshot | `Record<string, unknown>` -> JSONB | Yes-ish | Snapshot generated from loose profile JSON; no field schema. | Build snapshot from typed profile DTO. | Prevent malformed CV content. | P1 |
| Job Search | search/location/skills | URL query strings -> API params | Optional | Skills as comma string, not multiselect. | Token multiselect/autocomplete with skill IDs/slugs. | Avoid mismatched skill text. | P2 |
| Job Search | minSalary/maxSalary | UI number -> BigDecimal params | Optional | No frontend min <= max validation. | Validate salary range before submit. | Avoid confusing no-result states. | P2 |
| Job Search | experienceLevel | Select values | Optional | UI values must match backend lowercase equality after normalization. | Centralize enum constants. | Prevent drift. | P2 |
| Job Search | category | API/type supported | Missing UI | Candidate cannot use category filter. | Add category select or remove from candidate contract. | Completeness. | P2 |
| Application | jobId | DTO string `@NotNull` -> UUID | Yes | `@NotNull` still allows blank string until parse error. | Use `@NotBlank` and UUID validation. | Cleaner contract. | P2 |
| Application | cvId/cvVersionId | DTO strings -> UUID | Optional one-of | DTO does not enforce one-of or mutual exclusion; service precedence chooses `cvId` first. | Add one-of validation. | Prevent ambiguous submissions. | P1 |
| Application | preferredLocation | String | Frontend requires; backend optional | Frontend/backend requiredness mismatch. | Align requiredness or make optional in UI. | Avoid direct API bypass of required UI rule. | P2 |
| Application | coverLetter | String max 2000 backend | Optional | UI shows count but textarea lacks `maxLength`; backend trims/rejects. | Add `maxLength=2000` and inline limit. | Prevent failed submissions. | P2 |
| Application | allowAi/agreePolicy | UI booleans only | UI blocks submit | Not sent to API or persisted. | Persist consent version/timestamp or remove as a blocking input. | Privacy/compliance mismatch. | P1 |
| AI Practice | targetRole | String, DTO `@NotBlank @Size(max=120)` | Yes | Defaults to "Java Backend Developer"; no role autocomplete. | Autocomplete from job titles/categories. | Better relevance. | P3 |
| AI Practice | skills | Comma text -> `List<String>` DTO `@NotEmpty @Size(max=12)` | Yes | UI can enter more than 12 skills then backend rejects. | Tokenize and enforce max 12 client-side. | Validation parity. | P2 |
| AI Practice | jobId/questionSetId | Optional strings | No | UI fixed/AI mode changes field but query state not URL-persisted. | Persist tab/mode/session state in URL or local draft. | Avoid accidental loss. | P3 |
| AI Answer | audio file | MediaRecorder File -> MultipartFile | Required for STT | Frontend file type depends on recorder; backend validates header/type. | Include expected question ID and duration metadata. | Prevent stale mapping. | P1 |
| AI Answer | transcript | Textarea string -> DTO `@NotBlank @Size(max=12000)` | Yes for answer | Manual mode editable, hands-free read-only; no max length UI. | Add max length and draft state. | Prevent API rejection/loss. | P2 |

## Missing Fields

- Languages/proficiency.
- Professional headline/title in UI/API despite entity support.
- LinkedIn URL and portfolio URL in UI/API despite entity support.
- Structured experience years and experience level in UI/API despite entity/schema support.
- Job preference fields such as desired role, salary, work mode, and preferred locations.
- Application consent/AI-processing acknowledgement persistence.

## Incorrect Fields

- Candidate profile section fields use generic `title`, `organization`, `time`, `description` for education, work experience, projects, and certifications; this is too generic for reliable CV rendering or AI context.
- Application `preferredLocation` is required in UI but optional in backend.
- Application `cvId` and `cvVersionId` are ambiguous optional strings instead of a one-of resume reference.

## Unnecessary Fields

- `allowAi` and `agreePolicy` are currently UI-only gating fields and are unnecessary unless persisted/audited.
- Public job `category` exists in the API/type contract but is not present in Candidate UI; either expose it or remove from the candidate-facing contract.

## Fields Requiring Better Validation

- `fullName`, `phone`, `dateOfBirth`, `location`, `bio`.
- Profile section JSON arrays.
- CV upload actual file content.
- Salary min/max.
- Cover letter max length.
- AI practice skills max count and per-skill length in UI.
- AI transcript max length in UI.

## Fields That Should Become Enum

- Candidate experience level.
- Candidate skill level.
- Job experience level/job type/work mode/salary type in shared UI constants.
- Interview response and offer response statuses.
- Language proficiency if languages are added.

## Fields That Should Become Structured Data

- Skills should be tokenized/selectable with canonical skill IDs/slugs.
- Education, work experience, projects, certifications should use typed DTOs and JSON schema.
- Location should be normalized or split into city/country/work mode where matching matters.
- Job preferences should be structured separately from profile and applications.

## Database Changes Required

- Add or expose existing profile columns: `headline`, `experience_level`, `years_of_experience`, `linkedin_url`, `portfolio_url`.
- Add candidate languages table or JSON schema if languages are in scope.
- Add application consent metadata if AI processing consent remains a blocking UI action.
- Consider JSON schema constraints or separate normalized tables for profile sections if search/reporting needs grow.

## API Contract Changes Required

- Add `@Valid` and field constraints to `CandidateProfileRequest`.
- Replace `List<Object>` profile section fields with typed request/response DTOs.
- Add one-of validation to application resume reference.
- Align `preferredLocation` requiredness across UI and backend.
- Add frontend max-length parity for backend-limited fields.
- Return paged responses for candidate lists where data can grow.
