# Candidate Product Gap Analysis

Date: 2026-08-10

Scope: Candidate-facing product only. This analysis uses mature recruitment platforms such as TopCV, LinkedIn Jobs, Indeed, and VietnamWorks as product references, not as UI templates or a feature checklist.

Baseline reviewed:

- Candidate routes and screens in `frontend/src/App.tsx`.
- Candidate API clients in `frontend/src/services/candidateService.ts` and `frontend/src/services/jobService.ts`.
- Candidate/job/application domain types in `frontend/src/types/candidateDomain.ts` and `frontend/src/types/job.ts`.
- Backend Candidate, Job, Application, notification, CV, and workflow services/controllers.
- Existing Candidate audits in `docs/audit/CANDIDATE_DEEP_FUNCTIONAL_REVIEW.md`, `docs/audit/CANDIDATE_UX_BEHAVIOR_AUDIT.md`, `docs/audit/CANDIDATE_FIELD_AUDIT.md`, and `docs/audit/CANDIDATE_FUNCTIONAL_AUDIT.md`.

Reference patterns used:

- LinkedIn Help: job alerts, saved jobs/job tracker, applied jobs, application updates.
  - https://www.linkedin.com/help/linkedin/answer/a511279
  - https://www.linkedin.com/help/linkedin/answer/a513247
  - https://www.linkedin.com/help/linkedin/answer/a8684146
  - https://www.linkedin.com/help/linkedin/answer/a512329
- Indeed Help: My Jobs/application tracking and profile/preferences privacy.
  - https://www.indeed.com/help/job-seekers/articles/4412589551757-my-jobs-managing-applied-jobs
  - https://www.indeed.com/help/job-seekers/articles/360051197532-profile-and-preferences-what-do-i-share-with-employers
- VietnamWorks FAQ/app listings: saved jobs, job alerts, viewed/saved/applied jobs, public company profiles.
  - https://faq.vietnamworks.com/en/job/save-jobs-to-view-and-apply-later
  - https://faq.vietnamworks.com/en/job-alert/register-for-job-alert
  - https://faq.vietnamworks.com/en/find-dream-jobs-with-vietnamworks-mobile-app/view-the-viewed-saved-and-applied-jobs-on-vietnamworks-mobile-app
  - https://faq.vietnamworks.com/en/job/view-company-profile
- TopCV public product pages: CV creation, job search, saved/applied/matching jobs, company/job information.
  - https://www.topcv.vn/
  - https://topcv.com/do-it-yourself-tools

## Executive Summary

The Candidate product has the core job-seeker loop: authenticate, maintain a profile, manage CVs, search jobs, view job details, save jobs, apply, track applications, respond to interviews/offers, and receive notifications.

The biggest product gap is not that the system lacks a Candidate journey. It is that some parts still behave like a basic project implementation rather than a dependable recruitment product:

- Profile data is too loose for repeated matching, CV generation, and employer screening.
- Job detail omits decision-critical fields already available in the API/domain.
- Application history does not clearly preserve what the Candidate submitted.
- Long-running lists are not yet designed for real Candidate history.
- There is no saved search/job alert capability, even though repeated discovery is a core job-seeker behavior.
- Company trust information is too thin for candidates deciding where to apply.

No benchmark-only feature should be adopted blindly. Social networking, company reviews, salary insights, endorsements, messaging, and advanced marketplace features should stay out of current release scope unless project goals expand.

## Capability Comparison

| Capability | Current system | Common mature platform behavior | User problem | Is the difference important? | Recommendation |
|---|---|---|---|---|---|
| Candidate Profile | Candidate can edit name, phone, DOB, location, comma-separated skills, bio, education, work experience, projects, and certifications. Some professional fields exist in backend/entity but are not exposed. | Profiles usually capture professional headline, experience level/years, searchable skills, links, structured education/work history, and completion guidance. | Candidate cannot express their professional identity clearly; matching and CV generation rely on loose data. | Yes. This affects apply readiness, recommendations, and employer screening. | Expose existing professional fields and convert generic profile sections into typed, validated sections. |
| CV Management | Candidate can upload PDF CVs, set default, delete/download uploaded CVs, and create builder CV versions from profile snapshots. | Mature products let candidates manage multiple resumes, know which one is default, tailor resumes, and review the exact resume used per application. | Candidate can submit a CV but later cannot confidently inspect the exact submitted version/content. | Yes. This is a trust and auditability issue. | Add immutable submitted-CV visibility in application detail; keep CV tailoring advanced features as optional. |
| Job Search | Public job search supports URL-backed keyword, location, skills, salary, experience level, sort, and page. Category exists in API/type but is not exposed in the UI. | Search usually includes keyword/location plus strong filters such as category, job type, work mode, salary, experience, date posted, and result counts. | Candidate can narrow some results but cannot use all available job attributes. | Yes, for category/job type/work mode. Not all mature filters are necessary. | Add category, job type, and work mode filters if backed by data; avoid heavy advanced filters unless inventory warrants them. |
| Search Filters | Filters are form-based and URL-backed for several fields. Selected filters are not highly visible as removable chips. | Mature products keep filters discoverable, show active filters, allow clear one/clear all, and preserve/filter state across refresh/back. | Candidate may not know which filters are active or may need to re-enter frequent searches. | Medium. It improves efficiency but core search works. | Add visible active filter chips and clear-one behavior; keep URL state for shareable/search-state fields. |
| Job Recommendations | Candidate dashboard shows recommended jobs from backend service. Current recommendation signals appear basic, mostly around profile/searchable data such as skills/location. | Mature products use profile, preferences, activity, freshness, saved/applied behavior, and explicit job-seeking preferences. | Recommendations may feel generic and give the Candidate little control over relevance. | Medium. Important after core search/detail quality is stable. | Add explicit job preferences before investing in advanced recommendation logic. |
| Job Details | Detail shows title, company summary, description, requirements, location, salary, experience, save/apply/report. It omits several API fields such as skills, benefits, job type, work mode, deadline, working time, vacancies, salary type, and richer company details. | Job detail pages present decision-critical metadata, benefits, required skills, deadline/status, company trust signals, and related actions. | Candidate may apply without knowing work mode, deadline, benefits, or complete requirements. | Yes. This affects application quality and trust. | Display available job fields with clear sections and closed/expired states. |
| Saved Jobs | Candidate can save/unsave from job detail and open saved jobs list. | Saved jobs usually support direct unsave/archive, stale/expired labels, apply action, and sometimes stage tracking. | Saved jobs become hard to clean up and may not communicate whether a job is still viable. | Medium. Core save exists, but management is thin. | Add direct unsave from list, expired/closed labels, and pagination/load-more when data grows. |
| Job Application | Candidate selects/upload CV, enters preferred location and cover letter, then submits. Backend enforces active job, apply-ready profile, unique application, and CV ownership. Browser-only consent checkboxes are not persisted. Backend can default to default uploaded CV if no CV id/version is sent. | Mature products make the submitted resume explicit, show confirmation, prevent duplicates, and preserve application metadata. Consent/acknowledgement is auditable when it gates submission. | Candidate may not have a durable record of consent or exact resume; API behavior can differ from UI expectations. | Yes. This is a core application integrity gap. | Align resume selection contract and persist or remove consent gates. Link success state to the created application. |
| Application Tracking | Candidate can list applications and view detail with status, timeline, interviews, offers, and responses. The list is unpaged and lacks search/filtering. | Mature products centralize applied/saved/interview/archive stages, support status filters, and keep application history manageable. | Long-term Candidates cannot easily find, filter, or manage application history. | Yes for pagination/status filtering. Full job tracker stages are optional. | Add pagination and status filter/search. Keep complex manual pipeline tracking for future unless project scope needs it. |
| Company Information | Job detail embeds a compact company summary. No confirmed Candidate company detail route. | Mature platforms let candidates inspect company profile, verification, website, location, industry, active jobs, and sometimes reviews/content. | Candidate cannot evaluate employer credibility or browse all jobs from the same company. | Medium to high depending on marketplace trust goals. | Add a Candidate-visible company detail page using existing employer/company data. Defer reviews/community content. |
| Notifications | Candidate can view notifications, mark one/all read, and see unread counts. Notifications are unpaged. | Mature products support notification preferences, job alerts, application updates, and manageable history. | Candidate can receive system updates but cannot control important alert types or handle large history. | Medium. Job alerts are more important than full preference center. | Add job alert notifications first; add preferences only for implemented notification channels. |
| Candidate Settings | Account security page exists for account/profile settings and subscription exists. | Mature products include privacy/searchability, communication preferences, default resume/application settings, and account security. | Candidate has limited control over how profile/CV/search preferences are used. | Medium. Some settings are future-scope depending on employer discovery. | Add default application/CV preferences and notification preferences only where product uses them. |

## Findings

Priority: P0 critical, P1 high, P2 medium, P3 low, FUTURE outside current release.

Complexity: S small, M medium, L large.

| ID | Category | Capability | Candidate problem solved | Current workaround | Necessary for project scope? | Complexity | Priority | Backend/data model required? | Recommendation |
|---|---|---|---|---|---|---|---|---|---|
| GAP-001 | MISSING CORE FEATURE | Structured professional profile | Candidate needs a reusable profile that can support matching, CV builder, employer screening, and application readiness. | Candidate can enter free-form/generic profile sections and comma-separated skills. | Yes. A recruitment product needs reliable candidate data. | M | P1 | Yes, at least DTO/schema validation; existing columns cover some fields. | Expose headline, years of experience, experience level, LinkedIn URL, and portfolio URL; type profile sections for education/work/projects/certifications. |
| GAP-002 | MISSING CORE FEATURE | Profile completion checklist | Candidate needs to know exactly what prevents applying or reduces match quality. | `applyReady` badge says ready/not ready, and backend returns apply errors. | Yes. It reduces failed application attempts. | S | P1 | No if based on existing apply-ready logic; maybe no schema work. | Add a profile completion panel listing missing name, phone, location, skills, and CV with links to fix each item. |
| GAP-003 | MISSING CORE FEATURE | Complete job detail data | Candidate needs enough information to decide whether to apply. | Candidate can infer from description/requirements, but many structured fields are hidden. | Yes. Job detail is core to application quality. | S | P1 | Usually no; `JobResponse` already includes many fields. | Display skills, benefits, job type, work mode, deadline, working time, vacancies, salary type, status/deadline, and company location when present. |
| GAP-004 | MISSING CORE FEATURE | Submitted CV visibility | Candidate needs proof of which resume/version was submitted. | Application detail shows file name or builder title only. | Yes. Application history must be trustworthy. | M | P1 | Maybe. Existing CV/file/version relations help; immutable snapshot/download may need endpoint changes. | Add a submitted resume panel with file/download or read-only builder snapshot, title, and submitted timestamp. |
| GAP-005 | MISSING CORE FEATURE | Application resume contract | Candidate must not accidentally apply with an unintended default CV. | UI requires explicit CV/upload, but backend can fallback to default CV when no id/version is supplied. | Yes. It protects application integrity. | S | P1 | Maybe DTO validation only. | Require exactly one resume reference or add an explicit `useDefaultCv` command visible in UI/API. |
| GAP-006 | MISSING CORE FEATURE | Application consent audit | Candidate consent/acknowledgement should be real if it blocks submission. | Browser checkboxes gate submit but are not sent to backend. | Yes if consent remains part of submit; otherwise remove it. | M | P1 | Yes if kept; application consent fields/table. | Persist consent version/timestamp/source or remove blocking checkboxes and keep only inline explanatory copy. |
| GAP-007 | MISSING CORE FEATURE | Scalable application tracking | Candidate needs to manage many applications over time. | Unpaged application list; detail exists. | Yes for realistic usage. | M | P1 | Yes, paged endpoint and query params. | Add server pagination, status filter, search by job/company, result count, and URL state for page/filter. |
| GAP-008 | MISSING CORE FEATURE | Scalable saved jobs/notifications/CV lists | Candidate history should not degrade as data grows. | Saved jobs, notifications, CVs are unpaged arrays. | Yes for release quality if seed/demo data can grow. | M | P1 | Yes for saved jobs/notifications; CVs can use cap or pagination. | Add paged or capped load-more APIs and consistent empty/error/loading states. |
| GAP-009 | USEFUL ENHANCEMENT | Job alerts and saved searches | Candidate should not repeat the same search daily. | Candidate can bookmark `/jobs` URLs manually; no app alert/search-save flow. | Strongly recommended, but not strictly required for first release. | M | P2 | Yes, saved search/job alert model and scheduled notification/email if enabled. | Let Candidate save current search filters as a job alert with notification frequency; start with in-app notifications before email. |
| GAP-010 | USEFUL ENHANCEMENT | Job preferences | Candidate needs control over recommendation/search defaults such as desired role, location, salary, job type, and work mode. | Profile location/skills influence recommendations; application has preferred location per submission. | Recommended if recommendations are emphasized. | M | P2 | Yes, preferences columns/table or JSON schema. | Add preferences as a profile subsection and use them in recommendations and default search filters. |
| GAP-011 | USEFUL ENHANCEMENT | Company detail page | Candidate needs employer credibility and context before applying. | Compact company summary in job detail. | Recommended for trust, especially if employers are public marketplace participants. | M | P2 | Maybe; depends on existing company fields and public endpoint. | Add Candidate company profile route with verification/status, website, industry, size, locations, description, and active jobs. |
| GAP-012 | USEFUL ENHANCEMENT | Search filters for category, job type, work mode | Candidate needs to narrow jobs by available structured data. | Search supports keyword/location/skills/salary/experience; category exists in service/type but not UI. | Recommended. | M | P2 | Maybe; category mostly exists, job type/work mode may need backend params. | Add filter controls and URL params for category, job type, and work mode; keep labels plain and understandable. |
| GAP-013 | USEFUL ENHANCEMENT | Saved job management | Candidate needs to clean up saved jobs and understand stale postings. | Saved list links to detail only; unsave exists on detail. | Recommended. | S | P2 | Maybe no backend beyond current unsave; stale state depends on job response. | Add direct unsave/apply actions from saved list and show expired/closed/unavailable labels. |
| GAP-014 | USEFUL ENHANCEMENT | Application success route | Candidate needs a clear next step after applying. | Success message appears on job detail/modal; no reliable link to application detail. | Recommended. | S | P2 | Maybe API should return created application id, which it already likely does. | After apply success, show `View application` action and set job state to already applied. |
| GAP-015 | USEFUL ENHANCEMENT | Default application settings | Candidate applying often needs default CV, preferred location, and reusable cover letter/answers. | Candidate can set default CV and enters application fields manually each time. | Optional for current scope. | M | P3 | Yes for reusable templates/preferences. | Keep default CV; defer reusable answers/templates unless application volume becomes a focus. |
| GAP-016 | USEFUL ENHANCEMENT | Cover letter management | Candidate menu says "Cover Letter cua toi" but routes to CV page. | Candidate can type a cover letter per application. | Not necessary unless cover letters are important in project rubric. | M | P3 | Yes if stored templates are added. | Either remove/rename the menu link or add basic saved cover letter templates. |
| GAP-017 | UX IMPROVEMENT | Active filter visibility | Candidate needs to know what is narrowing results. | Filters live in form inputs; clear all exists but no clear-one chips. | Yes for mature search behavior, but not core data model. | S | P2 | No. | Add active filter chips with remove-one and count; preserve URL behavior. |
| GAP-018 | UX IMPROVEMENT | Back-to-search context | Candidate opening a job from page 4 with filters should return to page 4. | Search page state is URL-backed, so browser Back generally preserves it when navigation starts from that URL. Direct job links do not know origin context. | Important for search usability. | S | P2 | No. | Preserve `location.state.from` and/or query return URL from job cards; show `Back to results` only when a known result context exists. |
| GAP-019 | UX IMPROVEMENT | Recommendation explainability | Candidate needs to know why jobs are recommended and how to improve them. | Dashboard shows recommendations without clear control/explanation. | Helpful, not required. | S | P3 | No for static explanations; yes for richer signals. | Add lightweight labels such as matching skills/location and link to edit profile/preferences. |
| GAP-020 | UX IMPROVEMENT | Notification preferences | Candidate should control noisy channels/types once alerts exist. | Mark read/all read exists; no preferences. | Not necessary until multiple notification types/channels exist. | M | P3 | Yes, preferences model if persisted. | Add preferences after job alerts/application updates are productized. |
| GAP-021 | UNNECESSARY FOR CURRENT SCOPE | Social feed/networking | Candidate social graph can support career discovery in large platforms. | No current substitute, but product is a recruitment workflow platform. | No. | L | FUTURE | Yes, major model and moderation work. | Do not build for current project. It would dilute scope and require moderation/privacy systems. |
| GAP-022 | UNNECESSARY FOR CURRENT SCOPE | Public endorsements/recommendations | Candidate can collect third-party endorsements on some professional networks. | Candidate can upload CV/profile; no endorsement flow. | No. | L | FUTURE | Yes. | Do not build. Structured profile and application quality matter more. |
| GAP-023 | UNNECESSARY FOR CURRENT SCOPE | Recruiter-candidate chat | Direct messaging can reduce email friction. | Notifications/interview/offer workflows provide structured communication. | No unless employer module already requires chat. | L | FUTURE | Yes, real-time delivery, abuse controls, moderation, retention. | Do not add for release; use structured status/interview/offer communication. |
| GAP-024 | UNNECESSARY FOR CURRENT SCOPE | Company reviews/community content | Reviews help candidates assess companies on large marketplaces. | Company summary exists but no community data. | No for current project. | L | FUTURE | Yes, review model, moderation, legal/privacy policy. | Defer. Use verified company data and public profile first. |
| GAP-025 | FUTURE FEATURE | Salary insights/market benchmarking | Candidate can evaluate whether compensation is competitive. | Salary range is shown when job provides it. | Not necessary for current scope. | L | FUTURE | Yes, salary dataset/analytics. | Consider only after enough reliable salary/job data exists. |
| GAP-026 | FUTURE FEATURE | Advanced recommendation learning | Candidate gets better matches from clicks, saves, applies, and dismissals. | Basic recommendations exist. | Not necessary before core data quality improves. | L | FUTURE | Yes, event tracking and ranking model. | First add profile/preferences and explainable rules; then collect activity signals. |
| GAP-027 | FUTURE FEATURE | Skill assessments/badges | Candidate can prove skill level beyond profile text. | AI interview exists, but no general skill proofing. | Future only. | L | FUTURE | Yes, assessment records and validation. | Defer unless assessment is a central differentiator. |
| GAP-028 | FUTURE FEATURE | Resume parsing/scoring optimization | Candidate can import CV data and improve ATS fit. | Candidate manually enters profile and can upload CV. | Future only unless CV quality is a release requirement. | L | FUTURE | Yes, parsing/scoring services and review UI. | Defer; prioritize profile structure and submitted-CV transparency first. |

## Categorized Findings

## MISSING CORE FEATURE

| ID | Finding | Priority | Release relevance |
|---|---|---|---|
| GAP-001 | Structured professional profile | P1 | Needed before release if recommendations/CV builder/employer screening are important. |
| GAP-002 | Profile completion checklist | P1 | Needed before release to prevent failed apply attempts. |
| GAP-003 | Complete job detail data | P1 | Needed before release because job detail drives applications. |
| GAP-004 | Submitted CV visibility | P1 | Needed before release for application trust. |
| GAP-005 | Application resume contract | P1 | Needed before release to avoid unintended CV submission. |
| GAP-006 | Application consent audit | P1 | Needed before release if consent gates remain. |
| GAP-007 | Scalable application tracking | P1 | Needed before release or shortly after, depending expected data volume. |
| GAP-008 | Scalable saved jobs/notifications/CV lists | P1 | Needed before release if production data can grow beyond demo scale. |

## USEFUL ENHANCEMENT

| ID | Finding | Priority | Release relevance |
|---|---|---|---|
| GAP-009 | Job alerts and saved searches | P2 | Strongly recommended; common job-seeker retention pattern. |
| GAP-010 | Job preferences | P2 | Strongly recommended if recommendations are shown prominently. |
| GAP-011 | Company detail page | P2 | Strongly recommended for trust and employer discovery. |
| GAP-012 | Category/job type/work mode filters | P2 | Recommended because the data model already contains part of it. |
| GAP-013 | Saved job management | P2 | Recommended for day-to-day Candidate cleanup. |
| GAP-014 | Application success route | P2 | Recommended for clarity after apply. |
| GAP-015 | Default application settings | P3 | Useful later for repeat applicants. |
| GAP-016 | Cover letter management | P3 | Useful only if cover letters are in scope; otherwise fix the menu label. |

## UX IMPROVEMENT

| ID | Finding | Priority | Release relevance |
|---|---|---|---|
| GAP-017 | Active filter visibility | P2 | Improves search confidence without backend work. |
| GAP-018 | Back-to-search context | P2 | Important for paged search results and browser Back behavior. |
| GAP-019 | Recommendation explainability | P3 | Helpful after recommendations depend on better profile/preferences. |
| GAP-020 | Notification preferences | P3 | Useful after job alerts and multiple notification types exist. |

## UNNECESSARY FOR CURRENT SCOPE

| ID | Finding | Priority | Reason |
|---|---|---|---|
| GAP-021 | Social feed/networking | FUTURE | Requires a different product model, moderation, and social graph. |
| GAP-022 | Public endorsements/recommendations | FUTURE | High complexity and weak fit for current recruitment workflow. |
| GAP-023 | Recruiter-candidate chat | FUTURE | Structured interview/offer workflows already solve near-term communication. |
| GAP-024 | Company reviews/community content | FUTURE | Requires moderation/legal/privacy policy; verified company profile is enough now. |

## FUTURE FEATURE

| ID | Finding | Priority | Reason |
|---|---|---|---|
| GAP-025 | Salary insights/market benchmarking | FUTURE | Needs reliable salary dataset and analytics. |
| GAP-026 | Advanced recommendation learning | FUTURE | Needs event tracking and enough usage data. |
| GAP-027 | Skill assessments/badges | FUTURE | Separate assessment product area. |
| GAP-028 | Resume parsing/scoring optimization | FUTURE | Useful later, but profile/data integrity should come first. |

## Must Have Before Release

- GAP-003: Show complete available job detail fields.
- GAP-004: Show exact submitted CV/file/version in application detail.
- GAP-005: Align application resume selection contract between UI and backend.
- GAP-006: Persist application consent or remove consent as a blocking submit requirement.
- GAP-002: Add a profile completion checklist tied to apply readiness.
- GAP-001: At minimum expose existing professional profile fields and validate core profile fields. Full typed section restructuring can be staged if release time is tight.
- GAP-007/GAP-008: Add pagination/load-more for application, saved job, notification, and other unbounded Candidate lists if production-like data volume is expected.

## Strongly Recommended

- GAP-009: Add saved searches/job alerts, starting with in-app notifications.
- GAP-010: Add job preferences and use them for recommendations/search defaults.
- GAP-011: Add a Candidate-visible company detail page.
- GAP-012: Add category, job type, and work mode filters where backend data supports them.
- GAP-013: Add direct unsave/stale-state handling to saved jobs.
- GAP-014: Link successful applications to the created application detail.
- GAP-017: Add active filter chips and clear-one interactions.
- GAP-018: Preserve return-to-search context for result-to-detail navigation.

## Nice to Have

- GAP-015: Default application settings beyond default CV.
- GAP-016: Cover letter templates/management if cover letters are a meaningful project requirement.
- GAP-019: Explain why each job is recommended.
- GAP-020: Notification preferences after job alerts exist.

## Not Needed For Current Project

- GAP-021: Social feed/networking.
- GAP-022: Public endorsements/recommendations.
- GAP-023: Recruiter-candidate chat, unless the employer module explicitly adds messaging.
- GAP-024: Company reviews/community content.

## Future Product Opportunities

- GAP-025: Salary insights/market salary benchmarking.
- GAP-026: Advanced recommendation learning from clicks, saves, applies, dismissals, and profile fit.
- GAP-027: Skill assessments and verified badges.
- GAP-028: Resume parsing, resume scoring, and application-specific CV optimization.

## Product Principle

The Candidate product should not become a clone of large recruitment platforms. The right release target is a focused, trustworthy job-seeker workflow:

1. Candidate can create a reliable profile and manage CVs.
2. Candidate can find relevant jobs with understandable filters.
3. Candidate can inspect enough job/company information to make an application decision.
4. Candidate can apply with a clear, auditable resume/consent contract.
5. Candidate can track application progress over time without losing context.

Features outside that loop should be deferred unless they directly solve a current Candidate problem.
