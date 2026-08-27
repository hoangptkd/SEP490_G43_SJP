# Candidate Implementation Roadmap

Ngay cap nhat: 2026-08-10

Pham vi: roadmap cai thien Candidate dua tren `docs/audit/CANDIDATE_IMPROVEMENT_BACKLOG.md`. Khong bao gom AI interview/practice improvements. Thu tu uu tien duoc sap theo: security/data integrity -> broken/core functionality -> business logic -> data model -> backend/API -> frontend -> UX/navigation/forms -> responsive/accessibility -> performance -> polish.

## Nguyen tac thuc hien

1. Khong sua UI truoc khi contract backend/API chua ro neu tinh nang can data moi.
2. Moi thay doi co API/DB can co integration test va manual QA flow Candidate.
3. Cac refactor lon chi nen lam sau khi da co smoke test cho core Candidate routes.
4. Uu tien root cause thay vi fix cosmetic rieng le.
5. Khong implement cac muc AI-only trong roadmap nay.

## Phase 0 - Preflight va test baseline

Muc tieu: tao net an toan truoc khi sua cac flow Candidate quan trong.

| Order | Backlog ID | Work | Dependencies | Output |
|---:|---|---|---|---|
| 0.1 | All P1 | Tao/kiem tra smoke checklist cho Candidate: login, profile save, CV upload, job search, detail, save, apply, applications, notifications. | None | Manual QA script hoac checklist co fixture account. |
| 0.2 | CIB-030 | Neu co kha nang, them route-level smoke tests nho truoc refactor. | None | Test baseline de bat regression. |
| 0.3 | CIB-003/CIB-004 | Kiem tra env local/deploy hien tai truoc khi doi config/upload ignore. | None | Danh sach env can cap nhat. |

## Phase 1 - Critical Security And Data Integrity

Muc tieu: dong cac rui ro token, secret, upload truoc khi mo rong UX/function.

| Order | Backlog ID | Work | Dependency sequence | Notes |
|---:|---|---|---|---|
| 1.1 | CIB-003 | Loai bo secret/default key khoi `application.yml`; cap nhat env example/deploy docs. | Config -> startup validation -> QA payment disabled/enabled | Nen lam dau tien vi effort nho va risk bao mat ro. |
| 1.2 | CIB-001 | Chon va implement token invalidation strategy. | Database if token version -> backend/domain -> JWT validation -> auth API behavior -> frontend session messaging -> tests | Neu dung token version, them column/version claim va validate voi DB/current user status. |
| 1.3 | CIB-002 | Them rate limiting cho auth public endpoints. | Backend filter/service -> API 429/error shape -> tests -> frontend copy neu can | Nen throttle theo IP + email/identifier, khong leak account existence. |
| 1.4 | CIB-004 | Tang cuong CV upload validation va storage hygiene. | Storage policy -> `.gitignore`/upload dir -> backend validation -> optional scan metadata -> frontend error parity -> QA adversarial files | Can bao dam CV cu/fallback local van co migration/recovery path neu dang dung. |

## Phase 2 - Application Business Logic

Muc tieu: dam bao Candidate nop ho so va phan hoi offer dung contract, co audit trail.

| Order | Backlog ID | Work | Dependency sequence | Notes |
|---:|---|---|---|---|
| 2.1 | CIB-005 | Chuan hoa resume reference khi apply. | Backend DTO validation -> service rule -> API error codes -> frontend apply modal/service -> integration/manual QA | Nen lam truoc submitted CV visibility de contract ro rang. |
| 2.2 | CIB-006 | Quyet dinh persist hay remove consent checkbox; neu persist thi them metadata. | Product/legal copy -> database migration -> DTO/API -> service save -> frontend submit/detail -> tests | Neu chua co yeu cau compliance, remove blocking checkbox la phuong an nho hon. |
| 2.3 | CIB-007 | Hien thi exact submitted CV trong application detail. | Data representation -> backend response/download endpoint -> frontend panel -> QA uploaded/builder cases | Phu thuoc CIB-005; neu builder snapshot can immutable thi co the can DB/API change. |
| 2.4 | CIB-008 | Guard offer response state. | Backend state machine -> DTO command -> frontend service -> UI disable/stale refresh -> tests | Uu tien truoc UX polish cho application detail. |
| 2.5 | CIB-010 | Go side effect mark-all-read khoi nav. | Frontend only -> manual notification QA | Effort nho, nen lam trong batch business/data integrity vi no mat triage state. |

## Phase 3 - Candidate Profile Data Model

Muc tieu: lam profile tro thanh nguon du lieu dang tin cay cho CV, apply readiness, search/recommendation.

| Order | Backlog ID | Work | Dependency sequence | Notes |
|---:|---|---|---|---|
| 3.1 | CIB-011 | Chot profile schema: typed JSONB sections hay normalized tables. | Product/data decision -> migration plan -> DTO schema -> validation rules | Quy dinh truoc khi viet UI de tranh sua lai form nhieu lan. |
| 3.2 | CIB-011 | Expose hidden fields co san: headline, experience years/level, LinkedIn, portfolio. | Backend DTO/mapper/service -> frontend types -> profile UI -> validation/manual QA | Co the ship som hon typed sections vi DB da co fields. |
| 3.3 | CIB-011 | Typed sections va tokenized skills. | Database/JSON schema if needed -> backend validation -> frontend section forms/skill picker -> migration/backfill -> tests | Day la phan effort L va risk cao nhat cua profile. |
| 3.4 | CIB-012 | Apply-readiness checklist. | Backend/profile response if needed -> frontend dashboard/profile/apply CTA | Nen dung rules moi tu CIB-011 de checklist khong sai. |

## Phase 4 - Backend/API For Lists And Search

Muc tieu: sua contract data truoc khi frontend lam pagination/filter UX.

| Order | Backlog ID | Work | Dependency sequence | Notes |
|---:|---|---|---|---|
| 4.1 | CIB-016 | Dinh nghia shared page response cho Candidate lists. | API design -> backend controller/service/repository -> frontend service types | Uu tien applications, notifications, saved jobs; CVs co the cap/load-more neu scope nho. |
| 4.2 | CIB-016 | Them application list status/search/page. | Backend query -> API -> frontend URL/service -> QA | Route nen dung `page`, `status`, `search`. |
| 4.3 | CIB-017 | Mo rong job search filter contract. | Backend params for jobType/workMode if needed -> category URL parser -> frontend controls -> QA | Remove hoac implement `relevance` trong cung batch de tranh misleading sort. |
| 4.4 | CIB-029 | Them index/query optimization phu hop voi filter moi. | DB migration indexes -> query plan -> performance test | Lam sau khi filter contract chot de index dung query that. |

## Phase 5 - Frontend Functionality

Muc tieu: dua API moi vao Candidate UI theo huong mature web app.

| Order | Backlog ID | Work | Dependencies | Notes |
|---:|---|---|---|---|
| 5.1 | CIB-016 | UI pagination/load-more/error/retry cho saved jobs, applications, notifications, CVs. | Phase 4.1, 4.2 | Applications can co status/search; notifications can co unread filter sau. |
| 5.2 | CIB-017 | Job search filters: category/job type/work mode, active chips, clear one/all. | Phase 4.3 | Giu URL state cho filters co ich refresh/share/back. |
| 5.3 | CIB-018 | Search salary validation, stale request handling, out-of-range page recovery. | CIB-017 nen gan xong | Co the lam chung voi search UI batch. |
| 5.4 | CIB-009 | Job detail metadata sections. | Mapping label enum/job fields | FE-only neu response da du fields. |
| 5.5 | CIB-020 | Saved jobs direct unsave/apply/stale labels. | CIB-016 service shape | Can optimistic rollback va failure state. |
| 5.6 | CIB-021 | Candidate company detail page/panel. | Public company API decision | Chi hien public-safe company fields. |

## Phase 6 - UX, Navigation, Forms

Muc tieu: lam Candidate app hanh xu nhat quan, de recover, khong mat context.

| Order | Backlog ID | Work | Dependencies | Notes |
|---:|---|---|---|---|
| 6.1 | CIB-013 | Shared form error/focus pattern cho auth/profile/account/apply/search/report. | CIB-011/CIB-014 field rules | Nen uu tien forms dai va core flow truoc: profile, apply, account. |
| 6.2 | CIB-014 | Align requiredness/limits application/report fields. | Product decision preferredLocation | Lam gan CIB-013 de UI/API loi nhat quan. |
| 6.3 | CIB-015 | Auth recovery UX: password checklist, reset invalid token, verify resend. | API resend if needed | P2 nhung nen lam truoc release neu auth polish quan trong. |
| 6.4 | CIB-019 | Return context va scroll restoration cho job/application detail. | CIB-016 application URL state | Dam bao Back tu page 4/filter tra dung context. |
| 6.5 | CIB-023 | Shared status/feedback pattern va status labels. | CIB-016/CIB-013 | Add `WITHDRAWN` va centralize Candidate status display. |
| 6.6 | CIB-024 | Sua Cover Letter menu label hoac build templates. | Product decision | Neu khong build templates, remove/rename la quick win. |

## Phase 7 - Accessibility And Responsive

Muc tieu: sua cac problem behavior/mobile/accessibility sau khi form/list/search core da on dinh.

| Order | Backlog ID | Work | Dependencies | Notes |
|---:|---|---|---|---|
| 7.1 | CIB-025 | Accessible dialog primitive cho ActionModal/apply modal. | None, but easier after shared components exist | Nen lam truoc CIB-026. |
| 7.2 | CIB-026 | Deactivate confirmation. | CIB-025, CIB-001 | Confirmation nen noi ro token/session effect sau CIB-001. |
| 7.3 | CIB-027 | Popover/menu/table/status/icon accessibility. | CIB-023, optional CIB-030 | Test keyboard va screen reader. |
| 7.4 | CIB-028 | Mobile nav/filter/table/modal behavior. | CIB-017, CIB-025 | Test 360x640, 375x812, 768x1024, 1024x768, 1366x768. |

## Phase 8 - Architecture And Performance

Muc tieu: giam risk maintainability va cai thien scalability sau khi behavior core on dinh.

| Order | Backlog ID | Work | Dependencies | Notes |
|---:|---|---|---|---|
| 8.1 | CIB-031 | Chuan hoa async loading/error/stale handling. | CIB-016, CIB-023 | Co the dung query abstraction nhe thay vi ad-hoc effects. |
| 8.2 | CIB-030 | Tach `App.tsx` thanh routes/components va lazy-load. | Smoke tests Phase 0, shared primitives from earlier phases | Refactor lon, risk cao; khong nen lam truoc khi P1 logic chua xong. |
| 8.3 | CIB-029 | Job search performance measurement/index/N+1 follow-up. | CIB-017 filter contract | Neu dataset nho, co the defer sau release nhung can measurement. |

## Phase 9 - P3 Polish / Future Convenience

Muc tieu: chi lam neu core quality da dat.

| Order | Backlog ID | Work | Dependencies | Notes |
|---:|---|---|---|---|
| 9.1 | CIB-032 | Public jobs page-size/first-last pagination neu can. | Usage data/product decision | Khong release blocker. |
| 9.2 | CIB-033 | Icon/copy/loading polish. | CIB-030 helpful | Lam sau accessibility primitives de khong sua hai lan. |

## Release Gate Recommendation

## Must Fix Before Release

- CIB-001: JWT invalidation/session status.
- CIB-003: secret hygiene.
- CIB-004: CV upload/storage hygiene.
- CIB-005: application resume contract.
- CIB-006: application consent persist/remove.
- CIB-007: submitted CV visibility.
- CIB-008: offer response state guard.
- CIB-009: job detail complete fields.
- CIB-010: notification navigation must not mark all read.
- CIB-011: at least expose/validate core professional profile fields; typed sections can be staged if schedule requires.
- CIB-016: at least applications and notifications pagination/error recovery for production-like data.
- CIB-025: accessible modal baseline for apply/delete/destructive dialogs.

## Strongly Recommended Before Release

- CIB-002: auth rate limiting.
- CIB-012: profile completion checklist.
- CIB-013: field-level validation/focus for core forms.
- CIB-017/CIB-018: search/filter/sort predictability.
- CIB-019: return context/scroll restoration.
- CIB-026: deactivate confirmation.
- CIB-028: mobile Candidate nav/filter/table verification.

## Can Ship After Release

- CIB-014: secondary form limit parity.
- CIB-015: auth recovery refinements.
- CIB-020: saved-job direct actions.
- CIB-021: company detail page.
- CIB-022: job alerts/preferences.
- CIB-023: global feedback/status standardization.
- CIB-027: remaining accessibility polish beyond modal/form basics.
- CIB-029/CIB-030/CIB-031: performance/architecture improvements if no current measurable bottleneck.
- CIB-032/CIB-033: optional polish.

## Suggested Implementation Batches

Batch A - Security/data integrity:
CIB-003, CIB-001, CIB-002, CIB-004.

Batch B - Application correctness:
CIB-005, CIB-006, CIB-007, CIB-008, CIB-010.

Batch C - Profile data:
CIB-011, CIB-012, then CIB-013 profile-specific validation.

Batch D - Lists/search/job detail:
CIB-016, CIB-017, CIB-018, CIB-009, CIB-020.

Batch E - UX/accessibility/mobile:
CIB-025, CIB-026, CIB-019, CIB-023, CIB-027, CIB-028.

Batch F - Product/performance/architecture:
CIB-021, CIB-022, CIB-029, CIB-031, CIB-030, CIB-032, CIB-033.

## QA Matrix

| Area | Required manual QA |
|---|---|
| Auth/session | Old token after logout/password change/deactivate; inactive user direct API; rate limit behavior. |
| CV | Valid PDF, renamed non-PDF, oversized file, owner-only download, old local fallback if applicable. |
| Apply | Uploaded CV, builder CV, missing CV, duplicate apply, expired job, consent metadata/removal, success link/detail. |
| Application tracking | Pagination, status/search filters, detail return context, submitted CV display, offer response stale state. |
| Notifications | Sidebar navigation no longer marks read, mark one/all read success/failure, pagination/load-more. |
| Profile | Hidden fields, validation, typed sections, skills, dirty navigation, completion checklist. |
| Search/jobs | URL filters, category/jobType/workMode, salary validation, stale request, page out of range, Back from detail. |
| Accessibility | Keyboard-only modal/popover/forms, focus return, Escape behavior, screen reader status messages. |
| Responsive | 360x640, 375x812, 768x1024, 1024x768, 1366x768 for nav, filters, tables, apply modal, long job detail. |
