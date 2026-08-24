package com.sjp.recruitment.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.entity.Job;
import com.sjp.recruitment.service.SystemSettingsService;
import com.sjp.recruitment.service.AiInterviewTelemetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopAiKeyClient {

    static final int INITIAL_QUESTION_MAX_TOKENS = 8_000;
    static final int CV_PROFILE_MAX_TOKENS = 8_000;
    static final int CV_PROFILE_RETRY_MAX_TOKENS = 8_000;
    static final int TURN_DECISION_MAX_TOKENS = 256;
    static final int TURN_EVIDENCE_MAX_TOKENS = 4_000;
    static final int ADAPTIVE_QUESTION_MAX_TOKENS = 8_000;
    static final int TRANSCRIPT_CORRECTION_MAX_TOKENS = 8_000;
    static final int FINAL_EVALUATION_MAX_TOKENS = 8_000;
    static final int PROVIDER_MAX_ATTEMPTS = 3;
    static final int QUESTION_TARGET_MAX_LENGTH = 240;
    static final int QUESTION_HARD_MAX_LENGTH = 500;
    static final String TURN_DECISION_STAGE = "assessment_turn_decision";
    static final String TURN_DECISION_PROMPT_VERSION = "assessment-turn-decision-v2";
    static final String TURN_EVIDENCE_STAGE = "assessment_turn_evidence";
    static final String TURN_EVIDENCE_PROMPT_VERSION = "assessment-turn-evidence-v1";
    static final String TRANSCRIPT_CORRECTION_STAGE = "transcript_correction";
    static final int UPDATED_ITEM_SUMMARY_MAX_LENGTH = 1_200;
    static final int FOLLOW_UP_MAX_LENGTH = 600;
    static final String EMPTY_ITEM_SUMMARY =
            "Chưa ghi nhận bằng chứng cụ thể từ câu trả lời này.";
    static final String ADAPTIVE_PROMPT_VERSION = "ai-question-adaptive-v4";
    static final String GROUPED_EVALUATION_PROMPT_VERSION = "bars-evaluation-grouped-v2";
    private final AiInterviewProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final SystemSettingsService systemSettingsService;
    private final AiInterviewTelemetryService telemetryService;

    public TranscriptCorrectionDraft correctBrowserTranscript(
            UUID sessionId,
            TranscriptCorrectionContext context
    ) {
        if (context == null || !StringUtils.hasText(context.currentQuestion())
                || !StringUtils.hasText(context.rawTranscript())) {
            throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_CONTEXT",
                    "Thiếu câu hỏi hoặc raw transcript để sửa lỗi nhận dạng");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("currentQuestion", context.currentQuestion());
        input.put("rawTranscript", context.rawTranscript());
        input.put("cvTechnicalTerms", context.cvTechnicalTerms());
        input.put("jobTechnicalTerms", context.jobTechnicalTerms());
        input.put("relevantTechnicalVocabulary", context.relevantTechnicalVocabulary());
        input.put("previousContext", context.previousContext());

        String prompt = withSystemPrompt("""
                Bạn là AI xử lý câu trả lời trong hệ thống luyện phỏng vấn.
                Chỉ trả đúng một JSON object hợp lệ, không markdown và không thêm field ngoài schema.
                Nhiệm vụ:
                1. Chỉ sửa lỗi nhận dạng giọng nói có xác suất cao trong rawTranscript, đặc biệt là thuật ngữ kỹ thuật.
                2. Không paraphrase, không sửa văn phong, không thêm ý, không bỏ từ hoặc filler như “ờ”, “ừm”.
                3. Không thay đổi nghĩa. Thuật ngữ đã đúng như Postman, Docker, Spring Boot, JWT, REST API phải giữ nguyên.
                4. Ưu tiên đối chiếu theo thứ tự: currentQuestion, cvTechnicalTerms, jobTechnicalTerms,
                   relevantTechnicalVocabulary, rồi ngữ cảnh.
                5. Nếu không đủ chắc chắn thì giữ nguyên text. False correction nguy hiểm hơn missed correction.
                6. Chỉ khai báo correction có confidence >= 0.90. Mọi thay đổi trong correctedTranscript phải xuất hiện
                   đúng trong corrections; không được có thay đổi ngầm.
                7. Tạo correctedTranscript bằng cách thay tuần tự đúng từng original bằng replacement trên rawTranscript.
                   Kể cả chỉ đổi chữ hoa/chữ thường hoặc dấu câu cũng phải khai báo thành correction; nếu không khai báo
                   thì phải giữ nguyên chính tả, cách viết hoa và dấu câu của rawTranscript.
                8. Trích evidence quan sát được làm draft cho adaptive analysis; không tạo điểm số.
                9. Nếu original xuất hiện nhiều lần trong rawTranscript, phải lấy thêm từ xung quanh để original
                   chỉ xuất hiện đúng một lần; replacement phải thay toàn bộ cùng đoạn đó. Không khai báo một
                   original mơ hồ có nhiều vị trí khớp.
                JSON schema chính xác:
                {
                  "correctedTranscript": "string",
                  "corrections": [{
                    "original": "string",
                    "replacement": "string",
                    "confidence": 0.0,
                    "reason": "current_question|cv_term|job_term|technical_vocabulary|context"
                  }],
                  "evidence": [{
                    "type": "situation|task|action|result|technical_knowledge|other",
                    "text": "string"
                  }],
                  "answerSummary": "string",
                  "followUpNeeded": false,
                  "followUpReason": "string or null"
                }
                Input:
                %s
                """.formatted(jsonString(input)));

        JsonNode json = callJson(
                prompt,
                TRANSCRIPT_CORRECTION_MAX_TOKENS,
                0.0,
                sessionId,
                TRANSCRIPT_CORRECTION_STAGE,
                properties.getTranscriptCorrectionPromptVersion(),
                properties.getTranscriptCorrectionTimeoutMs(),
                false
        );
        return parseProviderResponse(
                sessionId,
                TRANSCRIPT_CORRECTION_STAGE,
                properties.getTranscriptCorrectionPromptVersion(),
                json,
                () -> transcriptCorrectionDraft(json)
        );
    }

    public InterviewPackageDraft generateInitialInterviewPackage(
            InterviewSession session,
            CandidateProfile candidate
    ) {
        String prompt = withSystemPrompt("""
                Tạo Evaluation Profile và đúng 3 câu đầu cho buổi phỏng vấn luyện tập bằng tiếng Việt.
                Chỉ trả JSON hợp lệ, không markdown.
                Bắt đầu response ngay bằng ký tự { và kết thúc bằng ký tự }. Không giải thích schema,
                không nhắc lại yêu cầu và không viết nội dung nào ngoài JSON.
                Evaluation Profile có 3-6 năng lực tổng, nhưng scoredCompetencyIds phải chọn đúng 3-4 năng lực
                thực sự được chấm trong phiên 5 câu.
                Không dùng bằng cấp, trường học, tuổi, giới tính hoặc ngoại hình làm tiêu chí.
                Mỗi rating importance, entryNeedScore, distinguishingValueScore là số nguyên 1-5 và đều cùng chiều:
                - importance: 5 = cực kỳ quan trọng.
                - entryNeedScore: 5 = cần có ngay khi bắt đầu; 4 = cần rất sớm; 3 = cần trong thời gian đầu;
                  2 = có thể phát triển sau khi vào việc; 1 = chủ yếu có thể đào tạo sau khi vào việc.
                - distinguishingValueScore: 5 = phân biệt rất mạnh người thể hiện tốt và yếu.
                Không dùng trực tiếp encoding Need At Entry của OPM vì thang OPM có chiều ngược với entryNeedScore.
                Backend tính priority = 0.50*importance + 0.20*entryNeedScore + 0.30*distinguishingValueScore.
                Chỉ chọn vào scoredCompetencyIds các năng lực có thể quan sát bằng 5 câu phỏng vấn.
                communicationDemand là số nguyên 1-5 dựa trên mức giao tiếp cần thiết của vai trò.
                Ba câu đầu phải gồm: 1 câu xác minh kinh nghiệm/dự án, 1 câu năng lực cốt lõi,
                và 1 câu hành vi hoặc tình huống. Mỗi question chỉ hỏi một ý chính, tối đa %d ký tự,
                chỉ có một dấu hỏi (?) và có thể trả lời trong 2-3 phút.
                Ba câu đầu phải dùng 3 competencyId khác nhau thuộc scoredCompetencyIds để ưu tiên coverage.
                BARS là rubric ẩn; level1, level3 và level5 phải mô tả hành vi/bằng chứng quan sát được.
                JSON schema:
                {
                  "evaluationProfile": {
                    "targetRole": "string",
                    "seniority": "intern|fresher|junior|middle|senior",
                    "communicationDemand": 1,
                    "scoredCompetencyIds": ["competency-id"],
                    "competencies": [{
                      "id": "string",
                      "name": "string",
                      "definition": "string",
                      "importance": 1,
                      "entryNeedScore": 1,
                      "distinguishingValueScore": 1,
                      "measurementMode": "content|voice|both",
                      "rationale": "string"
                    }]
                  },
                  "questions": [{
                    "questionType": "behavioral|technical|situational|cv_experience|general",
                    "difficulty": "easy|medium|hard",
                    "competencyId": "string",
                    "skillTag": "string or null",
                    "question": "string",
                    "timeLimitSeconds": 180,
                    "expectedEvidence": ["string"],
                    "bars": {"level1": "string", "level3": "string", "level5": "string"}
                  }]
                }
                Context:
                %s
                """.formatted(QUESTION_TARGET_MAX_LENGTH, buildContext(session, candidate)));
        return callJsonValidated(
                prompt,
                INITIAL_QUESTION_MAX_TOKENS,
                0.2,
                session.getId(),
                "initial_questions",
                "ai-question-initial-v2",
                json -> new InterviewPackageDraft(
                        evaluationProfile(json.path("evaluationProfile")),
                        rubricQuestions(json.path("questions"), 3)
                )
        );
    }

    public AnswerDecisionDraft analyzeAssessmentTurnDecision(
            InterviewSession session,
            InterviewQuestion coreQuestion,
            String currentAnswer,
            AssessmentTurnCounters counters
    ) {
        validateAssessmentTurnInput(session, coreQuestion, currentAnswer, counters);
        String questionType = normalizedQuestionType(coreQuestion.getQuestionType());
        String prompt = withSystemPrompt("""
                Chọn bước tiếp theo cho một lượt phỏng vấn tiếng Việt. Chỉ trả JSON, không markdown:
                {"action":"NEXT|PROBE|CLARIFY","followUp":"string|null"}
                NEXT khi câu trả lời đã hữu ích, chỉ còn thiếu chi tiết phụ/lặp ý, hoặc đã chạm giới hạn.
                PROBE khi câu trả lời liên quan nhưng thiếu đúng một evidence quan trọng có thể đổi đánh giá.
                CLARIFY khi câu trả lời mơ hồ, quá ngắn hoặc lệch trọng tâm nhưng ứng viên có thể làm rõ.
                PROBE/CLARIFY phải có đúng một câu hỏi followUp ngắn; NEXT phải có followUp=null.
                Tổng PROBE+CLARIFY tối đa %d; probeCount >= %d thì cấm PROBE; clarifyCount >= %d thì cấm CLARIFY.
                Nếu totalAssessmentTurns + remainingCoreQuestions >= %d thì bắt buộc NEXT.
                Context: %s
                """.formatted(
                properties.getMaxFollowUpsPerCore(),
                properties.getMaxProbesPerCore(),
                properties.getMaxClarifiesPerCore(),
                properties.getMaxTotalAssessmentTurns(),
                jsonString(assessmentDecisionContext(
                        session, coreQuestion, currentAnswer, counters, questionType))
        ));

        JsonNode json = callJson(
                prompt,
                TURN_DECISION_MAX_TOKENS,
                0.0,
                session.getId(),
                TURN_DECISION_STAGE,
                TURN_DECISION_PROMPT_VERSION
        );
        return parseProviderResponse(
                session.getId(),
                TURN_DECISION_STAGE,
                TURN_DECISION_PROMPT_VERSION,
                json,
                () -> answerDecisionDraft(json, session.getId())
        );
    }

    public AnswerEvidenceDraft analyzeAssessmentTurnEvidence(
            InterviewSession session,
            InterviewQuestion coreQuestion,
            String currentAnswer,
            Map<String, Object> itemEvidenceSummary
    ) {
        validateAssessmentEvidenceInput(session, coreQuestion, currentAnswer);
        String questionType = normalizedQuestionType(coreQuestion.getQuestionType());
        List<String> dimensions = evidenceDimensions(questionType);
        Map<String, Boolean> coverageSchema = new LinkedHashMap<>();
        dimensions.forEach(dimension -> coverageSchema.put(dimension, false));
        Map<String, Object> currentSummary = itemEvidenceSummary == null ? Map.of() : itemEvidenceSummary;
        if (jsonString(currentSummary).length() > 4_000) {
            throw new AiProviderException("AI_CONTEXT_TOO_LARGE",
                    "Tóm tắt evidence của assessment item vượt giới hạn cho phép");
        }

        String prompt = withSystemPrompt("""
                Trích xuất và cập nhật evidence cho đúng một lượt trả lời structured interview bằng tiếng Việt.
                Chỉ trả đúng một JSON object hợp lệ, không markdown. Không quyết định NEXT/PROBE/CLARIFY
                và không tạo câu hỏi follow-up.
                STAR chỉ áp dụng cho behavioral. Không chấm điểm ở bước này và không thưởng vì nói dài.
                Với technical, chỉ xét accuracy, reasoning, tradeOffs, implementationDetail, realWorldApplication.
                Với situational, chỉ xét problemIdentification, decision, reasoning, risk, alternative.
                Với cv_experience, chỉ xét personalContribution, technicalDepth, consistency, result.
                Với general, xét relevance, specificity, evidence, result.
                updatedItemSummary phải là tóm tắt evidence tích lũy ngắn, không chép lại transcript,
                không rỗng và dài tối đa %d ký tự. Nếu currentAnswer không có evidence hữu ích,
                giữ summary trước đó; nếu chưa có summary thì trả đúng câu: "%s"
                globalEvidenceDelta chỉ chứa thay đổi mới từ currentAnswer và dùng competencyId hiện tại.
                demonstratedCompetencyIds có tối đa 1 phần tử; các danh sách khác tối đa 4 phần tử.
                JSON schema:
                {
                  "keyClaims": ["string"],
                  "evidenceCoverage": %s,
                  "missingEvidence": ["string"],
                  "updatedItemSummary": "string",
                  "globalEvidenceDelta": {
                    "demonstratedCompetencyIds": ["string"],
                    "weakEvidence": ["string"],
                    "interestingClaims": ["string"],
                    "unverifiedClaims": ["string"]
                  }
                }
                EvidenceContext:
                %s
                """.formatted(
                UPDATED_ITEM_SUMMARY_MAX_LENGTH,
                EMPTY_ITEM_SUMMARY,
                jsonString(coverageSchema),
                jsonString(assessmentEvidenceContext(
                        session, coreQuestion, currentAnswer, currentSummary, questionType))
        ));

        JsonNode json = callJson(
                prompt,
                TURN_EVIDENCE_MAX_TOKENS,
                0.0,
                session.getId(),
                TURN_EVIDENCE_STAGE,
                TURN_EVIDENCE_PROMPT_VERSION
        );
        return parseProviderResponse(
                session.getId(),
                TURN_EVIDENCE_STAGE,
                TURN_EVIDENCE_PROMPT_VERSION,
                json,
                () -> answerEvidenceDraft(
                        json,
                        questionType,
                        coreQuestion.getCompetencyId(),
                        currentSummary,
                        session.getId())
        );
    }

    public List<RubricQuestionDraft> generateAdaptiveInterviewQuestions(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers
    ) {
        return generateAdaptiveInterviewQuestions(session, questions, answers, false);
    }

    public List<RubricQuestionDraft> regenerateAdaptiveInterviewQuestionsForCoverage(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers
    ) {
        return generateAdaptiveInterviewQuestions(session, questions, answers, true);
    }

    private List<RubricQuestionDraft> generateAdaptiveInterviewQuestions(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers,
            boolean coverageCorrection
    ) {
        String prompt = withSystemPrompt("""
                Tạo đúng 2 câu tiếp theo cho buổi phỏng vấn luyện tập bằng tiếng Việt.
                Chỉ trả JSON hợp lệ, không markdown.
                Chỉ dùng competencyId trong scoredCompetencyIds của EvaluationProfile.
                Hai câu phải kết hợp các mục tiêu sau theo thứ tự ưu tiên:
                - cover competency thứ 4 nếu scored set có 4 phần tử và ba câu đầu chưa cover;
                - đào sâu competency có evidence yếu, chưa rõ hoặc chưa nhất quán;
                - cross-check competency quan trọng đã có bằng chứng nhưng cần xác nhận.
                Sau 5 câu, mọi scored competency phải có ít nhất một primary question.
                CoverageStatus đã được backend tính sẵn. Nếu missingScoredCompetencyIds không rỗng,
                câu adaptive đầu tiên BẮT BUỘC dùng competencyId đầu tiên trong danh sách đó.
                CoverageCorrectionMode: %s.
                Nếu CoverageCorrectionMode=true thì kết quả trước đã bị backend từ chối do thiếu coverage;
                tuyệt đối không được lặp lại lỗi và không được thay competencyId còn thiếu bằng competency khác.
                Không bắt buộc 5 câu tương ứng với 5 competency khác nhau.
                Không lặp lại câu cũ. Mỗi câu chỉ hỏi một ý chính và trả lời trong 2-3 phút.
                JSON schema:
                {"questions": [{
                    "questionType": "behavioral|technical|situational|cv_experience|general",
                  "difficulty": "easy|medium|hard",
                  "competencyId": "string",
                  "skillTag": "string or null",
                  "question": "string",
                  "timeLimitSeconds": 180,
                  "expectedEvidence": ["string"],
                  "bars": {"level1": "string", "level3": "string", "level5": "string"}
                }]}
                EvaluationProfile: %s
                CoverageStatus: %s
                InterviewEvidenceSummary:
                %s
                """.formatted(
                        coverageCorrection,
                        jsonString(session.getEvaluationProfile()),
                        jsonString(adaptiveCoverageStatus(session, questions)),
                        jsonString(session.getEvidenceSummaryJson() == null
                                ? Map.of()
                                : session.getEvidenceSummaryJson())
                ));
        JsonNode json = callJson(
                prompt,
                ADAPTIVE_QUESTION_MAX_TOKENS,
                coverageCorrection ? 0.0 : 0.2,
                session.getId(),
                coverageCorrection ? "adaptive_questions_coverage_retry" : "adaptive_questions",
                ADAPTIVE_PROMPT_VERSION
        );
        return rubricQuestions(json.path("questions"), 2);
    }

    private Map<String, Object> adaptiveCoverageStatus(
            InterviewSession session,
            List<InterviewQuestion> questions) {
        Object rawScoredIds = session.getEvaluationProfile() == null
                ? null : session.getEvaluationProfile().get("scoredCompetencyIds");
        List<String> scoredIds = rawScoredIds instanceof java.util.Collection<?> values
                ? values.stream().map(String::valueOf).filter(value -> !value.isBlank()).distinct().toList()
                : List.of();
        Set<String> coveredIds = questions.stream()
                .map(InterviewQuestion::getCompetencyId)
                .filter(java.util.Objects::nonNull)
                .filter(scoredIds::contains)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<String> missingIds = scoredIds.stream().filter(id -> !coveredIds.contains(id)).toList();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("coveredScoredCompetencyIds", List.copyOf(coveredIds));
        status.put("missingScoredCompetencyIds", missingIds);
        return status;
    }

    public CvInterviewProfileDraft analyzeCvInterviewProfile(Map<String, Object> candidateContext) {
        String prompt = withSystemPrompt("""
                Phân tích CV để chuẩn bị một buổi phỏng vấn luyện tập bằng tiếng Việt.
                Chỉ trả JSON hợp lệ, không markdown.
                Không chấm điểm CV. Không suy luận tuổi, giới tính, ngoại hình hoặc giá trị bằng cấp.
                Chỉ trích xuất kinh nghiệm, dự án, kỹ năng và các tuyên bố có thể hỏi sâu trong phỏng vấn.
                Đề xuất từ 3 đến 5 vị trí thực tế. Nếu CV còn ít thông tin, vẫn đề xuất vai trò ở cấp độ phù hợp và giải thích ngắn.
                Giữ output ngắn gọn: summary tối đa 500 ký tự, skills tối đa 20 phần tử,
                suggestedRoles tối đa 5 phần tử và evidenceClaims tối đa 12 phần tử.
                experienceLevel chỉ được là intern|fresher|junior|middle|senior.
                JSON schema:
                {
                  "summary": "string",
                  "experienceLevel": "intern|fresher|junior|middle|senior",
                  "skills": ["string"],
                  "suggestedRoles": [
                    {"title": "string", "reason": "string"}
                  ],
                  "evidenceClaims": [
                    {"id": "claim-1", "topic": "string", "claim": "string"}
                  ]
                }
                CandidateContext:
                %s
                """.formatted(jsonString(candidateContext)));
        JsonNode json = callJson(prompt, CV_PROFILE_MAX_TOKENS, 0.2, null,
                "cv_profile", properties.getCvProfilePromptVersion());
        List<RoleSuggestionDraft> roles = roleSuggestions(json.path("suggestedRoles"));
        if (roles.isEmpty()) {
            throw new AiProviderException("AI_MISSING_FIELD", "AI thiếu danh sách vị trí gợi ý");
        }
        return new CvInterviewProfileDraft(
                requireText(json, "summary"),
                oneOf(json.path("experienceLevel").asText("fresher"),
                        List.of("intern", "fresher", "junior", "middle", "senior"), "fresher"),
                stringList(json.path("skills")).stream().limit(20).toList(),
                roles.stream().limit(5).toList(),
                evidenceClaims(json.path("evidenceClaims")).stream().limit(12).toList()
        );
    }

    @SuppressWarnings("unchecked")
    private JsonNode callJson(String userPrompt, int maxTokens, double temperature,
                              UUID sessionId, String stage, String promptVersion) {
        return callJson(userPrompt, maxTokens, temperature, sessionId, stage, promptVersion,
                properties.getProviderReadTimeoutMs());
    }

    @SuppressWarnings("unchecked")
    private JsonNode callJson(String userPrompt, int maxTokens, double temperature,
                              UUID sessionId, String stage, String promptVersion, int readTimeoutMs) {
        return callJson(userPrompt, maxTokens, temperature, sessionId, stage, promptVersion,
                readTimeoutMs, false);
    }

    @SuppressWarnings("unchecked")
    private JsonNode callJson(String userPrompt, int maxTokens, double temperature,
                              UUID sessionId, String stage, String promptVersion, int readTimeoutMs,
                              boolean requireJsonOnly) {
        return callJsonInternal(userPrompt, maxTokens, temperature, sessionId, stage, promptVersion,
                readTimeoutMs, requireJsonOnly, Function.identity());
    }

    private <T> T callJsonValidated(
            String userPrompt,
            int maxTokens,
            double temperature,
            UUID sessionId,
            String stage,
            String promptVersion,
            Function<JsonNode, T> responseParser
    ) {
        return callJsonInternal(userPrompt, maxTokens, temperature, sessionId, stage, promptVersion,
                properties.getProviderReadTimeoutMs(), false, responseParser);
    }

    @SuppressWarnings("unchecked")
    private <T> T callJsonInternal(
            String userPrompt,
            int maxTokens,
            double temperature,
            UUID sessionId,
            String stage,
            String promptVersion,
            int readTimeoutMs,
            boolean requireJsonOnly,
            Function<JsonNode, T> responseParser
    ) {
        AiInterviewProperties.ModelRoute route = routeFor(stage);
        int maxAttempts = TURN_DECISION_STAGE.equals(stage)
                ? 1
                : Math.max(1, route.getMaxAttempts());
        int effectiveReadTimeoutMs = route.getReadTimeoutMs() > 0
                ? route.getReadTimeoutMs()
                : readTimeoutMs;
        AiProviderException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.nanoTime();
            String responseContent = null;
            try {
                int requestMaxTokens = expandedStructuredOutputRetryBudget(attempt, lastFailure)
                        ? CV_PROFILE_RETRY_MAX_TOKENS
                        : maxTokens;
                String requestPrompt = promptForRetry(userPrompt, stage, lastFailure);
                RestClient client = buildClient(effectiveReadTimeoutMs);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", route.getModel());
                String endpoint;
                if (isGeminiModel(route.getModel())) {
                    endpoint = "/chat/completions";
                    body.put("messages", List.of(
                            Map.of("role", "system", "content", "You return strict JSON only."),
                            Map.of("role", "user", "content", requestPrompt)
                    ));
                    body.put("max_tokens", requestMaxTokens);
                    body.put("temperature", temperature);
                } else {
                    endpoint = "/responses";
                    body.put("instructions", "You return strict JSON only.");
                    body.put("input", requestPrompt);
                    body.put("max_output_tokens", requestMaxTokens);
                    if (route.isReasoningEnabled()) {
                        body.put("reasoning", Map.of("effort", route.getEffort()));
                    }
                }
                Map<String, Object> response = client.post()
                        .uri(endpoint)
                        .header("Authorization", "Bearer " + properties.getTextAi().getApiKey())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(Map.class);
                String value = responseOutputText(response);
                Integer inputTokens = usageTokens(response, "input_tokens", "prompt_tokens");
                Integer outputTokens = usageTokens(response, "output_tokens", "completion_tokens");
                responseContent = value.trim();
                if (requireJsonOnly && (!responseContent.startsWith("{")
                        || !responseContent.endsWith("}"))) {
                    throw new AiProviderException("AI_INVALID_JSON",
                            "AI phải chỉ trả một JSON object hợp lệ");
                }
                JsonNode parsed;
                try {
                    parsed = objectMapper.readTree(
                            requireJsonOnly ? responseContent : extractJson(value));
                } catch (JsonProcessingException | AiProviderException invalidJson) {
                    if (providerOutputWasTruncated(response)) {
                        throw new AiProviderException(
                                "AI_PROVIDER_OUTPUT_TRUNCATED",
                                "ShopAIKey đã cắt output trước khi hoàn tất JSON."
                        );
                    }
                    throw invalidJson;
                }
                T parsedResponse = responseParser.apply(parsed);
                recordTelemetry(sessionId, stage, route.getModel(), promptVersion, inputTokens, outputTokens,
                        elapsedMillis(started), true, null);
                return parsedResponse;
            } catch (JsonProcessingException | RuntimeException exception) {
                ProviderFailure failure = classifyProviderFailure(exception);
                lastFailure = failure.exception();
                boolean retryScheduled = (failure.retryable()
                        || retryableStructuredOutputFailure(failure.exception())
                        || retryableResponseContractFailure(stage, failure.exception()))
                        && attempt < maxAttempts;
                recordTelemetry(sessionId, stage, route.getModel(), promptVersion, null, null,
                        elapsedMillis(started), false, failure.exception().getCode());
                logProviderFailure(
                        sessionId,
                        stage,
                        promptVersion,
                        attempt,
                        maxAttempts,
                        retryScheduled,
                        failure,
                        exception,
                        responseContent
                );
                if (!retryScheduled) throw failure.exception();
            }
        }
        throw lastFailure == null
                ? new AiProviderException("AI_PROVIDER_FAILED",
                "Hệ thống chưa xử lý được câu trả lời này, vui lòng thử lại.")
                : lastFailure;
    }

    private String responseOutputText(Map<String, Object> response) {
        if (response == null) {
            throw new AiProviderException("AI_EMPTY_RESPONSE", "Nhà cung cấp AI không trả kết quả");
        }
        Object rawChoices = response.get("choices");
        if (rawChoices instanceof List<?> choices && !choices.isEmpty()
                && choices.get(0) instanceof Map<?, ?> choice
                && choice.get("message") instanceof Map<?, ?> message
                && message.get("content") instanceof String content
                && !content.isBlank()) {
            return content;
        }
        Object direct = response.get("output_text");
        if (direct instanceof String value && !value.isBlank()) return value;
        Object rawOutput = response.get("output");
        if (!(rawOutput instanceof List<?> output)) {
            throw new AiProviderException("AI_EMPTY_RESPONSE", "Nhà cung cấp AI không trả output");
        }
        StringBuilder text = new StringBuilder();
        for (Object rawItem : output) {
            if (!(rawItem instanceof Map<?, ?> item)) continue;
            Object rawContent = item.get("content");
            if (!(rawContent instanceof List<?> content)) continue;
            for (Object rawPart : content) {
                if (!(rawPart instanceof Map<?, ?> part)) continue;
                Object rawText = part.get("text");
                if (rawText instanceof String value && !value.isBlank()) {
                    if (!text.isEmpty()) text.append('\n');
                    text.append(value);
                }
            }
        }
        if (text.isEmpty()) {
            throw new AiProviderException("AI_EMPTY_CONTENT", "Nhà cung cấp AI không trả nội dung");
        }
        return text.toString();
    }

    private boolean isGeminiModel(String model) {
        return model != null && model.trim().toLowerCase(java.util.Locale.ROOT)
                .startsWith("gemini-");
    }

    private boolean expandedStructuredOutputRetryBudget(
            int attempt,
            AiProviderException lastFailure
    ) {
        return attempt > 1 && retryableStructuredOutputFailure(lastFailure);
    }

    private boolean retryableStructuredOutputFailure(AiProviderException failure) {
        if (failure == null) return false;
        return Set.of(
                        "AI_INVALID_JSON",
                        "AI_PROVIDER_OUTPUT_TRUNCATED",
                        "AI_EMPTY_RESPONSE",
                        "AI_EMPTY_CONTENT"
                )
                .contains(failure.getCode());
    }

    private boolean retryableResponseContractFailure(String stage, AiProviderException failure) {
        if (!"initial_questions".equals(stage) || failure == null) return false;
        return Set.of(
                        "AI_MISSING_FIELD",
                        "AI_QUESTION_TOO_LONG",
                        "AI_INVALID_QUESTION_BATCH",
                        "AI_INVALID_EVALUATION_PROFILE",
                        "AI_INVALID_SCORED_COMPETENCY_SET"
                )
                .contains(failure.getCode());
    }

    private String promptForRetry(
            String originalPrompt,
            String stage,
            AiProviderException lastFailure
    ) {
        if (!retryableResponseContractFailure(stage, lastFailure)) return originalPrompt;
        return originalPrompt + """

                QUAN TRỌNG: response trước đã bị backend từ chối (%s: %s).
                Hãy tạo lại TOÀN BỘ JSON từ đầu theo đúng schema. Giữ đúng 3 questions có BARS hợp lệ.
                Mỗi questions[].question phải có tối đa %d ký tự, chỉ hỏi một ý chính và chỉ có một dấu hỏi (?).
                """.formatted(
                lastFailure.getCode(),
                lastFailure.getMessage(),
                QUESTION_TARGET_MAX_LENGTH
        );
    }

    private boolean providerOutputWasTruncated(Map<String, Object> response) {
        if (response == null) return false;
        Object rawChoices = response.get("choices");
        if (!(rawChoices instanceof List<?> choices) || choices.isEmpty()
                || !(choices.get(0) instanceof Map<?, ?> choice)) {
            return false;
        }
        Object rawReason = choice.get("finish_reason");
        if (!(rawReason instanceof String reason)) return false;
        String normalized = reason.trim().toLowerCase(java.util.Locale.ROOT);
        return Set.of("length", "max_tokens", "max_output_tokens").contains(normalized);
    }

    private AiInterviewProperties.ModelRoute routeFor(String stage) {
        AiInterviewProperties.TextAi textAi = properties.getTextAi();
        AiInterviewProperties.ModelRoute route = switch (stage) {
            case "cv_profile" -> textAi.getCvAnalysis();
            case "initial_questions" -> textAi.getInitialQuestions();
            case TURN_DECISION_STAGE -> textAi.getTurnDecision();
            case TURN_EVIDENCE_STAGE -> textAi.getTurnEvidence();
            case "adaptive_questions", "adaptive_questions_coverage_retry" -> textAi.getAdaptiveQuestions();
            case TRANSCRIPT_CORRECTION_STAGE -> textAi.getTranscriptCorrection();
            case "interview_evaluation" -> textAi.getFinalEvaluation();
            default -> throw new AiProviderException("AI_STAGE_ROUTE_MISSING",
                    "Chưa cấu hình model cho AI stage " + stage);
        };
        if (route == null || !StringUtils.hasText(route.getModel())) {
            throw new AiProviderException("AI_STAGE_ROUTE_INVALID",
                    "Cấu hình model không hợp lệ cho AI stage " + stage);
        }
        Set<String> allowedEfforts = Set.of("none", "low", "medium", "high", "xhigh", "max");
        String normalizedEffort = StringUtils.hasText(route.getEffort())
                ? route.getEffort().trim().toLowerCase(java.util.Locale.ROOT)
                : "none";
        if (route.isReasoningEnabled() && !allowedEfforts.contains(normalizedEffort)) {
            throw new AiProviderException("AI_REASONING_EFFORT_INVALID",
                    "Reasoning effort không hợp lệ cho AI stage " + stage);
        }
        if (route.getMaxAttempts() <= 0) {
            throw new AiProviderException("AI_STAGE_ROUTE_INVALID",
                    "Số lần gọi provider không hợp lệ cho AI stage " + stage);
        }
        return new AiInterviewProperties.ModelRoute(
                route.getModel().trim(),
                normalizedEffort,
                route.isReasoningEnabled(),
                route.getMaxAttempts(),
                route.getReadTimeoutMs());
    }

    static AiProviderException mapProviderException(Exception exception) {
        return classifyProviderFailure(exception).exception();
    }

    private static ProviderFailure classifyProviderFailure(Exception exception) {
        if (exception instanceof AiProviderException providerException) {
            return new ProviderFailure(providerException, false, null);
        }
        RestClientResponseException responseException = findCause(
                exception, RestClientResponseException.class);
        if (responseException != null) {
            int status = responseException.getStatusCode().value();
            String responseBody = responseException.getResponseBodyAsString();
            boolean unsupportedConversion = responseBody != null
                    && responseBody.toLowerCase(java.util.Locale.ROOT)
                    .contains("convert_request_failed");
            return new ProviderFailure(
                    new AiProviderException(
                            unsupportedConversion
                                    ? "AI_PROVIDER_UNSUPPORTED_REQUEST"
                                    : "AI_PROVIDER_HTTP_ERROR",
                            unsupportedConversion
                                    ? "ShopAIKey không hỗ trợ định dạng request này cho model đã chọn."
                                    : "ShopAIKey trả lỗi HTTP " + status + ", vui lòng thử lại."
                    ),
                    !unsupportedConversion && status >= 500 && status <= 599,
                    status
            );
        }
        boolean timedOut = hasCause(exception, SocketTimeoutException.class)
                || hasCause(exception, HttpTimeoutException.class);
        if (timedOut) {
            return new ProviderFailure(
                    new AiProviderException(
                            "AI_PROVIDER_TIMEOUT",
                            "ShopAIKey phản hồi quá thời gian cho phép, vui lòng thử lại."
                    ),
                    true,
                    null
            );
        }
        if (findCause(exception, ResourceAccessException.class) != null) {
            return new ProviderFailure(
                    new AiProviderException(
                            "AI_PROVIDER_CONNECTION_FAILED",
                            "Không thể kết nối ổn định tới ShopAIKey, vui lòng thử lại."
                    ),
                    true,
                    null
            );
        }
        if (exception instanceof JsonProcessingException) {
            return new ProviderFailure(
                    new AiProviderException(
                            "AI_INVALID_JSON",
                            "ShopAIKey trả JSON không hợp lệ, vui lòng thử lại."
                    ),
                    false,
                    null
            );
        }
        if (findCause(exception, ClassCastException.class) != null) {
            return new ProviderFailure(
                    new AiProviderException(
                            "AI_INVALID_PROVIDER_RESPONSE",
                            "ShopAIKey trả response sai cấu trúc, vui lòng thử lại."
                    ),
                    false,
                    null
            );
        }
        return new ProviderFailure(
                new AiProviderException(
                        "AI_PROVIDER_FAILED",
                        "Hệ thống chưa xử lý được câu trả lời này, vui lòng thử lại."
                ),
                false,
                null
        );
    }

    private void logProviderFailure(
            UUID sessionId,
            String stage,
            String promptVersion,
            int attempt,
            int maxAttempts,
            boolean retryScheduled,
            ProviderFailure failure,
            Exception source,
            String responseContent
    ) {
        Throwable root = rootCause(source);
        String responseBody = responseContent;
        RestClientResponseException responseException = findCause(
                source, RestClientResponseException.class);
        if (responseBody == null && responseException != null) {
            responseBody = responseException.getResponseBodyAsString();
        }
        log.warn("ShopAIKey call failed: sessionId={}, stage={}, promptVersion={}, attempt={}/{}, "
                        + "retryScheduled={}, code={}, httpStatus={}, exceptionType={}, rootCauseType={}, "
                        + "detail=\"{}\", rootDetail=\"{}\", responseBody=\"{}\"",
                sessionId,
                stage,
                promptVersion,
                attempt,
                maxAttempts,
                retryScheduled,
                failure.exception().getCode(),
                failure.httpStatus(),
                source.getClass().getSimpleName(),
                root.getClass().getSimpleName(),
                compactLogValue(source.getMessage()),
                compactLogValue(root.getMessage()),
                compactLogValue(responseBody));
    }

    private <T> T parseProviderResponse(
            UUID sessionId,
            String stage,
            String promptVersion,
            JsonNode response,
            Supplier<T> parser
    ) {
        try {
            return parser.get();
        } catch (AiProviderException exception) {
            log.warn("ShopAIKey response rejected: sessionId={}, stage={}, promptVersion={}, code={}, "
                            + "detail=\"{}\", responseBody=\"{}\"",
                    sessionId,
                    stage,
                    promptVersion,
                    exception.getCode(),
                    compactLogValue(exception.getMessage()),
                    compactLogValue(response == null ? null : response.toString()));
            throw exception;
        }
    }

    private String compactLogValue(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", " ")
                .trim()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) return causeType.cast(current);
            current = current.getCause();
        }
        return null;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        return findCause(throwable, causeType) != null;
    }

    private record ProviderFailure(
            AiProviderException exception,
            boolean retryable,
            Integer httpStatus
    ) {
    }

    private String buildContext(InterviewSession session, CandidateProfile candidate) {
        String sessionContext = "Session=" + buildSessionContext(session);
        if ("practice".equals(session.getSessionType())) {
            return sessionContext;
        }
        return sessionContext
                + "; Candidate skills=" + candidate.getSkills()
                + "; Candidate bio=" + safe(candidate.getBio())
                + "; Candidate headline=" + safe(candidate.getHeadline());
    }

    private String buildSessionContext(InterviewSession session) {
        Job job = session.getJob();
        String jobText = job == null ? "none" : job.getTitle() + " / " + job.getRequirementsText();
        return "type=" + ("job_based".equals(session.getSessionType()) ? "application" : "practice")
                + "; title=" + session.getTitle()
                + "; job=" + jobText
                + "; practiceContext=" + session.getPracticeContext();
    }

    private void validateAssessmentTurnInput(
            InterviewSession session,
            InterviewQuestion coreQuestion,
            String currentAnswer,
            AssessmentTurnCounters counters
    ) {
        if (session == null || session.getId() == null) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT", "Thiếu session để phân tích câu trả lời");
        }
        if (coreQuestion == null || coreQuestion.getId() == null
                || !StringUtils.hasText(coreQuestion.getContent())
                || !StringUtils.hasText(coreQuestion.getCompetencyId())
                || coreQuestion.getRubric() == null || coreQuestion.getRubric().isEmpty()) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT",
                    "Thiếu core question, competency hoặc BARS để phân tích câu trả lời");
        }
        if (!StringUtils.hasText(currentAnswer)) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT", "Không có câu trả lời để phân tích");
        }
        if (counters == null || counters.probeCount() < 0 || counters.clarifyCount() < 0
                || counters.totalAssessmentTurns() < 0 || counters.remainingCoreQuestions() < 0) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT", "Bộ đếm assessment turn không hợp lệ");
        }
    }

    private void validateAssessmentEvidenceInput(
            InterviewSession session,
            InterviewQuestion coreQuestion,
            String currentAnswer
    ) {
        if (session == null || session.getId() == null) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT",
                    "Thiếu session để phân tích evidence");
        }
        if (coreQuestion == null || coreQuestion.getId() == null
                || !StringUtils.hasText(coreQuestion.getContent())
                || !StringUtils.hasText(coreQuestion.getCompetencyId())
                || coreQuestion.getRubric() == null || coreQuestion.getRubric().isEmpty()) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT",
                    "Thiếu core question, competency hoặc BARS để phân tích evidence");
        }
        if (!StringUtils.hasText(currentAnswer)) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT",
                    "Không có câu trả lời để phân tích evidence");
        }
    }

    private Map<String, Object> assessmentDecisionContext(
            InterviewSession session,
            InterviewQuestion coreQuestion,
            String currentAnswer,
            AssessmentTurnCounters counters,
            String questionType
    ) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionType", questionType);
        question.put("competencyId", coreQuestion.getCompetencyId());
        Map<String, Object> competency = relevantCompetency(session, coreQuestion.getCompetencyId());
        question.put("competencyName", competency.getOrDefault("name", coreQuestion.getCompetencyId()));
        question.put("question", coreQuestion.getContent());
        question.put("expectedEvidence",
                coreQuestion.getRubric().getOrDefault("expectedEvidence", List.of()));

        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("probeCount", counters.probeCount());
        limits.put("clarifyCount", counters.clarifyCount());
        limits.put("totalAssessmentTurns", counters.totalAssessmentTurns());
        limits.put("remainingCoreQuestions", counters.remainingCoreQuestions());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("coreQuestion", question);
        context.put("currentAnswer", currentAnswer.replaceAll("\\s+", " ").trim());
        context.put("counters", limits);
        return context;
    }

    private Map<String, Object> assessmentEvidenceContext(
            InterviewSession session,
            InterviewQuestion coreQuestion,
            String currentAnswer,
            Map<String, Object> itemEvidenceSummary,
            String questionType
    ) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionId", coreQuestion.getId().toString());
        question.put("questionType", questionType);
        question.put("competencyId", coreQuestion.getCompetencyId());
        question.put("competency", relevantCompetency(session, coreQuestion.getCompetencyId()));
        question.put("question", coreQuestion.getContent());
        question.put("rubric", coreQuestion.getRubric());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("coreQuestion", question);
        context.put("currentAnswer", currentAnswer.replaceAll("\\s+", " ").trim());
        context.put("itemEvidenceSummary", itemEvidenceSummary);
        return context;
    }

    private Map<String, Object> relevantCompetency(InterviewSession session, String competencyId) {
        Map<String, Object> profile = session.getEvaluationProfile();
        Object rawCompetencies = profile == null ? null : profile.get("competencies");
        if (!(rawCompetencies instanceof Iterable<?> competencies)) {
            return Map.of("id", competencyId);
        }
        for (Object raw : competencies) {
            if (!(raw instanceof Map<?, ?> competency)
                    || !competencyId.equals(String.valueOf(competency.get("id")))) {
                continue;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", competencyId);
            copyIfPresent(competency, result, "name");
            copyIfPresent(competency, result, "definition");
            copyIfPresent(competency, result, "measurementMode");
            return result;
        }
        return Map.of("id", competencyId);
    }

    private void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private String normalizedQuestionType(String rawQuestionType) {
        String questionType = safe(rawQuestionType).trim().toLowerCase(java.util.Locale.ROOT);
        if (!List.of("behavioral", "technical", "situational", "cv_experience", "general")
                .contains(questionType)) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT",
                    "Question type không được hỗ trợ để phân tích evidence");
        }
        return questionType;
    }

    private List<String> evidenceDimensions(String questionType) {
        return switch (questionType) {
            case "behavioral" -> List.of("situation", "task", "action", "result");
            case "technical" -> List.of(
                    "accuracy", "reasoning", "tradeOffs", "implementationDetail", "realWorldApplication");
            case "situational" -> List.of(
                    "problemIdentification", "decision", "reasoning", "risk", "alternative");
            case "cv_experience" -> List.of(
                    "personalContribution", "technicalDepth", "consistency", "result");
            case "general" -> List.of("relevance", "specificity", "evidence", "result");
            default -> throw new AiProviderException("AI_INVALID_ANALYSIS_CONTEXT",
                    "Question type không được hỗ trợ để phân tích evidence");
        };
    }

    private String withSystemPrompt(String prompt) {
        String custom = systemSettingsService.getString(SystemSettingsService.AI_SYSTEM_PROMPT, "");
        if (!StringUtils.hasText(custom)) {
            return prompt;
        }
        return "Hướng dẫn hệ thống từ admin:\n" + custom.trim() + "\n\n" + prompt;
    }

    private String withFeedbackPrompt(String prompt) {
        String custom = systemSettingsService.getString(SystemSettingsService.AI_FEEDBACK_PROMPT, "");
        if (!StringUtils.hasText(custom)) {
            return prompt;
        }
        return "Hướng dẫn đánh giá từ admin:\n" + custom.trim() + "\n\n" + prompt;
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiProviderException("AI_INVALID_JSON", "AI không trả JSON hợp lệ");
        }
        return content.substring(start, end + 1);
    }

    private String requireText(JsonNode json, String field) {
        String value = json.path(field).asText("");
        if (value.isBlank()) {
            throw new AiProviderException("AI_MISSING_FIELD", "AI thieu truong " + field);
        }
        return value;
    }

    private String conciseQuestion(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            throw new AiProviderException("AI_INVALID_QUESTION_BATCH", "AI trả câu hỏi rỗng");
        }
        long questionMarkCount = normalized.chars().filter(character -> character == '?').count();
        if (normalized.length() > QUESTION_HARD_MAX_LENGTH || questionMarkCount > 1) {
            throw new AiProviderException(
                    "AI_QUESTION_TOO_LONG",
                    "AI tạo câu hỏi vượt giới hạn hoặc ghép nhiều câu hỏi"
            );
        }
        return normalized;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("");
        return value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("");
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
        }
        return values;
    }

    private List<RoleSuggestionDraft> roleSuggestions(JsonNode node) {
        List<RoleSuggestionDraft> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String title = item.path("title").asText("").trim();
                String reason = item.path("reason").asText("").trim();
                if (!title.isBlank() && !reason.isBlank()) {
                    values.add(new RoleSuggestionDraft(title, reason));
                }
            });
        }
        return values;
    }

    private List<EvidenceClaimDraft> evidenceClaims(JsonNode node) {
        List<EvidenceClaimDraft> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            int[] index = {1};
            node.forEach(item -> {
                String topic = item.path("topic").asText("").trim();
                String claim = item.path("claim").asText("").trim();
                String id = item.path("id").asText("").trim();
                if (!topic.isBlank() && !claim.isBlank()) {
                    values.add(new EvidenceClaimDraft(
                            id.isBlank() ? "claim-" + index[0] : id,
                            topic,
                            claim
                    ));
                    index[0]++;
                }
            });
        }
        return values;
    }

    private AnswerDecisionDraft answerDecisionDraft(JsonNode json, UUID sessionId) {
        if (json == null || !json.isObject()) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI phải trả object hợp lệ cho turn decision");
        }

        AnswerAnalysisAction action;
        try {
            action = AnswerAnalysisAction.valueOf(
                    requireText(json, "action").trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_ACTION",
                    "AI chỉ được trả action NEXT, PROBE hoặc CLARIFY");
        }
        return new AnswerDecisionDraft(
                action,
                validatedFollowUp(json.get("followUp"), action, sessionId)
        );
    }

    private AnswerEvidenceDraft answerEvidenceDraft(
            JsonNode json,
            String questionType,
            String competencyId,
            Map<String, Object> itemEvidenceSummary,
            UUID sessionId
    ) {
        if (json == null || !json.isObject()) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI phải trả object hợp lệ cho turn evidence");
        }
        List<String> keyClaims = tolerantCompactStringList(
                json, "keyClaims", 6, 240, sessionId);
        Map<String, Boolean> coverage = tolerantEvidenceCoverage(
                json.get("evidenceCoverage"), questionType, sessionId);
        List<String> missingEvidence = tolerantCompactStringList(
                json, "missingEvidence", 6, 240, sessionId);
        String updatedItemSummary = normalizedUpdatedItemSummary(
                json,
                itemEvidenceSummary,
                keyClaims,
                sessionId
        );
        GlobalEvidenceDeltaDraft globalDelta = tolerantGlobalEvidenceDelta(
                json.get("globalEvidenceDelta"), competencyId, sessionId);

        return new AnswerEvidenceDraft(
                keyClaims,
                coverage,
                missingEvidence,
                updatedItemSummary,
                globalDelta
        );
    }

    private String normalizedUpdatedItemSummary(
            JsonNode parent,
            Map<String, Object> itemEvidenceSummary,
            List<String> keyClaims,
            UUID sessionId
    ) {
        JsonNode node = parent == null ? null : parent.get("updatedItemSummary");
        if (node == null || !node.isTextual()) {
            String fallback = fallbackItemSummary(itemEvidenceSummary, keyClaims);
            logEvidenceNormalization(sessionId, "UPDATED_ITEM_SUMMARY_DEFAULTED",
                    "updatedItemSummary bị thiếu hoặc không phải chuỗi");
            return fallback;
        }
        String value = node.asText().replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            String fallback = fallbackItemSummary(itemEvidenceSummary, keyClaims);
            log.warn("AI updatedItemSummary normalized: sessionId={}, reason=EMPTY, fallbackLength={}",
                    sessionId, fallback.length());
            return fallback;
        }
        if (value.length() > UPDATED_ITEM_SUMMARY_MAX_LENGTH) {
            String truncated = truncateAtWordBoundary(value, UPDATED_ITEM_SUMMARY_MAX_LENGTH);
            log.warn("AI updatedItemSummary normalized: sessionId={}, reason=TOO_LONG, "
                            + "originalLength={}, normalizedLength={}",
                    sessionId, value.length(), truncated.length());
            return truncated;
        }
        return value;
    }

    private String fallbackItemSummary(
            Map<String, Object> itemEvidenceSummary,
            List<String> keyClaims
    ) {
        Object rawPreviousSummary = itemEvidenceSummary == null
                ? null
                : itemEvidenceSummary.get("summary");
        String previousSummary = rawPreviousSummary instanceof String value
                ? value.replaceAll("\\s+", " ").trim()
                : "";
        if (!previousSummary.isEmpty()) {
            return truncateAtWordBoundary(previousSummary, UPDATED_ITEM_SUMMARY_MAX_LENGTH);
        }
        String claimSummary = keyClaims == null
                ? ""
                : keyClaims.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        return claimSummary.isEmpty()
                ? EMPTY_ITEM_SUMMARY
                : truncateAtWordBoundary(claimSummary, UPDATED_ITEM_SUMMARY_MAX_LENGTH);
    }

    private String truncateAtWordBoundary(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        int boundary = value.lastIndexOf(' ', maxLength);
        if (boundary <= 0) boundary = maxLength;
        return value.substring(0, boundary).trim();
    }

    private TranscriptCorrectionDraft transcriptCorrectionDraft(JsonNode json) {
        requireExactFields(json, Set.of(
                "correctedTranscript",
                "corrections",
                "evidence",
                "answerSummary",
                "followUpNeeded",
                "followUpReason"
        ), "transcriptCorrection");
        String correctedTranscript = compactRequiredText(json, "correctedTranscript", 12_000);
        List<TranscriptCorrectionItemDraft> corrections = transcriptCorrectionItems(
                json.path("corrections"));
        List<TranscriptEvidenceDraft> evidence = transcriptEvidence(json.path("evidence"));
        String answerSummary = compactRequiredText(json, "answerSummary", 1_200);
        JsonNode followUpNeededNode = json.get("followUpNeeded");
        if (followUpNeededNode == null || !followUpNeededNode.isBoolean()) {
            throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                    "AI phải trả boolean cho followUpNeeded");
        }
        boolean followUpNeeded = followUpNeededNode.booleanValue();
        String followUpReason = textOrNull(json.get("followUpReason"));
        if ((followUpNeeded && !StringUtils.hasText(followUpReason))
                || (!followUpNeeded && followUpReason != null)
                || (followUpReason != null && followUpReason.length() > 500)) {
            throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                    "followUpReason không khớp followUpNeeded");
        }
        return new TranscriptCorrectionDraft(
                correctedTranscript,
                corrections,
                evidence,
                answerSummary,
                followUpNeeded,
                followUpReason
        );
    }

    private List<TranscriptCorrectionItemDraft> transcriptCorrectionItems(JsonNode node) {
        int maxItems = Math.max(0, properties.getTranscriptCorrectionMaxCorrections());
        if (node == null || !node.isArray() || node.size() > maxItems) {
            throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                    "AI trả corrections không hợp lệ");
        }
        List<TranscriptCorrectionItemDraft> result = new ArrayList<>();
        Set<String> allowedReasons = Set.of(
                "current_question", "cv_term", "job_term", "technical_vocabulary", "context");
        node.forEach(item -> {
            requireExactFields(item, Set.of("original", "replacement", "confidence", "reason"),
                    "transcriptCorrectionItem");
            String original = compactRequiredText(item, "original", 200);
            String replacement = compactRequiredText(item, "replacement", 200);
            JsonNode confidenceNode = item.get("confidence");
            if (confidenceNode == null || !confidenceNode.isNumber()) {
                throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                        "AI phải trả confidence dạng số");
            }
            double confidence = confidenceNode.doubleValue();
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                        "AI trả confidence ngoài khoảng 0 đến 1");
            }
            String reason = compactRequiredText(item, "reason", 40);
            if (!allowedReasons.contains(reason)) {
                throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                        "AI trả reason correction không hợp lệ");
            }
            result.add(new TranscriptCorrectionItemDraft(original, replacement, confidence, reason));
        });
        return List.copyOf(result);
    }

    private List<TranscriptEvidenceDraft> transcriptEvidence(JsonNode node) {
        int maxItems = Math.max(0, properties.getTranscriptCorrectionMaxEvidenceItems());
        if (node == null || !node.isArray() || node.size() > maxItems) {
            throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                    "AI trả evidence không hợp lệ");
        }
        Set<String> allowedTypes = Set.of(
                "situation", "task", "action", "result", "technical_knowledge", "other");
        List<TranscriptEvidenceDraft> result = new ArrayList<>();
        node.forEach(item -> {
            requireExactFields(item, Set.of("type", "text"), "transcriptEvidence");
            String type = compactRequiredText(item, "type", 40);
            String text = compactRequiredText(item, "text", 500);
            if (!allowedTypes.contains(type)) {
                throw new AiProviderException("AI_INVALID_TRANSCRIPT_CORRECTION_SCHEMA",
                        "AI trả evidence type không hợp lệ");
            }
            result.add(new TranscriptEvidenceDraft(type, text));
        });
        return List.copyOf(result);
    }

    private String validatedFollowUp(
            JsonNode node,
            AnswerAnalysisAction action,
            UUID sessionId
    ) {
        if (action == AnswerAnalysisAction.NEXT) {
            if (node != null && !node.isNull() && StringUtils.hasText(node.asText())) {
                logDecisionNormalization(sessionId, "NEXT_FOLLOW_UP_IGNORED",
                        "action NEXT được ưu tiên và followUp bị bỏ qua");
            }
            return null;
        }
        if (node == null || !node.isTextual() || !StringUtils.hasText(node.asText())) {
            throw new AiProviderException("AI_INVALID_FOLLOW_UP",
                    "Action PROBE/CLARIFY bắt buộc có followUp");
        }
        String followUp = node.asText().replaceAll("\\s+", " ").trim();
        if (followUp.length() > FOLLOW_UP_MAX_LENGTH) {
            throw new AiProviderException("AI_INVALID_FOLLOW_UP",
                    "Follow-up vượt giới hạn độ dài cho phép");
        }
        if (!followUp.endsWith("?")) {
            followUp = followUp.replaceFirst("[.!;:…]+$", "").trim() + "?";
            logDecisionNormalization(sessionId, "FOLLOW_UP_PUNCTUATION_NORMALIZED",
                    "dấu kết thúc followUp được chuẩn hóa thành dấu hỏi");
        }
        return followUp;
    }

    private Map<String, Boolean> tolerantEvidenceCoverage(
            JsonNode node,
            String questionType,
            UUID sessionId
    ) {
        List<String> dimensions = evidenceDimensions(questionType);
        Map<String, Boolean> values = new LinkedHashMap<>();
        boolean normalized = node == null || !node.isObject();
        for (String dimension : dimensions) {
            JsonNode value = node != null && node.isObject() ? node.get(dimension) : null;
            if (value != null && value.isBoolean()) {
                values.put(dimension, value.booleanValue());
            } else {
                values.put(dimension, false);
                normalized = true;
            }
        }
        if (normalized) {
            logEvidenceNormalization(sessionId, "EVIDENCE_COVERAGE_DEFAULTED",
                    "evidenceCoverage thiếu hoặc chứa giá trị không phải boolean");
        }
        return Map.copyOf(values);
    }

    private GlobalEvidenceDeltaDraft tolerantGlobalEvidenceDelta(
            JsonNode node,
            String competencyId,
            UUID sessionId
    ) {
        if (node == null || !node.isObject()) {
            logEvidenceNormalization(sessionId, "GLOBAL_EVIDENCE_DELTA_DEFAULTED",
                    "globalEvidenceDelta bị thiếu hoặc không phải object");
            return new GlobalEvidenceDeltaDraft(List.of(), List.of(), List.of(), List.of());
        }
        List<String> rawDemonstratedIds = tolerantCompactStringList(
                node, "demonstratedCompetencyIds", 6, 100, sessionId);
        List<String> demonstratedIds = rawDemonstratedIds.stream()
                .filter(competencyId::equals)
                .distinct()
                .limit(1)
                .toList();
        if (demonstratedIds.size() != rawDemonstratedIds.size()) {
            logEvidenceNormalization(sessionId, "UNKNOWN_COMPETENCY_IDS_FILTERED",
                    "competency ID ngoài assessment item hiện tại đã bị lọc");
        }
        return new GlobalEvidenceDeltaDraft(
                demonstratedIds,
                tolerantCompactStringList(node, "weakEvidence", 4, 240, sessionId),
                tolerantCompactStringList(node, "interestingClaims", 4, 240, sessionId),
                tolerantCompactStringList(node, "unverifiedClaims", 4, 240, sessionId)
        );
    }

    private List<String> tolerantCompactStringList(
            JsonNode parent,
            String field,
            int maxItems,
            int maxLength,
            UUID sessionId
    ) {
        JsonNode node = parent == null ? null : parent.get(field);
        if (node == null || !node.isArray()) {
            logEvidenceNormalization(sessionId, "OPTIONAL_LIST_DEFAULTED",
                    field + " bị thiếu hoặc không phải array");
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        boolean sanitized = node.size() > maxItems;
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                sanitized = true;
                continue;
            }
            String value = item.asText().replaceAll("\\s+", " ").trim();
            if (value.isEmpty()) {
                sanitized = true;
                continue;
            }
            String normalized = truncateAtWordBoundary(value, maxLength);
            boolean added = values.add(normalized);
            if (!normalized.equals(value) || !added) {
                sanitized = true;
            }
            if (values.size() == maxItems) break;
        }
        if (sanitized) {
            logEvidenceNormalization(sessionId, "OPTIONAL_LIST_SANITIZED",
                    field + " chứa phần tử không hợp lệ, trùng hoặc vượt giới hạn");
        }
        return List.copyOf(values);
    }

    private void logDecisionNormalization(UUID sessionId, String code, String detail) {
        logResponseNormalization(
                sessionId, TURN_DECISION_STAGE, TURN_DECISION_PROMPT_VERSION, code, detail);
    }

    private void logEvidenceNormalization(UUID sessionId, String code, String detail) {
        logResponseNormalization(
                sessionId, TURN_EVIDENCE_STAGE, TURN_EVIDENCE_PROMPT_VERSION, code, detail);
    }

    private void logResponseNormalization(
            UUID sessionId,
            String stage,
            String promptVersion,
            String code,
            String detail
    ) {
        log.warn("ShopAIKey assessment response normalized: sessionId={}, stage={}, "
                        + "promptVersion={}, code={}, detail=\"{}\"",
                sessionId,
                stage,
                promptVersion,
                code,
                compactLogValue(detail));
    }

    private List<String> requiredCompactStringList(
            JsonNode parent,
            String field,
            int maxItems,
            int maxLength
    ) {
        JsonNode node = parent == null ? null : parent.get(field);
        if (node == null || !node.isArray() || node.size() > maxItems) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI phải trả array hợp lệ cho trường " + field);
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (!item.isTextual()) {
                throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                        "AI phải trả chuỗi trong trường " + field);
            }
            String value = item.asText().replaceAll("\\s+", " ").trim();
            if (value.isEmpty() || value.length() > maxLength) {
                throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                        "AI trả nội dung không hợp lệ trong trường " + field);
            }
            values.add(value);
        });
        if (new HashSet<>(values).size() != values.size()) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI trả phần tử trùng trong trường " + field);
        }
        return List.copyOf(values);
    }

    private String compactRequiredText(JsonNode parent, String field, int maxLength) {
        JsonNode node = parent == null ? null : parent.get(field);
        if (node == null || !node.isTextual()) {
            throw new AiProviderException("AI_MISSING_FIELD", "AI thiếu trường " + field);
        }
        String value = node.asText().replaceAll("\\s+", " ").trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI trả nội dung không hợp lệ trong trường " + field);
        }
        return value;
    }

    private void requireExactFields(JsonNode node, Set<String> expectedFields, String objectName) {
        if (node == null || !node.isObject()) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI phải trả object hợp lệ cho " + objectName);
        }
        Set<String> actualFields = new HashSet<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(expectedFields)) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI trả sai schema cho " + objectName);
        }
    }

    private EvaluationProfileDraft evaluationProfile(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new AiProviderException("AI_MISSING_FIELD", "AI thiếu Evaluation Profile");
        }
        List<CompetencyDraft> competencies = new ArrayList<>();
        Set<String> competencyIds = new HashSet<>();
        JsonNode competencyNodes = node.path("competencies");
        if (competencyNodes.isArray()) {
            competencyNodes.forEach(item -> {
                String id = item.path("id").asText("").trim();
                String name = item.path("name").asText("").trim();
                String definition = item.path("definition").asText("").trim();
                if (!id.isBlank() && !name.isBlank() && !definition.isBlank() && competencyIds.add(id)) {
                    competencies.add(new CompetencyDraft(
                            id,
                            name,
                            definition,
                            requiredScaleScore(item, "importance"),
                            requiredScaleScore(item, "entryNeedScore"),
                            requiredScaleScore(item, "distinguishingValueScore"),
                            oneOf(item.path("measurementMode").asText("content"),
                                    List.of("content", "voice", "both"), "content"),
                            item.path("rationale").asText("").trim()
                    ));
                }
            });
        }
        if (competencies.size() < 3 || competencies.size() > 6) {
            throw new AiProviderException("AI_INVALID_EVALUATION_PROFILE",
                    "AI phải trả từ 3 đến 6 năng lực trong Evaluation Profile");
        }
        List<String> scoredCompetencyIds = stringList(node.path("scoredCompetencyIds")).stream()
                .distinct()
                .toList();
        if (scoredCompetencyIds.size() < 3 || scoredCompetencyIds.size() > 4
                || !competencyIds.containsAll(scoredCompetencyIds)) {
            throw new AiProviderException("AI_INVALID_SCORED_COMPETENCY_SET",
                    "AI phải chọn 3 đến 4 scored competency hợp lệ");
        }
        return new EvaluationProfileDraft(
                requireText(node, "targetRole"),
                oneOf(node.path("seniority").asText("fresher"),
                        List.of("intern", "fresher", "junior", "middle", "senior"), "fresher"),
                clampInt(node.path("communicationDemand").asInt(1), 1, 5),
                scoredCompetencyIds,
                List.copyOf(competencies)
        );
    }

    public InterviewEvaluationDraft evaluateInterview(
            InterviewSession session,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers
    ) {
        Map<UUID, InterviewAnswer> answersByQuestion = answers.stream()
                .collect(java.util.stream.Collectors.toMap(
                        InterviewAnswer::getQuestionId,
                        answer -> answer,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        List<GroupedAssessmentEvidence> groupedEvidence = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            InterviewAnswer answer = answersByQuestion.get(question.getId());
            if (answer == null || answer.isSkipped()) {
                continue;
            }
            groupedEvidence.add(new GroupedAssessmentEvidence(
                    question.getId().toString(),
                    question.getCompetencyId(),
                    normalizedQuestionType(question.getQuestionType()),
                    question.getContent(),
                    question.getRubric(),
                    List.of(new EvidenceTurnDraft("CORE_QUESTION", safe(answer.getTranscriptText())))
            ));
        }
        return evaluateGroupedInterview(session, groupedEvidence);
    }

    public InterviewEvaluationDraft evaluateGroupedInterview(
            InterviewSession session,
            List<GroupedAssessmentEvidence> groupedEvidence
    ) {
        validateGroupedAssessmentEvidence(session, groupedEvidence);
        String prompt = withFeedbackPrompt("""
                Đánh giá toàn bộ buổi phỏng vấn luyện tập bằng tiếng Việt.
                Chỉ trả JSON hợp lệ, không markdown và không tự tính điểm tổng.
                Mỗi assessment item gồm một CORE_QUESTION và các evidenceTurns theo đúng thứ tự thời gian.
                Hãy gộp evidence từ câu trả lời core, PROBE và CLARIFY của cùng item, sau đó trả đúng một
                BARS level 1-5 duy nhất theo rubric của core question. Tuyệt đối không chấm hoặc tính trung bình
                từng probe/clarify như câu hỏi độc lập.
                Không thưởng điểm chỉ vì câu trả lời dài hoặc kể đúng hình thức STAR.
                STAR chỉ hỗ trợ tìm evidence cho behavioral; BARS vẫn là căn cứ chấm điểm.
                Không dùng CV, bằng cấp, trường học, tuổi, giới tính hoặc ngoại hình để chấm.
                Phải trả đúng một rating cho mỗi questionId có trong GroupedEvaluationInput.
                Input không chứa câu skipped/not answered. Không tự tạo rating cho câu không có trong input.
                Mọi phần tử questionRatings đều ngầm có evaluationStatus=RATED; backend xử lý NOT_ANSWERED riêng.
                JSON schema:
                {
                  "questionRatings": [{
                    "questionId": "uuid",
                    "competencyId": "string",
                    "barsLevel": 1,
                    "evidence": ["string"],
                    "missingEvidence": ["string"]
                  }],
                  "summary": "string",
                  "strengths": ["string"],
                  "improvements": ["string"],
                  "actionPlan": ["string"]
                }
                actionPlan bắt buộc có ít nhất một hành động cụ thể, đo lường được và không lặp lại nguyên văn improvements.
                GroupedEvaluationInput:
                %s
                """.formatted(groupedEvaluationInput(session, groupedEvidence)));
        JsonNode json = callJson(
                prompt,
                FINAL_EVALUATION_MAX_TOKENS,
                0.1,
                session.getId(),
                "interview_evaluation",
                GROUPED_EVALUATION_PROMPT_VERSION
        );
        List<QuestionRatingDraft> ratings = questionRatings(json.path("questionRatings"));
        validateGroupedRatings(groupedEvidence, ratings);
        return new InterviewEvaluationDraft(
                ratings,
                requireText(json, "summary"),
                stringList(json.path("strengths")),
                stringList(json.path("improvements")),
                requiredNonEmptyCompactStringList(json, "actionPlan", 6, 300)
        );
    }

    private List<String> requiredNonEmptyCompactStringList(
            JsonNode parent,
            String field,
            int maxItems,
            int maxLength
    ) {
        List<String> values = requiredCompactStringList(parent, field, maxItems, maxLength);
        if (values.isEmpty()) {
            throw new AiProviderException("AI_INVALID_ANALYSIS_SCHEMA",
                    "AI phải trả ít nhất một phần tử cho trường " + field);
        }
        return values;
    }

    private List<RubricQuestionDraft> rubricQuestions(JsonNode node, int expectedCount) {
        List<RubricQuestionDraft> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                JsonNode bars = item.path("bars");
                String competencyId = item.path("competencyId").asText("").trim();
                List<String> expectedEvidence = stringList(item.path("expectedEvidence"));
                if (competencyId.isBlank() || expectedEvidence.isEmpty() || !bars.isObject()) {
                    return;
                }
                values.add(new RubricQuestionDraft(
                        oneOf(item.path("questionType").asText("general"),
                                List.of("behavioral", "technical", "situational", "cv_experience", "general"),
                                "general"),
                        oneOf(item.path("difficulty").asText("medium"),
                                List.of("easy", "medium", "hard"), "medium"),
                        competencyId,
                        textOrNull(item.path("skillTag")),
                        conciseQuestion(item.path("question").asText("")),
                        clampInt(item.path("timeLimitSeconds").asInt(properties.getAudioMaxSeconds()),
                                30, properties.getAudioMaxSeconds()),
                        expectedEvidence,
                        requireText(bars, "level1"),
                        requireText(bars, "level3"),
                        requireText(bars, "level5")
                ));
            });
        }
        if (values.size() != expectedCount) {
            throw new AiProviderException("AI_INVALID_QUESTION_BATCH",
                    "AI không trả đúng " + expectedCount + " câu hỏi có BARS hợp lệ");
        }
        return List.copyOf(values);
    }

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AiProviderException("AI_CONTEXT_SERIALIZATION_FAILED", "Không thể chuẩn bị dữ liệu CV cho AI");
        }
    }

    private Integer usageTokens(Map<String, Object> response, String primaryKey, String alternateKey) {
        if (response == null || !(response.get("usage") instanceof Map<?, ?> usage)) return null;
        Object value = usage.get(primaryKey);
        if (!(value instanceof Number)) value = usage.get(alternateKey);
        return value instanceof Number number ? Math.max(0, number.intValue()) : null;
    }

    private void recordTelemetry(UUID sessionId, String stage, String model, String promptVersion,
                                 Integer inputTokens, Integer outputTokens, long latencyMs,
                                 boolean success, String errorCode) {
        if (telemetryService == null) return;
        try {
            telemetryService.record(sessionId, stage, model, promptVersion,
                    inputTokens, outputTokens, latencyMs, success, errorCode);
        } catch (RuntimeException ignored) {
            // Telemetry must not hide or change the provider result.
        }
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private String evaluationInput(InterviewSession session,
                                   List<InterviewQuestion> questions,
                                   List<InterviewAnswer> answers) {
        Map<UUID, InterviewAnswer> answersByQuestion = answers.stream()
                .collect(java.util.stream.Collectors.toMap(InterviewAnswer::getQuestionId, answer -> answer));
        List<Map<String, Object>> items = new ArrayList<>();
        for (InterviewQuestion question : questions) {
            InterviewAnswer answer = answersByQuestion.get(question.getId());
            if (answer == null || answer.isSkipped()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionId", question.getId().toString());
            item.put("competencyId", question.getCompetencyId());
            item.put("questionType", question.getQuestionType());
            item.put("question", question.getContent());
            item.put("rubric", question.getRubric());
            item.put("transcript", answer.getTranscriptText());
            items.add(item);
        }
        return jsonString(Map.of(
                "evaluationProfile", session.getEvaluationProfile(),
                "answers", items
        ));
    }

    private void validateGroupedAssessmentEvidence(
            InterviewSession session,
            List<GroupedAssessmentEvidence> groupedEvidence
    ) {
        if (session == null || session.getId() == null) {
            throw new AiProviderException("AI_INVALID_EVALUATION_BATCH",
                    "Thiếu session để chấm phỏng vấn");
        }
        if (groupedEvidence == null || groupedEvidence.isEmpty()) {
            throw new AiProviderException("AI_INVALID_EVALUATION_BATCH",
                    "Không có assessment evidence để chấm");
        }
        Set<String> questionIds = new LinkedHashSet<>();
        for (GroupedAssessmentEvidence item : groupedEvidence) {
            if (item == null || !StringUtils.hasText(item.questionId())
                    || !StringUtils.hasText(item.competencyId())
                    || !StringUtils.hasText(item.question())
                    || item.rubric() == null || item.rubric().isEmpty()
                    || item.evidenceTurns() == null || item.evidenceTurns().isEmpty()
                    || !questionIds.add(item.questionId())) {
                throw new AiProviderException("AI_INVALID_EVALUATION_BATCH",
                        "Assessment evidence thiếu field bắt buộc hoặc bị trùng questionId");
            }
            try {
                UUID.fromString(item.questionId());
            } catch (IllegalArgumentException exception) {
                throw new AiProviderException("AI_INVALID_EVALUATION_BATCH",
                        "Assessment evidence chứa questionId không hợp lệ");
            }
            for (EvidenceTurnDraft turn : item.evidenceTurns()) {
                if (turn == null || !Set.of("CORE_QUESTION", "PROBE", "CLARIFY").contains(turn.turnType())
                        || !StringUtils.hasText(turn.answer())) {
                    throw new AiProviderException("AI_INVALID_EVALUATION_BATCH",
                            "Evidence turn chỉ được là CORE_QUESTION, PROBE hoặc CLARIFY và phải có câu trả lời");
                }
            }
        }
    }

    private String groupedEvaluationInput(
            InterviewSession session,
            List<GroupedAssessmentEvidence> groupedEvidence
    ) {
        List<Map<String, Object>> items = groupedEvidence.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("questionId", item.questionId());
            value.put("competencyId", item.competencyId());
            value.put("questionType", normalizedQuestionType(item.questionType()));
            value.put("question", item.question());
            value.put("rubric", item.rubric());
            value.put("evidenceTurns", item.evidenceTurns().stream().map(turn -> Map.of(
                    "turnType", turn.turnType(),
                    "answer", turn.answer().replaceAll("\\s+", " ").trim()
            )).toList());
            return value;
        }).toList();
        return jsonString(Map.of(
                "evaluationProfile", session.getEvaluationProfile() == null
                        ? Map.of() : session.getEvaluationProfile(),
                "assessmentItems", items
        ));
    }

    private void validateGroupedRatings(
            List<GroupedAssessmentEvidence> groupedEvidence,
            List<QuestionRatingDraft> ratings
    ) {
        Map<String, String> expected = groupedEvidence.stream().collect(
                java.util.stream.Collectors.toMap(
                        GroupedAssessmentEvidence::questionId,
                        GroupedAssessmentEvidence::competencyId,
                        (left, ignored) -> left,
                        LinkedHashMap::new));
        if (ratings == null || ratings.size() != expected.size()) {
            throw new AiProviderException("AI_INVALID_EVALUATION_BATCH",
                    "AI không trả đủ một BARS rating cho mỗi assessment item");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (QuestionRatingDraft rating : ratings) {
            String expectedCompetency = expected.get(rating.questionId());
            if (expectedCompetency == null || !expectedCompetency.equals(rating.competencyId())
                    || !seen.add(rating.questionId())) {
                throw new AiProviderException("AI_INVALID_EVALUATION_BATCH",
                        "AI trả rating sai questionId hoặc competencyId");
            }
        }
    }

    private List<QuestionRatingDraft> questionRatings(JsonNode node) {
        List<QuestionRatingDraft> ratings = new ArrayList<>();
        Set<String> seenQuestionIds = new HashSet<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String questionId = item.path("questionId").asText("").trim();
                String competencyId = item.path("competencyId").asText("").trim();
                JsonNode levelNode = item.get("barsLevel");
                if (questionId.isBlank() || competencyId.isBlank() || levelNode == null
                        || !levelNode.isIntegralNumber() || levelNode.asInt() < 1 || levelNode.asInt() > 5
                        || !seenQuestionIds.add(questionId)) {
                    throw new AiProviderException("AI_INVALID_BARS_LEVEL", "AI trả BARS không hợp lệ");
                }
                ratings.add(new QuestionRatingDraft(
                        questionId,
                        competencyId,
                        levelNode.asInt(),
                        stringList(item.path("evidence")),
                        stringList(item.path("missingEvidence"))
                ));
            });
        }
        return List.copyOf(ratings);
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int requiredScaleScore(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || !value.isIntegralNumber() || value.asInt() < 1 || value.asInt() > 5) {
            throw new AiProviderException("AI_INVALID_EVALUATION_PROFILE",
                    "AI trả " + field + " không phải số nguyên 1-5");
        }
        return value.asInt();
    }

    private String oneOf(String value, List<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.shopaikey.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private RestClient buildClient() {
        return buildClient(properties.getProviderReadTimeoutMs());
    }

    private RestClient buildClient(int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getProviderConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, readTimeoutMs)));
        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(trimTrailingSlash(properties.getTextAi().getBaseUrl()))
                .build();
    }

    public record CvInterviewProfileDraft(
            String summary,
            String experienceLevel,
            List<String> skills,
            List<RoleSuggestionDraft> suggestedRoles,
            List<EvidenceClaimDraft> evidenceClaims
    ) {
    }

    public record RoleSuggestionDraft(String title, String reason) {
    }

    public record EvidenceClaimDraft(String id, String topic, String claim) {
    }

    public enum AnswerAnalysisAction {
        NEXT,
        PROBE,
        CLARIFY
    }

    public record AssessmentTurnCounters(
            int probeCount,
            int clarifyCount,
            int totalAssessmentTurns,
            int remainingCoreQuestions
    ) {
    }

    public record AnswerDecisionDraft(
            AnswerAnalysisAction action,
            String followUp
    ) {
    }

    public record AnswerEvidenceDraft(
            List<String> keyClaims,
            Map<String, Boolean> evidenceCoverage,
            List<String> missingEvidence,
            String updatedItemSummary,
            GlobalEvidenceDeltaDraft globalEvidenceDelta
    ) {
    }

    public record AnswerAnalysisDraft(
            AnswerAnalysisAction action,
            List<String> keyClaims,
            Map<String, Boolean> evidenceCoverage,
            List<String> missingEvidence,
            String followUp,
            String updatedItemSummary,
            GlobalEvidenceDeltaDraft globalEvidenceDelta
    ) {
    }

    public record TranscriptCorrectionDraft(
            String correctedTranscript,
            List<TranscriptCorrectionItemDraft> corrections,
            List<TranscriptEvidenceDraft> evidence,
            String answerSummary,
            boolean followUpNeeded,
            String followUpReason
    ) {
    }

    public record TranscriptCorrectionItemDraft(
            String original,
            String replacement,
            double confidence,
            String reason
    ) {
    }

    public record TranscriptEvidenceDraft(String type, String text) {
    }

    public record GlobalEvidenceDeltaDraft(
            List<String> demonstratedCompetencyIds,
            List<String> weakEvidence,
            List<String> interestingClaims,
            List<String> unverifiedClaims
    ) {
    }

    public record EvidenceTurnDraft(
            String turnType,
            String answer
    ) {
    }

    public record GroupedAssessmentEvidence(
            String questionId,
            String competencyId,
            String questionType,
            String question,
            Map<String, Object> rubric,
            List<EvidenceTurnDraft> evidenceTurns
    ) {
    }

    public record InterviewPackageDraft(
            EvaluationProfileDraft evaluationProfile,
            List<RubricQuestionDraft> questions
    ) {
    }

    public record EvaluationProfileDraft(
            String targetRole,
            String seniority,
            int communicationDemand,
            List<String> scoredCompetencyIds,
            List<CompetencyDraft> competencies
    ) {
    }

    public record CompetencyDraft(
            String id,
            String name,
            String definition,
            int importance,
            int entryNeedScore,
            int distinguishingValueScore,
            String measurementMode,
            String rationale
    ) {
    }

    public record RubricQuestionDraft(
            String questionType,
            String difficulty,
            String competencyId,
            String skillTag,
            String question,
            Integer timeLimitSeconds,
            List<String> expectedEvidence,
            String barsLevel1,
            String barsLevel3,
            String barsLevel5
    ) {
    }

    public record InterviewEvaluationDraft(
            List<QuestionRatingDraft> questionRatings,
            String summary,
            List<String> strengths,
            List<String> improvements,
            List<String> actionPlan
    ) {
    }

    public record QuestionRatingDraft(
            String questionId,
            String competencyId,
            int barsLevel,
            List<String> evidence,
            List<String> missingEvidence
    ) {
    }

}
