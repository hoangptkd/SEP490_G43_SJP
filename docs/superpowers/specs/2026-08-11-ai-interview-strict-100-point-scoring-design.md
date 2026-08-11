# AI Interview Strict 100-Point Scoring Design

## Context

The AI interview prompt already asks the provider to return a score on a 0–100 scale. The response parser nevertheless contains compatibility logic that multiplies any positive score up to 10 by 10. This produces inconsistent results: an AI score of 9 becomes 90, while 11 remains 11.

The current backend also clamps scores into the 0–100 interval, treats a missing score as zero, and generates a fallback score between 35 and 75 from transcript word count when provider evaluation fails. Those values are not the score returned by the AI provider.

## Decision

Use the AI provider's score as the single source of truth for answer evaluation.

- The AI must return a numeric score directly on the 0–100 scale.
- The backend must not multiply, rescale, clamp, round to an integer, or fabricate the score.
- Valid decimal values are persisted with the database's existing two-decimal precision.
- Missing, non-numeric, negative, or above-100 values are invalid provider output, not values for the backend to repair.
- Provider or validation failures must be returned to the frontend as explicit, safe API errors.

The session `overallScore` remains the arithmetic average of valid per-answer AI scores. It does not include VAD, fluency, transcript confidence, or any other auxiliary metric.

## 100-Point AI Rubric

The answer-evaluation prompt will tell the AI to judge the answer directly using this 100-point rubric:

- Relevance and correctness: 40 points.
- Depth and completeness: 30 points.
- Structure and clarity: 20 points.
- Concrete examples or evidence: 10 points.

The AI returns the final total in the existing `score` field. The rubric guides the model; the backend does not recompute or override the total.

## Validation and Error Contract

The parser must require the `score` field to be a JSON number in the inclusive range 0–100. It must preserve the numeric value returned by the AI.

Invalid model output returns a controlled `502 Bad Gateway` API error with code `AI_INVALID_SCORE` and a Vietnamese message explaining that the AI did not return a valid 0–100 score. Provider transport, timeout, JSON, or required-field failures retain their provider-specific safe error codes and are also exposed through the normal API error response.

Raw provider responses, credentials, request headers, and other sensitive diagnostics must not be returned to the browser.

## Evaluation and Persistence Flow

Confirmed transcript data remains independent from evaluation. If answer evaluation fails:

- the confirmed answer and original transcript evidence remain saved;
- no `AiAnswerFeedback` row containing a fabricated score is created;
- no heuristic score based on word count is created;
- the interview is not marked completed with an artificial result;
- the API returns the evaluation error, and the frontend displays it in the existing error panel;
- the user can retry the finish/evaluation action.

If session summary generation fails, the backend must not create a fallback summary that hides the provider failure. It returns the error and leaves the session available for retry. Successfully persisted AI scores remain the only inputs to a later overall-score calculation.

Implementation must avoid holding a database transaction open across provider network calls and must use short persistence transactions so an evaluation failure does not erase confirmed transcripts.

## Frontend Behavior

The frontend already extracts the backend error message and displays it in the AI interview error panel. No score formatting or rescaling is added on the frontend. A valid score continues to be displayed as a percentage using the current presentation rounding only; the stored value remains unchanged.

No fallback badge or fallback feedback should be created for new evaluation failures because no fallback evaluation is produced.

## Scope

This change affects AI answer-score prompting, score validation, evaluation error propagation, and summary error propagation. It does not change:

- Gladia transcription or browser-transcript fallback;
- Silero VAD metrics;
- fluency, confidence, emotion, or personality analysis;
- answer confirmation semantics;
- the arithmetic definition of `overallScore`;
- existing database score columns or constraints.

## Validation

Per user instruction, no new score-boundary or provider-failure test cases will be added for this change. Validation consists of running the existing relevant regression tests, the full backend test suite, the backend package build, and UTF-8 encoding checks for all modified localized files.

## Expected Result

An AI score of 9 is stored as 9, 11 is stored as 11, and any other valid 0–100 score is stored without backend rescaling. Invalid or unavailable AI evaluation produces a visible API error instead of a repaired or fallback score.
