# AI Candidate Evaluation Audit

Do not claim AI evaluation accuracy from code review. The implementation can be assessed for what it evaluates, what it stores, and what must be benchmarked.

## What The AI Evaluates

The provider prompt evaluates:
- One question content.
- One transcript string.
- Session context containing session type/title, job title + requirements text, and practice context.

Evidence: `backend/src/main/java/com/sjp/recruitment/service/ai/ShopAiKeyClient.java:65-93`, `220-231`.

It does not explicitly receive:
- A structured rubric per question.
- Human reference answer.
- STT confidence.
- Full job description/benefits/work mode/seniority.
- Original audio.
- Evidence extraction requirement.

## Static Findings

| ID | Finding | Evidence | Impact | Priority | Acceptance criteria |
|---|---|---|---|---|---|
| AI-EVAL-001 | Evaluation prompt requests score 0-100 and feedback/strengths/weaknesses/suggestions JSON. | `ShopAiKeyClient.java:65-93`. | Basic output structure exists. | P2 | Invalid/missing fields are tested and handled. |
| AI-EVAL-002 | There is no explicit rubric for correctness/completeness/relevance/reasoning/communication. | Prompt at `ShopAiKeyClient.java:65-93` contains no rubric dimensions. | Scores may be inconsistent and hard to audit. | P1 | Each question carries rubric criteria or evaluator prompt includes measurable dimensions. |
| AI-EVAL-003 | Feedback is not required to cite evidence from transcript. | Prompt lacks quote/evidence requirement at `ShopAiKeyClient.java:65-93`. | AI may produce unsupported feedback. | P1 | Feedback includes evidence spans or structured grounding references. |
| AI-EVAL-004 | Score normalization converts 0-10 to 0-100 and clamps. | `ShopAiKeyClient.java:288-296`. | Helps scale consistency but can hide provider format drift. | P2 | Log/flag when provider returns unexpected scale. |
| AI-EVAL-005 | Provider failure creates fallback score based on transcript word count. | `AiInterviewService.java:564-578`. | Numeric score is not rubric- or answer-quality-based. | P1 | Fallback feedback is non-scored or clearly provisional. |
| AI-EVAL-006 | Session summary uses average of answer feedback scores and provider text. | `AiInterviewService.java:505-542`, `637-649`. | Summary inherits answer scoring weaknesses. | P1 | Summary reports source/fallback and benchmarked confidence. |
| AI-EVAL-007 | STT transcript is treated as answer input after transcription; no confidence source is passed to evaluator. | `AiInterviewService.java:250-257`, `ShopAiKeyClient.java:65-93`. | STT errors can directly alter score/feedback. | P1 | Store transcript source/confidence and show candidate verification state before evaluation. |
| AI-EVAL-008 | Manual transcript editing is allowed in manual mode. | `frontend/src/App.tsx:5067-5073`. | Evaluation may score edited text, not necessarily spoken answer. | P2 | Store source/edit history or clearly define transcript as candidate-confirmed answer. |
| AI-EVAL-009 | Evaluation source/fallback is stored and returned. | `AiInterviewService.java:352-362`, `AiInterviewResponseAssembler.java:114-130`. | Positive transparency hook. | P2 | UI makes source/fallback clear enough not to mislead. |
| AI-EVAL-010 | No benchmark/human-labeled evaluation dataset found. | Reviewed AI service/tests include validation/rate limiter/deferred tests, but no human label benchmark files in AI paths. | Accuracy cannot be accepted. | P1 | Create benchmark and report MAE, agreement, unsupported feedback, hallucination. |

## Questions Answered By Static Review

- Is there an explicit rubric? No, not in the prompt reviewed.
- Is scoring deterministic enough? Not proven; temperature 0.2 and provider variance remain.
- Is feedback grounded in candidate answers? Not guaranteed; no evidence citation requirement.
- Can AI invent information? Possible; prompt does not prohibit unsupported claims strongly enough or require evidence mapping.
- Can STT errors alter evaluation? Yes, transcript string is evaluator input.
- Are irrelevant factors used? Prompt includes session context/job requirements/practice context; empirical testing needed.
- Can equivalent answers receive different scores? Must be measured with repeated runs and paraphrase tests.
- Does feedback explain why score was assigned? Prompt asks feedback but not score rationale tied to rubric.

## Empirical Tests Required

| ID | Test | Metric | Acceptance gate |
|---|---|---|---|
| AI-EVAL-T001 | Human-labeled answer scoring benchmark | MAE, correlation, tolerance agreement | Threshold documented before running, e.g. MAE target on 0-100 scale. |
| AI-EVAL-T002 | Feedback grounding | Unsupported Feedback Rate, Hallucination Rate | Critical unsupported claims target 0%. |
| AI-EVAL-T003 | Repeated-run consistency | Score variance/deviation over N reruns | Threshold documented by scale. |
| AI-EVAL-T004 | Paraphrase robustness | Score delta for equivalent answers | Equivalent answers remain within accepted tolerance. |
| AI-EVAL-T005 | STT robustness | Score delta original text vs STT transcript with errors | Evaluation warns/degrades safely when STT quality is low. |
| AI-EVAL-T006 | Language robustness | Vietnamese, English, mixed technical terms | No material degradation for expected candidate language. |
| AI-EVAL-T007 | Fallback safety | Candidate comprehension of fallback vs provider evaluation | Users do not interpret fallback as final AI score. |
