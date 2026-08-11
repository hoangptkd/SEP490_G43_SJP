# CANDIDATE SYSTEM QUALITY STANDARD

Version: 1.0  
Scope: Candidate Role  
System Type: Recruitment Platform with AI Virtual Interview

---

# 1. PURPOSE

This document defines the quality standard for auditing the Candidate side of the recruitment platform.

The audit must evaluate:

1. Functional completeness
2. Business logic correctness
3. Candidate user experience
4. Forms and data completeness
5. Navigation and interaction patterns
6. Accessibility
7. Responsive behavior
8. Performance
9. Security and privacy
10. AI Virtual Interview quality
11. AI question generation quality
12. AI candidate evaluation accuracy

This document should be used as:

- Product Quality Standard
- Audit Checklist
- Definition of Done
- QA Reference
- AI Evaluation Standard

---

# 2. AUDIT RULES

Every criterion must receive one status:

PASS  
FAIL  
PARTIAL  
NOT APPLICABLE  
NEEDS MANUAL TEST  
NEEDS AI EVALUATION

Every FAIL or PARTIAL result must contain:

- Criterion ID
- Feature
- Current behavior
- Evidence
- User impact
- Expected behavior
- Recommended change
- Priority
- Acceptance criteria

Priority:

P0 = Critical / release blocking  
P1 = High  
P2 = Medium  
P3 = Low / polish

Codex must NOT mark a criterion as PASS unless there is evidence from the implementation.

---

# 3. CANDIDATE FUNCTIONAL QUALITY

## AUTH – Authentication

### AUTH-001 Registration
[ ] Candidate can register successfully.

[ ] Required information is clearly indicated.

[ ] Invalid information is rejected.

[ ] Duplicate accounts are handled correctly.

[ ] Password requirements are clear.

[ ] Backend validation matches frontend validation.

[ ] Registration failures provide understandable feedback.

### AUTH-002 Login

[ ] Correct credentials allow login.

[ ] Incorrect credentials produce appropriate feedback.

[ ] Authentication state survives refresh appropriately.

[ ] Expired sessions are handled correctly.

[ ] Protected candidate routes cannot be accessed without authentication.

### AUTH-003 Password

[ ] Forgot-password flow exists if required.

[ ] Reset tokens expire appropriately.

[ ] Password change invalidates sessions where appropriate.

---

# 4. CANDIDATE PROFILE

## PROFILE-001 Basic information

Audit all candidate profile fields.

Check whether the system appropriately supports:

[ ] Full name  
[ ] Avatar  
[ ] Email  
[ ] Phone  
[ ] Date of birth where justified  
[ ] Location  
[ ] Professional title  
[ ] Career objective / summary  
[ ] Skills  
[ ] Work experience  
[ ] Education  
[ ] Certifications  
[ ] Languages  
[ ] Portfolio / professional links where relevant

These are candidate fields to REVIEW, not mandatory requirements.

Codex must determine which fields are actually justified by the product.

## PROFILE-002 Data quality

[ ] Required fields are justified.

[ ] Optional fields are clearly distinguishable.

[ ] Appropriate field types are used.

[ ] Structured data is used where beneficial.

[ ] Date fields use appropriate date controls.

[ ] Skills are represented consistently.

[ ] Long text has reasonable limits.

[ ] URLs are validated.

[ ] Phone/email validation is correct.

## PROFILE-003 Profile completeness

Determine whether a profile completeness mechanism would meaningfully help candidates.

If present:

[ ] Completion percentage is accurate.

[ ] Missing information is clearly indicated.

[ ] Suggested next actions are useful.

---

# 5. CV / RESUME

## CV-001 Upload

[ ] Supported formats are clearly communicated.

[ ] File size limits exist.

[ ] Invalid files are rejected.

[ ] Upload progress is displayed where appropriate.

[ ] Upload errors are recoverable.

[ ] Duplicate uploads are handled.

## CV-002 Management

Where supported:

[ ] Candidate can view CV.

[ ] Candidate can download CV.

[ ] Candidate can replace/update CV.

[ ] Candidate can delete CV.

[ ] Candidate understands which CV is used for applications.

## CV-003 Security

[ ] Candidate CV cannot be accessed by unauthorized users.

[ ] File URLs do not unintentionally expose private documents.

[ ] File type validation exists server-side.

---

# 6. JOB DISCOVERY

## JOB-001 Job listing

[ ] Job cards expose sufficient decision-making information.

Review whether candidates can identify:

- job title
- company
- location
- salary
- employment type
- experience requirement
- posting date
- expiration date
- relevant skills

Only require information appropriate to the product.

## JOB-002 Search

[ ] Search produces relevant results.

[ ] Empty search is handled.

[ ] No-result state is useful.

[ ] Search input is debounced if API-backed.

[ ] Search term persists where appropriate.

## JOB-003 Filters

Review relevant filters:

[ ] Location  
[ ] Salary  
[ ] Experience  
[ ] Job type  
[ ] Skills/category  
[ ] Work arrangement  
[ ] Date posted

[ ] Multiple filters combine correctly.

[ ] Filters can be reset.

[ ] Active filters are visible.

[ ] Result count reflects filters.

## JOB-004 Pagination

[ ] Large datasets are paginated.

[ ] Changing page works correctly.

[ ] Filtering resets pagination where appropriate.

[ ] Page state persists appropriately.

[ ] Browser Back behaves correctly.

[ ] Empty last pages are avoided after data changes.

---

# 7. JOB DETAILS

## JOBDETAIL-001 Information completeness

Review:

[ ] Job description  
[ ] Responsibilities  
[ ] Requirements  
[ ] Skills  
[ ] Salary  
[ ] Benefits  
[ ] Location  
[ ] Employment type  
[ ] Experience  
[ ] Application deadline  
[ ] Company information

## JOBDETAIL-002 Candidate actions

Where applicable:

[ ] Apply  
[ ] Save  
[ ] Unsave  
[ ] View company  
[ ] View similar jobs

[ ] Already-applied state is visible.

[ ] Expired jobs cannot be incorrectly applied to.

---

# 8. JOB APPLICATION

## APPLY-001 Application flow

[ ] Candidate understands what will be submitted.

[ ] Correct CV is selected.

[ ] Required information is validated.

[ ] Duplicate applications are prevented where appropriate.

[ ] Submit button prevents accidental repeated submissions.

[ ] Loading state exists.

[ ] Success state exists.

[ ] Error recovery exists.

## APPLY-002 Application status

Candidate should understand relevant states such as:

Applied  
Under Review  
Interview  
Rejected  
Accepted

Actual status model must be derived from the system.

[ ] Status names are understandable.

[ ] Status history is accurate where available.

[ ] Candidate cannot manipulate protected status values.

---

# 9. SAVED JOBS

[ ] Save action is immediately understandable.

[ ] Saved state is visible.

[ ] Candidate can remove saved jobs.

[ ] Saved-job pagination works if needed.

[ ] Deleted/expired jobs are handled.

---

# 10. NAVIGATION & UX

Evaluate using Nielsen usability principles.

## UX-001 System status

[ ] Loading operations show feedback.

[ ] Successful operations show feedback.

[ ] Errors show feedback.

[ ] Long AI operations show progress/status.

## UX-002 User control

[ ] Candidate can cancel appropriate actions.

[ ] Destructive operations require confirmation.

[ ] Accidental actions can be recovered where appropriate.

## UX-003 Consistency

[ ] Same actions use consistent terminology.

[ ] Buttons follow consistent hierarchy.

[ ] Status colors/badges are consistent.

[ ] Forms behave consistently.

[ ] Tables behave consistently.

[ ] Modal behavior is consistent.

## UX-004 Navigation

[ ] Active navigation state is visible.

[ ] Browser Back works correctly.

[ ] Tabs behave predictably.

[ ] URL state is used when beneficial.

[ ] Refresh does not unnecessarily destroy important UI state.

---

# 11. FORMS

For EVERY Candidate form verify:

[ ] Label exists.

[ ] Required state is clear.

[ ] Appropriate input type is used.

[ ] Validation exists.

[ ] Validation messages explain the problem.

[ ] Backend validates the same constraint.

[ ] Input length is controlled.

[ ] Submit loading state exists.

[ ] Duplicate submission is prevented.

[ ] Server errors are displayed.

[ ] Successful completion is clear.

[ ] Unsaved-change protection exists where justified.

---

# 12. RESPONSIVE DESIGN

Test at minimum:

Mobile  
Tablet  
Laptop  
Desktop

Verify:

[ ] No unintended horizontal overflow.

[ ] Forms remain usable.

[ ] Navigation remains usable.

[ ] Tables have a deliberate mobile strategy.

[ ] Dialogs fit viewport.

[ ] Tabs remain usable.

[ ] Touch targets are sufficient.

[ ] Important actions remain discoverable.

[ ] Long text does not break layout.

---

# 13. ACCESSIBILITY

Target:

WCAG 2.2 AA where applicable.

Check:

[ ] Semantic HTML

[ ] Form labels

[ ] Keyboard navigation

[ ] Visible focus

[ ] Dialog focus management

[ ] Heading hierarchy

[ ] Image alternatives

[ ] Error identification

[ ] Color contrast

[ ] Status information not communicated by color alone

[ ] Accessible buttons

[ ] Accessible links

[ ] Accessible tables

---

# 14. PERFORMANCE

Evaluate:

[ ] Initial page loading

[ ] LCP

[ ] INP

[ ] CLS

[ ] Bundle size

[ ] Image optimization

[ ] Lazy loading

[ ] Duplicate API calls

[ ] Unnecessary rerenders

[ ] API waterfalls

[ ] Pagination

[ ] Caching

[ ] Large lists

[ ] AI API latency

Performance findings should include measurable evidence where possible.

---

# 15. SECURITY & PRIVACY

Audit:

[ ] Authentication

[ ] Authorization

[ ] IDOR risks

[ ] Candidate data access

[ ] CV access

[ ] Interview data access

[ ] AI evaluation access

[ ] Input validation

[ ] File upload validation

[ ] XSS risks

[ ] Injection risks

[ ] Sensitive information logging

[ ] Secrets exposure

[ ] Rate limiting where required

[ ] Personal data exposure

---

# 16. AI VIRTUAL INTERVIEW – FUNCTIONAL QUALITY

This section has HIGH PRIORITY.

## AI-FUNC-001 Interview creation

[ ] Interview is created for the correct candidate.

[ ] Correct job/job description is used.

[ ] Correct interview configuration is used.

[ ] Interview cannot start with invalid configuration.

## AI-FUNC-002 Interview session

[ ] Questions appear in correct order.

[ ] Candidate answers map to the correct question.

[ ] Answer submission is reliable.

[ ] Network failures do not silently lose answers.

[ ] Duplicate answers are prevented.

[ ] Interview progress is visible.

[ ] Interview completion is explicit.

## AI-FUNC-003 Speech-to-text

If speech input is used:

[ ] Recording state is clear.

[ ] Candidate can identify when recording starts/stops.

[ ] Transcription errors can be handled appropriately.

[ ] Technical terminology is preserved as accurately as possible.

[ ] Vietnamese-English code switching is considered.

[ ] Original answer/audio is distinguishable from transcript where applicable.

CRITICAL:

AI evaluation must not silently treat an unreliable transcript as unquestionably correct candidate input.

---

# 17. AI QUESTION QUALITY STANDARD

This is a CORE evaluation area.

Every generated interview question should be evaluated against the following dimensions.

## AI-Q-001 Job relevance

Question must relate to:

- job description
- required skills
- responsibilities
- seniority
- interview context

Metric:

Job Relevance Score: 1–5

Acceptance target:

Average >= 4.0

## AI-Q-002 Skill relevance

Questions should test skills actually relevant to the position.

Metric:

Skill Coverage Rate

Evaluate whether important skills from the job description receive appropriate coverage.

Target should be defined using the project's interview design.

## AI-Q-003 Difficulty appropriateness

Question difficulty must match candidate/job seniority.

Scale:

1 = Too easy  
2 = Slightly easy  
3 = Appropriate  
4 = Slightly difficult  
5 = Too difficult

Desired distribution should concentrate around 3.

## AI-Q-004 Clarity

Question must:

- be understandable
- avoid unnecessary ambiguity
- avoid contradictory requirements
- provide sufficient context

Clarity Score: 1–5

Target average >= 4.0

## AI-Q-005 Answerability

Question must be realistically answerable in an interview context.

[ ] Does not require unavailable information.

[ ] Does not require impossible precision.

[ ] Does not depend on hidden context.

## AI-Q-006 Non-duplication

Calculate:

Duplicate / Semantically Redundant Question Rate

Questions that test effectively the same thing without justification should be flagged.

## AI-Q-007 Question diversity

Evaluate coverage across relevant categories such as:

- technical knowledge
- practical experience
- problem solving
- behavioral
- situational
- communication

The correct distribution depends on job type.

Do NOT force every interview to contain every category.

---

# 18. AI CANDIDATE EVALUATION STANDARD

This is the MOST IMPORTANT AI quality area.

The AI must NOT be considered accurate simply because its feedback sounds reasonable.

Evaluation must be benchmarked against human-labeled reference data.

## AI-EVAL-001 Rubric

Every evaluated question should have explicit evaluation criteria.

Possible dimensions:

- correctness
- completeness
- relevance
- reasoning
- practical understanding
- communication

Only use dimensions relevant to the question.

## AI-EVAL-002 Evidence grounding

AI feedback must be grounded in what the candidate actually said.

For every major positive or negative judgment:

[ ] Evidence can be identified in the candidate answer.

The evaluator must not invent candidate knowledge.

Metric:

Unsupported Feedback Rate

Target:

As close to 0% as practically achievable.

Critical unsupported claims must be 0%.

## AI-EVAL-003 Score consistency

Equivalent answers should receive similar scores.

Test using repeated evaluation.

Metric:

Score variance / score deviation across repeated runs.

Define an acceptable threshold empirically.

## AI-EVAL-004 Human agreement

Create a benchmark dataset.

Recommended structure:

Question  
Candidate Answer  
Human Score  
Human Feedback  
AI Score  
AI Feedback

Use multiple human reviewers where practical.

Measure agreement between AI scores and human reference scores.

Possible metrics:

MAE – Mean Absolute Error

Correlation

Weighted Cohen's Kappa where appropriate

Exact / tolerance-based agreement

Do not rely on only one metric.

## AI-EVAL-005 Score MAE

For numeric scoring:

MAE = average absolute difference between AI score and human reference score.

Acceptance threshold must be determined according to the scoring scale.

Example:

For a 1–5 scoring system, an initial project target might be:

MAE <= 0.5

This is an example target and must be validated experimentally.

## AI-EVAL-006 Ranking accuracy

If AI evaluation is used to compare candidate performance:

Test whether AI preserves human ranking.

Possible metric:

Spearman Rank Correlation.

## AI-EVAL-007 Feedback correctness

Human evaluators should label AI feedback:

Correct  
Partially Correct  
Incorrect

Metric:

Feedback Correctness Rate

## AI-EVAL-008 Feedback usefulness

Human evaluators should determine whether feedback helps the candidate improve.

Scale:

1–5

Evaluate:

- specificity
- actionability
- correctness
- clarity

## AI-EVAL-009 Hallucination

Check whether AI:

- claims candidate mentioned something they did not
- invents technical mistakes
- invents strengths
- invents missing requirements
- evaluates unrelated information

Metric:

Hallucination / Unsupported Claim Rate

Critical hallucination target:

0%.

## AI-EVAL-010 Robustness

Create semantically equivalent answers with:

- paraphrasing
- different sentence order
- concise wording
- verbose wording

Scores should remain reasonably stable.

## AI-EVAL-011 Language robustness

Test:

Vietnamese  
English  
Vietnamese-English mixed answers

Particularly test technical terminology.

Examples:

Spring Boot  
REST API  
Docker  
React  
JWT  
microservices

Evaluation quality should not significantly degrade simply because technical terms are English inside Vietnamese speech.

---

# 19. AI QUESTION BENCHMARK DATASET

Create an evaluation dataset.

Recommended minimum initial benchmark:

10 different job descriptions

Multiple job categories where supported.

For each job:

Generate multiple interview sessions.

Human reviewers evaluate every generated question.

Store:

job_id  
job_description  
question  
question_type  
target_skill  
difficulty  
relevance_score  
clarity_score  
duplicate_flag  
human_notes

Calculate:

Average Job Relevance

Average Clarity

Skill Coverage

Duplicate Rate

Invalid Question Rate

Difficulty Appropriateness

---

# 20. AI ANSWER EVALUATION DATASET

Create candidate answer samples at different quality levels.

For each question create examples such as:

Poor answer  
Weak answer  
Average answer  
Good answer  
Excellent answer

Also include:

Partially correct answer  
Technically correct but vague answer  
Confident but incorrect answer  
Verbose but low-information answer  
Short but correct answer  
Off-topic answer

Have human reviewers establish reference scores.

Then run AI evaluation against the same dataset.

---

# 21. AI ACCEPTANCE GATE

AI Interview must NOT be considered validated based only on manual observation.

Before acceptance, produce measurable results for:

QUESTION GENERATION

[ ] Job Relevance

[ ] Clarity

[ ] Skill Coverage

[ ] Duplicate Rate

[ ] Difficulty Appropriateness

ANSWER EVALUATION

[ ] Human-AI score agreement

[ ] MAE

[ ] Feedback Correctness

[ ] Unsupported Feedback Rate

[ ] Hallucination Rate

[ ] Robustness

[ ] Language robustness

All thresholds must be documented.

Thresholds must not be invented after observing results merely to make the system pass.

---

# 22. AI FAILURE ANALYSIS

Every failed AI evaluation case should be classified.

Suggested categories:

QUESTION_IRRELEVANT

QUESTION_AMBIGUOUS

QUESTION_TOO_EASY

QUESTION_TOO_HARD

QUESTION_DUPLICATED

SKILL_NOT_COVERED

SCORE_TOO_HIGH

SCORE_TOO_LOW

FEEDBACK_INCORRECT

FEEDBACK_UNSUPPORTED

HALLUCINATION

TRANSCRIPTION_ERROR

LANGUAGE_ERROR

TECHNICAL_TERM_ERROR

RUBRIC_ERROR

PROMPT_ERROR

MODEL_VARIANCE

This classification should be used to identify systematic failure patterns.

---

# 23. ACCEPTANCE CRITERIA

Every Candidate feature must have explicit acceptance criteria.

Example:

JOB SEARCH

Given the candidate enters a valid keyword  
When search is executed  
Then matching jobs are displayed  
And result count is correct  
And pagination reflects filtered results  
And empty state is displayed when no results exist.

AI EVALUATION

Given a benchmark interview question  
And a human-labeled candidate answer  
When the AI evaluates the answer  
Then the score must remain within the project's accepted error tolerance  
And feedback must be grounded in the candidate answer  
And no critical unsupported claim may be generated.

Codex must generate missing acceptance criteria for existing functionality.

---

# 24. FINAL AUDIT OUTPUT

Create:

`CANDIDATE_QUALITY_AUDIT.md`

Summary:

Total criteria checked  
PASS  
FAIL  
PARTIAL  
NEEDS MANUAL TEST  
NEEDS AI EVALUATION

Then:

## P0 Critical Issues

## P1 High Priority

## P2 Improvements

## P3 Polish

## Missing Candidate Functionality

## Incorrect / Missing Fields

## UX Problems

## Security Problems

## Performance Problems

## AI Interview Problems

## AI Question Generation Problems

## AI Evaluation Problems

## Manual Tests Required

## AI Benchmark Tests Required

Finally create:

`CANDIDATE_IMPROVEMENT_BACKLOG.md`

Each backlog item must contain:

ID  
Feature  
Problem  
Evidence  
Standard violated  
Expected behavior  
Acceptance criteria  
Priority  
Effort  
Affected files  
Dependencies

Do NOT modify implementation during the audit.