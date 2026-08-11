# AI Question Generation Audit

Do not treat code review as evidence that generated questions are accurate. This audit separates static findings from empirical testing requirements.

## Static Findings

| ID | Finding | Evidence | Impact | Priority | Recommended fix / acceptance criteria |
|---|---|---|---|---|---|
| AI-Q-001 | Prompt requests one Vietnamese question, JSON only, max length, type/difficulty/skill/time schema. | `backend/src/main/java/com/sjp/recruitment/service/ai/ShopAiKeyClient.java:31-62`. | Positive structure for parseability. | P2 | Unit tests cover valid/invalid provider JSON and schema coercion. |
| AI-Q-002 | Context includes session type/title, job title, job requirements text, practice context, candidate skills/bio/headline. | `ShopAiKeyClient.java:220-231`. | Context is useful but incomplete for job relevance. | P1 | Include full job description, responsibilities, seniority, skills, work mode, and application job snapshot where applicable. |
| AI-Q-003 | Question generation can use admin custom system prompt. | `ShopAiKeyClient.java:243-250`. | Admin prompt can improve or degrade quality; no validation/safety guard found. | P2 | Admin prompts are versioned, validated, and included in AI benchmark metadata. |
| AI-Q-004 | Previous questions and answers are passed to generation. | `ShopAiKeyClient.java:232-241`. | Helps avoid duplicates, but duplicate prevention is not deterministic. | P2 | Add semantic duplicate check or post-generation validation. |
| AI-Q-005 | JSON parsing extracts substring between first `{` and last `}`. | `ShopAiKeyClient.java:259-266`. | Fragile if model returns extra braces in strings or malformed JSON. | P2 | Use strict response format if provider supports it; add robust parse tests. |
| AI-Q-006 | `questionType` and `difficulty` are constrained with fallback; question length is checked. | `ShopAiKeyClient.java:268-297`. | Some output validation exists. | P2 | Add validation for duplicate, answerability, relevance, and skillTag. |
| AI-Q-007 | On provider failure, fallback questions are generic behavioral questions. | `AiInterviewService.java:459-482`, `544-562`. | Fallback may not match job/skills well, especially later questions. | P1 | Label fallback questions or defer session until provider recovers. |
| AI-Q-008 | Fixed question mode copies approved active bank questions. | `AiInterviewService.java:170-201`, `484-502`. | Good alternative for testability. | P2 | Show candidate when fixed bank vs generated mode is used and benchmark banks separately. |
| AI-Q-009 | Model and temperature are configured in code/properties; generation uses temperature 0.3. | `ShopAiKeyClient.java:182-188`, `AiInterviewProperties.java:14-16`. | Some variance remains; deterministic tests require seeded/low-temp or fixed bank. | P2 | Record model/temperature/prompt version per question. |
| AI-Q-010 | No empirical benchmark dataset or metrics implementation was found. | No files matching benchmark metric storage in reviewed AI service/model files; quality standard requires metrics. | Cannot claim question quality. | P1 | Create benchmark dataset and metric reporting before accepting AI quality. |

## Metrics To Define And Measure

- Job Relevance: human 1-5 score comparing question to full job snapshot.
- Skill Relevance: whether question tests a job/profile-relevant skill.
- Skill Coverage: percentage of critical job skills covered across a session.
- Clarity: human 1-5 score for understandable, unambiguous wording.
- Difficulty Appropriateness: 1 too easy to 5 too hard, target centered on role seniority.
- Answerability: binary/graded; answerable in 2-3 minutes without hidden context.
- Duplicate Rate: semantically redundant questions per session.
- Question Diversity: distribution across technical, behavioral, situational, general when appropriate.
- Invalid Question Rate: malformed, too long, multiple-part, off-topic, unanswerable, or policy-violating questions.

## Findings Requiring Empirical AI Testing

| ID | Test Needed | Dataset | Metric | Acceptance gate |
|---|---|---|---|---|
| AI-Q-T001 | Does generated content use the correct job description and seniority? | At least 10 job descriptions from supported categories. | Average Job Relevance >= project threshold, e.g. >= 4.0/5 only if accepted by team before testing. | Must pass before production claim. |
| AI-Q-T002 | Are critical skills covered without duplicates? | Job skills annotated by human reviewers. | Skill Coverage and Duplicate Rate. | Threshold documented before test run. |
| AI-Q-T003 | Are Vietnamese and mixed technical terms clear? | Vietnamese and Vietnamese-English job descriptions/skills. | Clarity, Invalid Question Rate, language error rate. | Must meet language robustness target. |
| AI-Q-T004 | Is difficulty appropriate? | Jobs by intern/fresher/junior/middle/senior. | Difficulty appropriateness distribution. | Outliers reviewed and prompt adjusted. |
| AI-Q-T005 | How often does fallback occur? | Provider-failure and normal-operation logs. | Fallback Question Rate. | Fallback rate and UX policy documented. |
