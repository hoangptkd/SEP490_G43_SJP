# Candidate Master Audit

> Cập nhật 2026-08-11: nhiều phát hiện ngoài AI trong tài liệu này đã được khắc phục. Trạng thái tái kiểm tra theo từng CIB, bằng chứng và phần việc còn lại nằm ở `CANDIDATE_IMPROVEMENT_BACKLOG.md`. Các nhận định AI bên dưới chưa được khắc phục theo phạm vi đã thống nhất.

## Executive Summary

The Candidate side is substantially implemented across auth, profile, CVs, job search/detail, saved jobs, applications, notifications, subscription, and AI interview practice. The strongest implementation areas are backend ownership checks, public job pagination, application duplicate prevention, CV ownership, AI session ownership, answer/question uniqueness, and persisted AI interview results.

The main release risks are not missing screens; they are data quality, unbounded candidate lists, session/token revocation, file upload/privacy hardening, and AI evaluation validity. The AI interview flow is feature-rich but cannot be considered validated until question generation and answer evaluation are benchmarked against human-labeled data.

## P0 Critical Issues

No P0 release-blocking issue was proven from static review alone. Several P1 issues may become P0 depending on production policy for PII, AI scoring use, and real candidate CV files in the workspace.

## P1 High Priority Issues

- Generic unvalidated candidate profile JSON sections can store inconsistent education/experience/projects/certifications.
- Candidate lists such as applications, saved jobs, notifications, and CVs are unpaged/unbounded.
- Logout/password change/deactivation do not revoke JWTs.
- CV upload validation is too weak for private document upload.
- UI-only AI/application consent is not persisted.
- AI audio upload maps to backend current question without expected question ID, creating stale multi-tab risk.
- Unsaved AI audio/transcript can be lost on refresh/back.
- STT is configured as Vietnamese-only; mixed technical language requires benchmark/config review.
- AI fallback feedback creates numeric scores without provider evaluation.
- AI evaluation lacks explicit rubric/evidence grounding and benchmark data.

## P2 Improvements

- Add structured profile fields already present in entity/schema: headline, experience level/years, LinkedIn, portfolio.
- Add category filter to candidate job search or remove category from contract.
- Improve duplicate-apply recovery and link to existing application.
- Add frontend validation parity for cover letter length, salary min/max, AI skill count, reset token presence.
- Add error/retry states for saved jobs, applications, notifications, and mark-read.
- Deep-link AI tab/mode/session state.
- Add auth endpoint rate limiting.
- Add browser/manual responsive and accessibility verification.

## P3 Polish

- Replace emoji icons in controls/navigation with consistent icon components.
- Normalize loading/placeholder typography.
- Add direct page jumps or load-more enhancements to public job pagination.
- Add mobile section navigation for long profile forms.

## Functional Problems

See `docs/audit/CANDIDATE_FUNCTIONAL_AUDIT.md`.

## Missing Candidate Features

- Languages/proficiency if required by product.
- Structured job preferences.
- Candidate-facing company detail/profile route.
- Profile headline/professional links/experience fields despite backend support.
- Persistent application AI-processing consent.

## Incorrect/Missing Fields

See `docs/audit/CANDIDATE_FIELD_AUDIT.md`. Highest-priority field problems: generic profile JSON, skills as comma text, missing profile fields, application consent mismatch, and one-of CV reference validation.

## UX Problems

See `docs/audit/CANDIDATE_UX_AUDIT.md`. Highest-priority UX problems: unbounded personal lists, generic profile section forms, AI transcript loss, fallback score clarity, and inaccessible modal behavior.

## Responsive Problems

See `docs/audit/CANDIDATE_RESPONSIVE_AUDIT.md`. Needs manual viewport testing, especially candidate nav, tables/cards, apply modal, notification popover, and AI interview room.

## Accessibility Problems

See `docs/audit/CANDIDATE_ACCESSIBILITY_AUDIT.md`. Highest-priority accessibility issue is modal focus management. Forms/status announcements/popovers/tables also need keyboard and screen reader testing.

## Performance Problems

See `docs/audit/CANDIDATE_PERFORMANCE_AUDIT.md`. Highest-priority issues: unpaged candidate lists and synchronous long AI/STT operations.

## Security Problems

See `docs/audit/CANDIDATE_SECURITY_AUDIT.md`. Highest-priority issues: token revocation, upload validation, uploaded CV privacy, secrets in config defaults, missing auth rate limiting, and AI data consent/retention.

## Virtual Interview Problems

See `docs/audit/AI_INTERVIEW_FUNCTIONAL_AUDIT.md`. Highest-priority issues: stale question/audio mapping, answer loss on refresh, fallback scoring, STT/code-switching, and long synchronous AI operations.

## AI Question Problems

See `docs/audit/AI_QUESTION_GENERATION_AUDIT.md`. Static prompt structure exists, but full job context and benchmark validation are insufficient.

## AI Evaluation Problems

See `docs/audit/AI_CANDIDATE_EVALUATION_AUDIT.md`. Evaluation lacks explicit rubric, evidence grounding, STT confidence handling, and human benchmark validation.

## Needs Manual Verification

- End-to-end candidate journeys in browser.
- Direct URL protected route access with stale/expired token.
- Mobile/tablet/desktop layouts.
- Keyboard-only and screen reader operation.
- CV upload/download privacy in deployed storage.
- Multi-tab AI interview concurrency.
- Slow network and provider timeout states.

## Needs AI Benchmark Testing

- Question relevance, clarity, duplicate rate, skill coverage, diversity, difficulty.
- Answer scoring MAE/agreement against human labels.
- Unsupported feedback/hallucination rate.
- Repeated-run and paraphrase robustness.
- Vietnamese, English, and mixed technical language robustness.
- STT transcript error impact on evaluation.
