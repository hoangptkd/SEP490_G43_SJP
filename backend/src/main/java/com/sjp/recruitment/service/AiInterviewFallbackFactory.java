package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class AiInterviewFallbackFactory {

    public static final String FALLBACK_PROMPT_VERSION = "deterministic-fallback-v1";
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 180;

    public ShopAiKeyClient.InterviewPackageDraft initialPackage(InterviewSession session) {
        String role = targetRole(session);
        String seniority = seniority(session);
        List<ShopAiKeyClient.CompetencyDraft> competencies = List.of(
                competency(
                        "technical-foundation",
                        "Nền tảng kỹ thuật",
                        "Khả năng vận dụng kiến thức và công nghệ phù hợp với vị trí.",
                        "Đánh giá qua ví dụ kỹ thuật và cách triển khai thực tế."),
                competency(
                        "problem-solving",
                        "Giải quyết vấn đề",
                        "Khả năng phân tích nguyên nhân, lựa chọn giải pháp và kiểm chứng kết quả.",
                        "Đánh giá qua quy trình xử lý vấn đề và kết quả quan sát được."),
                competency(
                        "communication",
                        "Giao tiếp và phối hợp",
                        "Khả năng truyền đạt rõ ràng và phối hợp hiệu quả với người khác.",
                        "Đánh giá qua hành động phối hợp và kết quả làm việc nhóm.")
        );
        List<String> scoredIds = competencies.stream()
                .map(ShopAiKeyClient.CompetencyDraft::id)
                .toList();
        ShopAiKeyClient.EvaluationProfileDraft profile = new ShopAiKeyClient.EvaluationProfileDraft(
                role,
                seniority,
                3,
                scoredIds,
                competencies
        );
        List<ShopAiKeyClient.RubricQuestionDraft> questions = List.of(
                question(
                        "cv_experience",
                        "technical-foundation",
                        "Kinh nghiệm kỹ thuật",
                        "Bạn hãy mô tả một dự án liên quan đến " + role
                                + ", nêu rõ vai trò, công nghệ và phần việc bạn trực tiếp thực hiện?"),
                question(
                        "technical",
                        "problem-solving",
                        "Giải quyết vấn đề",
                        "Khi gặp một lỗi hoặc vấn đề hiệu năng trong hệ thống, bạn sẽ phân tích nguyên nhân và kiểm chứng giải pháp như thế nào?"),
                question(
                        "behavioral",
                        "communication",
                        "Làm việc nhóm",
                        "Hãy kể về một tình huống bạn phối hợp với đồng đội để hoàn thành một mục tiêu khó và kết quả đạt được?"));
        return new ShopAiKeyClient.InterviewPackageDraft(profile, questions);
    }

    public List<ShopAiKeyClient.RubricQuestionDraft> adaptiveQuestions(
            InterviewSession session,
            List<InterviewQuestion> existingQuestions,
            int requestedCount
    ) {
        if (requestedCount <= 0) return List.of();
        Map<String, String> competencyNames = competencyNames(session.getEvaluationProfile());
        List<String> scoredIds = scoredCompetencyIds(session.getEvaluationProfile());
        if (scoredIds.isEmpty()) {
            scoredIds = existingQuestions.stream()
                    .map(InterviewQuestion::getCompetencyId)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }
        if (scoredIds.isEmpty()) {
            scoredIds = List.of("problem-solving");
        }

        Set<String> covered = existingQuestions.stream()
                .map(InterviewQuestion::getCompetencyId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> selected = new ArrayList<>();
        scoredIds.stream().filter(id -> !covered.contains(id)).forEach(selected::add);
        int cursor = 0;
        while (selected.size() < requestedCount) {
            selected.add(scoredIds.get(cursor++ % scoredIds.size()));
        }
        if (selected.size() > requestedCount) {
            selected = new ArrayList<>(selected.subList(0, requestedCount));
        }

        List<ShopAiKeyClient.RubricQuestionDraft> drafts = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            String competencyId = selected.get(index);
            String competencyName = competencyNames.getOrDefault(competencyId, "năng lực " + competencyId);
            String content = index % 2 == 0
                    ? "Hãy mô tả một tình huống cụ thể thể hiện " + competencyName
                            + ", hành động bạn đã thực hiện và kết quả đạt được?"
                    : "Nếu gặp một tình huống mới cần vận dụng " + competencyName
                            + ", bạn sẽ lựa chọn cách tiếp cận và kiểm chứng kết quả như thế nào?";
            drafts.add(question(
                    index % 2 == 0 ? "behavioral" : "situational",
                    competencyId,
                    competencyName,
                    content));
        }
        return List.copyOf(drafts);
    }

    public ShopAiKeyClient.AnswerAnalysisDraft answerAnalysis(
            InterviewQuestion question,
            Map<String, Object> previousSummary
    ) {
        Map<String, Boolean> coverage = new LinkedHashMap<>();
        evidenceDimensions(question == null ? null : question.getQuestionType())
                .forEach(dimension -> coverage.put(dimension, false));
        String summary = previousSummary == null ? "" : normalize(previousSummary.get("summary"));
        if (summary.isBlank()) {
            summary = "Câu trả lời đã được ghi nhận để đánh giá khi kết thúc buổi phỏng vấn.";
        }
        return new ShopAiKeyClient.AnswerAnalysisDraft(
                ShopAiKeyClient.AnswerAnalysisAction.NEXT,
                List.of(),
                Map.copyOf(coverage),
                List.of("Chưa có evidence đã được AI xác minh ở lượt này."),
                null,
                summary,
                new ShopAiKeyClient.GlobalEvidenceDeltaDraft(
                        List.of(), List.of(), List.of(), List.of())
        );
    }

    public ShopAiKeyClient.InterviewEvaluationDraft finalEvaluation(
            List<ShopAiKeyClient.GroupedAssessmentEvidence> groupedEvidence,
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers
    ) {
        List<ShopAiKeyClient.GroupedAssessmentEvidence> evidence = groupedEvidence == null
                ? List.of()
                : groupedEvidence;
        if (evidence.isEmpty()) {
            evidence = legacyEvidence(questions, answers);
        }
        List<ShopAiKeyClient.QuestionRatingDraft> ratings = new ArrayList<>();
        for (ShopAiKeyClient.GroupedAssessmentEvidence item : evidence) {
            List<String> turnAnswers = item.evidenceTurns() == null
                    ? List.of()
                    : item.evidenceTurns().stream()
                    .map(ShopAiKeyClient.EvidenceTurnDraft::answer)
                    .map(this::normalize)
                    .filter(value -> !value.isBlank())
                    .toList();
            int wordCount = turnAnswers.stream().mapToInt(this::wordCount).sum();
            int barsLevel = provisionalBarsLevel(wordCount);
            List<String> observed = wordCount == 0
                    ? List.of()
                    : List.of("Đã ghi nhận " + turnAnswers.size() + " lượt trả lời với khoảng "
                            + wordCount + " từ để luyện tập.");
            ratings.add(new ShopAiKeyClient.QuestionRatingDraft(
                    item.questionId(),
                    item.competencyId(),
                    barsLevel,
                    observed,
                    List.of("Cần bổ sung ví dụ, hành động và kết quả có thể kiểm chứng rõ hơn.")
            ));
        }
        return new ShopAiKeyClient.InterviewEvaluationDraft(
                List.copyOf(ratings),
                "Bạn đã hoàn thành buổi luyện phỏng vấn. Kết quả này là đánh giá dự phòng dựa trên mức độ hoàn thiện của câu trả lời.",
                List.of("Bạn đã hoàn thành các nội dung phỏng vấn và cung cấp câu trả lời để luyện tập."),
                List.of("Một số câu trả lời cần làm rõ hành động cá nhân, quyết định và kết quả đạt được."),
                List.of(
                        "Chọn hai câu trả lời để luyện lại theo cấu trúc bối cảnh, hành động và kết quả.",
                        "Bổ sung ít nhất một ví dụ hoặc số liệu có thể kiểm chứng cho mỗi câu trả lời."
                )
        );
    }

    private List<ShopAiKeyClient.GroupedAssessmentEvidence> legacyEvidence(
            List<InterviewQuestion> questions,
            List<InterviewAnswer> answers
    ) {
        Map<java.util.UUID, InterviewAnswer> answersByQuestion = new LinkedHashMap<>();
        if (answers != null) {
            answers.stream()
                    .filter(answer -> answer != null && answer.getQuestionId() != null && !answer.isSkipped())
                    .forEach(answer -> answersByQuestion.putIfAbsent(answer.getQuestionId(), answer));
        }
        List<ShopAiKeyClient.GroupedAssessmentEvidence> values = new ArrayList<>();
        if (questions == null) return values;
        for (InterviewQuestion question : questions) {
            InterviewAnswer answer = answersByQuestion.get(question.getId());
            if (answer == null) continue;
            String transcript = normalize(answer.getFinalTranscript());
            if (transcript.isBlank()) transcript = normalize(answer.getTranscriptText());
            values.add(new ShopAiKeyClient.GroupedAssessmentEvidence(
                    question.getId().toString(),
                    question.getCompetencyId(),
                    question.getQuestionType(),
                    question.getContent(),
                    question.getRubric(),
                    List.of(new ShopAiKeyClient.EvidenceTurnDraft("CORE_QUESTION", transcript))
            ));
        }
        return List.copyOf(values);
    }

    private ShopAiKeyClient.CompetencyDraft competency(
            String id,
            String name,
            String definition,
            String rationale
    ) {
        return new ShopAiKeyClient.CompetencyDraft(
                id,
                name,
                definition,
                4,
                4,
                4,
                "BARS",
                rationale
        );
    }

    private ShopAiKeyClient.RubricQuestionDraft question(
            String questionType,
            String competencyId,
            String skillTag,
            String content
    ) {
        return new ShopAiKeyClient.RubricQuestionDraft(
                questionType,
                "medium",
                competencyId,
                skillTag,
                content,
                DEFAULT_TIME_LIMIT_SECONDS,
                List.of("Bối cảnh hoặc vấn đề", "Hành động hoặc quyết định", "Kết quả hoặc cách kiểm chứng"),
                "Câu trả lời rất ngắn, mơ hồ hoặc không nêu được hành động cụ thể.",
                "Câu trả lời có cách tiếp cận phù hợp và nêu được một số chi tiết liên quan.",
                "Câu trả lời rõ vai trò, lập luận, hành động, trade-off và kết quả có thể kiểm chứng."
        );
    }

    private List<String> scoredCompetencyIds(Map<String, Object> evaluationProfile) {
        Object rawIds = evaluationProfile == null ? null : evaluationProfile.get("scoredCompetencyIds");
        if (!(rawIds instanceof Collection<?> ids)) return List.of();
        return ids.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, String> competencyNames(Map<String, Object> evaluationProfile) {
        Map<String, String> names = new LinkedHashMap<>();
        Object rawCompetencies = evaluationProfile == null ? null : evaluationProfile.get("competencies");
        if (!(rawCompetencies instanceof Collection<?> competencies)) return names;
        for (Object value : competencies) {
            if (!(value instanceof Map<?, ?> competency)) continue;
            String id = normalize(competency.get("id"));
            String name = normalize(competency.get("name"));
            if (!id.isBlank() && !name.isBlank()) names.put(id, name);
        }
        return names;
    }

    private List<String> evidenceDimensions(String questionType) {
        return switch (normalize(questionType).toLowerCase(Locale.ROOT)) {
            case "behavioral" -> List.of("situation", "task", "action", "result");
            case "technical" -> List.of(
                    "accuracy", "reasoning", "tradeOffs", "implementationDetail", "realWorldApplication");
            case "situational" -> List.of(
                    "problemIdentification", "decision", "reasoning", "risk", "alternative");
            case "cv_experience" -> List.of(
                    "personalContribution", "technicalDepth", "consistency", "result");
            default -> List.of("relevance", "specificity", "evidence", "result");
        };
    }

    private String targetRole(InterviewSession session) {
        if (session != null && session.getJob() != null) {
            String title = normalize(session.getJob().getTitle());
            if (!title.isBlank()) return title;
        }
        if (session != null && session.getPracticeContext() != null) {
            String role = normalize(session.getPracticeContext().get("targetRole"));
            if (!role.isBlank()) return role;
        }
        if (session != null) {
            String title = normalize(session.getTitle()).replaceFirst("(?i)^practice:\\s*", "");
            if (!title.isBlank()) return title;
        }
        return "vị trí mục tiêu";
    }

    private String seniority(InterviewSession session) {
        if (session != null && session.getPracticeContext() != null) {
            String value = normalize(session.getPracticeContext().get("seniority")).toLowerCase(Locale.ROOT);
            if (Set.of("intern", "fresher", "junior", "middle", "senior").contains(value)) {
                return value;
            }
        }
        return "fresher";
    }

    private int provisionalBarsLevel(int wordCount) {
        if (wordCount < 8) return 1;
        if (wordCount < 25) return 2;
        if (wordCount < 60) return 3;
        return 4;
    }

    private int wordCount(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? 0 : normalized.split("\\s+").length;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("\\s+", " ").trim();
    }
}
