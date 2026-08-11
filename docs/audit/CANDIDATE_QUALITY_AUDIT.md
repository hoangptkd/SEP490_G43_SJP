# Candidate Quality Audit

This is the quality-standard rollup requested by `docs/quality/CANDIDATE_SYSTEM_QUALITY_STANDARD.md`.

## Summary Counts

Static criteria checked in the phase reports: 101

- PASS / positive evidence: 13
- FAIL: 0 P0-proven from static review
- PARTIAL: 58
- NEEDS MANUAL TEST: 17
- NEEDS AI EVALUATION: 13

Counts are approximate because several quality criteria span multiple screens and require runtime/benchmark verification.

## P0 Critical Issues

No P0 was proven by static review alone.

## P1 High Priority

- Unstructured/unvalidated candidate profile sections.
- Unpaged candidate personal lists.
- JWT revocation gap.
- Weak CV upload validation and repo-local uploaded CV privacy risk.
- AI/application consent not persisted.
- AI stale audio/question mapping risk.
- AI transcript loss on refresh/back.
- AI fallback scoring and lack of explicit rubric/evidence grounding.
- STT/code-switching benchmark gap.

## P2 Improvements

- Add missing profile fields already supported by entity/schema.
- Add category filter or remove from contract.
- Add frontend/backend validation parity.
- Improve duplicate application recovery.
- Improve error/retry states.
- Deep-link AI tab/mode/session state.
- Add auth rate limits.

## P3 Polish

- Replace emoji icons.
- Normalize placeholder/loading ellipsis.
- Improve mobile touch target polish.

## Missing Candidate Functionality

Languages, structured job preferences, candidate company detail route, profile professional links/headline/experience fields, persistent consent metadata.

## Incorrect / Missing Fields

See `CANDIDATE_FIELD_AUDIT.md`.

## UX Problems

See `CANDIDATE_UX_AUDIT.md`.

## Security Problems

See `CANDIDATE_SECURITY_AUDIT.md`.

## Performance Problems

See `CANDIDATE_PERFORMANCE_AUDIT.md`.

## AI Interview Problems

See `AI_INTERVIEW_FUNCTIONAL_AUDIT.md`.

## AI Question Generation Problems

See `AI_QUESTION_GENERATION_AUDIT.md`.

## AI Evaluation Problems

See `AI_CANDIDATE_EVALUATION_AUDIT.md`.

## Manual Tests Required

Responsive, accessibility, stale token/direct URL, large lists, file upload adversarial cases, AI multi-tab/refresh/timeout behavior.

## AI Benchmark Tests Required

Question generation relevance/coverage/diversity/clarity/difficulty and answer evaluation MAE/agreement/grounding/hallucination/robustness/language tests.
