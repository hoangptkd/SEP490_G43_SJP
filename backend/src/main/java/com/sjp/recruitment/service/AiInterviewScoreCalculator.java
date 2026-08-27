package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewAnswerCapture;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.enums.VoiceEvidenceStatus;
import com.sjp.recruitment.service.ai.ShopAiKeyClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiInterviewScoreCalculator {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{M}]+");
    private static final Pattern VIETNAMESE_MARK = Pattern.compile(
            "[ăâđêôơưĂÂĐÊÔƠƯ]|[\\p{L}&&[^\\p{ASCII}]]");
    private static final Pattern ENGLISH_VOWEL_GROUP = Pattern.compile("[aeiouy]+", Pattern.CASE_INSENSITIVE);

    private final AiInterviewProperties properties;

    public ScoreResult calculate(Map<String, Object> evaluationProfile,
                                 List<InterviewQuestion> questions,
                                 List<InterviewAnswer> answers,
                                 List<ShopAiKeyClient.QuestionRatingDraft> ratings) {
        return calculate(evaluationProfile, questions, answers, ratings, null);
    }

    public ScoreResult calculate(Map<String, Object> evaluationProfile,
                                 List<InterviewQuestion> questions,
                                 List<InterviewAnswer> answers,
                                 List<ShopAiKeyClient.QuestionRatingDraft> ratings,
                                 List<InterviewAnswerCapture> captures) {
        Map<UUID, InterviewAnswer> answersByQuestion = answers.stream()
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, answer -> answer));
        Map<UUID, ShopAiKeyClient.QuestionRatingDraft> ratingsByQuestion = ratings.stream()
                .collect(Collectors.toMap(rating -> parseQuestionId(rating.questionId()), rating -> rating));

        Set<String> scoredCompetencyIds = scoredCompetencyIds(evaluationProfile);
        Map<UUID, QuestionScoreResult> questionResults = new LinkedHashMap<>();
        Map<String, List<BigDecimal>> competencyQuestionScores = new LinkedHashMap<>();
        for (InterviewQuestion question : questions) {
            InterviewAnswer answer = answersByQuestion.get(question.getId());
            if (answer == null || answer.getAnsweredAt() == null) continue;
            if (!scoredCompetencyIds.contains(question.getCompetencyId())) {
                throw invalidEvaluation("Câu hỏi không thuộc Scored Competency Set");
            }
            QuestionScoreResult questionResult;
            if (answer.isSkipped()) {
                questionResult = new QuestionScoreResult(
                        "NOT_ANSWERED", null, BigDecimal.ZERO.setScale(2), "SKIPPED");
            } else {
                ShopAiKeyClient.QuestionRatingDraft rating = ratingsByQuestion.get(question.getId());
                if (rating == null || !Objects.equals(question.getCompetencyId(), rating.competencyId())) {
                    throw invalidEvaluation("AI thiếu rating hoặc trả sai năng lực của câu hỏi");
                }
                BigDecimal score = BigDecimal.valueOf((rating.barsLevel() - 1L) * 25L).setScale(2);
                questionResult = new QuestionScoreResult("RATED", rating.barsLevel(), score, null);
            }
            questionResults.put(question.getId(), questionResult);
            competencyQuestionScores.computeIfAbsent(question.getCompetencyId(), ignored -> new ArrayList<>())
                    .add(questionResult.questionScore());
        }
        BigDecimal contentScore = contentScore(evaluationProfile, scoredCompetencyIds, competencyQuestionScores);
        List<InterviewAnswer> ratedAnswers = answers.stream().filter(answer -> !answer.isSkipped()).toList();
        VoiceResult voice = captures == null
                ? voiceScore(ratedAnswers)
                : voiceScore(ratedAnswers, captures);
        BigDecimal configuredVoiceWeight = decimal(evaluationProfile.get("voiceWeight"));
        if (configuredVoiceWeight.compareTo(BigDecimal.valueOf(0.10)) < 0
                || configuredVoiceWeight.compareTo(BigDecimal.valueOf(0.30)) > 0) {
            throw invalidEvaluation("Evaluation Profile có trọng số giọng nói không hợp lệ");
        }
        int replayCount = questions.stream().mapToInt(InterviewQuestion::getReplayCount).sum();
        BigDecimal replayPenalty = BigDecimal.valueOf(Math.min(10, replayCount * 2L)).setScale(2);
        BigDecimal adjustedVoiceScore = voice.score() == null
                ? null
                : voice.score().subtract(replayPenalty).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal appliedVoiceWeight = adjustedVoiceScore == null
                ? BigDecimal.ZERO.setScale(2)
                : configuredVoiceWeight;
        BigDecimal overall = adjustedVoiceScore == null
                ? contentScore
                : contentScore.multiply(BigDecimal.ONE.subtract(appliedVoiceWeight))
                        .add(adjustedVoiceScore.multiply(appliedVoiceWeight))
                        .setScale(2, RoundingMode.HALF_UP);
        int manualFallbackQuestionCount = Math.max(0, ratedAnswers.size() - voice.evidenceQuestionCount());
        Map<String, Object> voiceMetrics = new LinkedHashMap<>(voice.metrics());
        voiceMetrics.put("configuredVoiceWeight", configuredVoiceWeight);
        voiceMetrics.put("voiceWeightApplied", appliedVoiceWeight);
        voiceMetrics.put("replayCount", replayCount);
        voiceMetrics.put("replayPenalty", replayPenalty);
        voiceMetrics.put("voiceEvidenceQuestionCount", voice.evidenceQuestionCount());
        voiceMetrics.put("manualFallbackQuestionCount", manualFallbackQuestionCount);
        return new ScoreResult(contentScore, adjustedVoiceScore, voice.score(), appliedVoiceWeight,
                replayCount, replayPenalty, voice.evidenceQuestionCount(), manualFallbackQuestionCount,
                overall, Map.copyOf(questionResults), Map.copyOf(voiceMetrics));
    }

    private BigDecimal contentScore(Map<String, Object> evaluationProfile,
                                    Set<String> scoredCompetencyIds,
                                    Map<String, List<BigDecimal>> competencyQuestionScores) {
        Object raw = evaluationProfile.get("competencies");
        if (!(raw instanceof Collection<?> competencies)) {
            throw invalidEvaluation("Evaluation Profile thiếu danh sách năng lực");
        }
        Map<String, BigDecimal> configuredWeights = new LinkedHashMap<>();
        for (Object value : competencies) {
            if (value instanceof Map<?, ?> item) {
                String id = String.valueOf(item.get("id"));
                if (scoredCompetencyIds.contains(id)) {
                    if (!competencyQuestionScores.containsKey(id)) {
                        throw invalidEvaluation("Scored competency không có primary question để chấm");
                    }
                    configuredWeights.put(id, decimal(item.get("scoredWeight")));
                }
            }
        }
        if (!configuredWeights.keySet().equals(scoredCompetencyIds)) {
            throw invalidEvaluation("Scored Competency Set không khớp Evaluation Profile");
        }
        BigDecimal activeWeight = configuredWeights.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (activeWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalidEvaluation("Không có năng lực nào đủ dữ liệu để chấm");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : configuredWeights.entrySet()) {
            List<BigDecimal> scores = competencyQuestionScores.get(entry.getKey());
            BigDecimal average = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 8, RoundingMode.HALF_UP);
            BigDecimal effectiveScoredWeight = entry.getValue().divide(activeWeight, 8, RoundingMode.HALF_UP);
            total = total.add(average.multiply(effectiveScoredWeight));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private Set<String> scoredCompetencyIds(Map<String, Object> evaluationProfile) {
        Object raw = evaluationProfile.get("scoredCompetencyIds");
        if (!(raw instanceof Collection<?> values)) {
            throw invalidEvaluation("Evaluation Profile thiếu Scored Competency Set");
        }
        Set<String> ids = values.stream()
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.size() < 3 || ids.size() > 4) {
            throw invalidEvaluation("Scored Competency Set phải có từ 3 đến 4 năng lực");
        }
        return ids;
    }

    private VoiceResult voiceScore(List<InterviewAnswer> answers) {
        List<VoiceEvidence> evidence = new ArrayList<>();
        for (InterviewAnswer answer : answers) {
            Map<String, Object> analysis = answer.getSpeechAnalysisJson();
            if (analysis == null || !"AUDIO_VAD_PLUS_GLADIA".equals(analysis.get("dataQuality"))) {
                continue;
            }
            Object rawMetrics = analysis.get("metrics");
            if (rawMetrics instanceof Map<?, ?> metrics) {
                evidence.add(new VoiceEvidence(
                        answer.getQuestionId(),
                        hasText(answer.getRawTranscript())
                                ? answer.getRawTranscript()
                                : answer.getTranscriptText(),
                        metrics
                ));
            }
        }
        return scoreVoiceEvidence(evidence);
    }

    private VoiceResult voiceScore(
            List<InterviewAnswer> answers,
            List<InterviewAnswerCapture> captures
    ) {
        Set<UUID> allowedAnswerIds = answers.stream()
                .map(InterviewAnswer::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, InterviewAnswerCapture> latestByTurn = new LinkedHashMap<>();
        for (InterviewAnswerCapture capture : captures) {
            if (capture == null || capture.getAnswer() == null
                    || !allowedAnswerIds.contains(capture.getAnswer().getId())
                    || !"completed".equals(capture.getStatus())
                    || capture.getVoiceEvidenceStatus() != VoiceEvidenceStatus.COMPLETED
                    || !"AUDIO_VAD_PLUS_GLADIA".equals(capture.getDataQuality())) {
                continue;
            }
            String key = capture.getConversationTurn() == null
                    ? "answer:" + capture.getAnswer().getId()
                    : "turn:" + capture.getConversationTurn().getId();
            latestByTurn.merge(key, capture, this::newerCapture);
        }
        List<VoiceEvidence> evidence = latestByTurn.values().stream()
                .map(capture -> new VoiceEvidence(
                        capture.getAnswer().getQuestionId(),
                        hasText(capture.getGladiaTranscript())
                                ? capture.getGladiaTranscript()
                                : capture.getRawTranscript(),
                        capture.getVadMetricsJson()
                ))
                .toList();
        return scoreVoiceEvidence(evidence);
    }

    private InterviewAnswerCapture newerCapture(
            InterviewAnswerCapture first,
            InterviewAnswerCapture second
    ) {
        if (second.getCaptureVersion() != first.getCaptureVersion()) {
            return second.getCaptureVersion() > first.getCaptureVersion() ? second : first;
        }
        if (first.getUpdatedAt() == null) return second;
        if (second.getUpdatedAt() == null) return first;
        return second.getUpdatedAt().isAfter(first.getUpdatedAt()) ? second : first;
    }

    private VoiceResult scoreVoiceEvidence(List<VoiceEvidence> evidence) {
        long syllables = 0;
        double activeDuration = 0;
        double speakingDuration = 0;
        double pauseDuration = 0;
        int pauseCount = 0;
        double longestPause = 0;
        Set<UUID> evidenceQuestionIds = new LinkedHashSet<>();
        for (VoiceEvidence sample : evidence) {
            Map<?, ?> metrics = sample.metrics();
            if (metrics == null || !"completed".equals(String.valueOf(metrics.get("status")))) {
                continue;
            }
            double answerSpeaking = number(metrics.get("speakingDurationSeconds"));
            List<?> segments = metrics.get("speechSegments") instanceof List<?> list ? list : List.of();
            if (answerSpeaking <= 0 || segments.isEmpty()) {
                continue;
            }
            double firstStart = segmentValue(segments.get(0), "startMs");
            double lastEnd = segmentValue(segments.get(segments.size() - 1), "endMs");
            double answerActive = (lastEnd - firstStart) / 1000.0;
            if (answerActive <= 0) continue;
            evidenceQuestionIds.add(sample.questionId());
            syllables += syllableCount(sample.rawTranscript());
            activeDuration += answerActive;
            speakingDuration += answerSpeaking;
            pauseDuration += number(metrics.get("totalInternalPauseDurationSeconds"));
            pauseCount += (int) number(metrics.get("internalPauseCount"));
            longestPause = Math.max(longestPause, number(metrics.get("longestInternalPauseSeconds")));
        }
        int evidenceQuestionCount = evidenceQuestionIds.size();
        if (evidenceQuestionCount == 0) {
            return new VoiceResult(null, Map.of(
                    "status", "INSUFFICIENT_VOICE_DATA",
                    "message", "Không đủ dữ liệu để chấm tốc độ nói và khoảng nghỉ"
            ), 0);
        }
        double activeMinutes = activeDuration / 60.0;
        double speakingMinutes = speakingDuration / 60.0;
        double speechRate = syllables / activeMinutes;
        double articulationRate = syllables / speakingMinutes;
        double pauseRatio = pauseDuration / activeDuration;
        double pauseFrequency = pauseCount / activeMinutes;
        double speedScore = 0.50 * band(speechRate, properties.getSpeechRateL0(), properties.getSpeechRateL1(),
                properties.getSpeechRateU1(), properties.getSpeechRateU0())
                + 0.50 * band(articulationRate, properties.getArticulationRateL0(),
                properties.getArticulationRateL1(), properties.getArticulationRateU1(),
                properties.getArticulationRateU0());
        double pauseScore = 0.50 * band(pauseRatio, properties.getPauseRatioL0(), properties.getPauseRatioL1(),
                properties.getPauseRatioU1(), properties.getPauseRatioU0())
                + 0.30 * band(pauseFrequency, properties.getPauseFrequencyL0(),
                properties.getPauseFrequencyL1(), properties.getPauseFrequencyU1(),
                properties.getPauseFrequencyU0())
                + 0.20 * band(longestPause, properties.getLongestPauseL0(), properties.getLongestPauseL1(),
                properties.getLongestPauseU1(), properties.getLongestPauseU0());
        BigDecimal score = BigDecimal.valueOf(0.50 * speedScore + 0.50 * pauseScore)
                .setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("syllableCount", syllables);
        metrics.put("activeDurationSeconds", activeDuration);
        metrics.put("speakingDurationSeconds", speakingDuration);
        metrics.put("pauseDurationSeconds", pauseDuration);
        metrics.put("pauseCount", pauseCount);
        metrics.put("longestPauseSeconds", longestPause);
        metrics.put("speechRate", speechRate);
        metrics.put("articulationRate", articulationRate);
        metrics.put("pauseRatio", pauseRatio);
        metrics.put("pauseFrequency", pauseFrequency);
        metrics.put("calibrationVersion", properties.getSpeechCalibrationVersion());
        metrics.put("status", "COMPLETED");
        return new VoiceResult(score, Map.copyOf(metrics), evidenceQuestionCount);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    double band(double value, double l0, double l1, double u1, double u0) {
        if (!(l0 < l1 && l1 <= u1 && u1 < u0)) {
            throw new IllegalStateException("Invalid speech reference band");
        }
        if (value <= l0 || value >= u0) return 0;
        if (value < l1) return 100 * (value - l0) / (l1 - l0);
        if (value <= u1) return 100;
        return 100 * (u0 - value) / (u0 - u1);
    }

    private long syllableCount(String transcript) {
        if (transcript == null || transcript.isBlank()) return 0;
        List<String> tokens = TOKEN.matcher(transcript).results().map(match -> match.group()).toList();
        boolean vietnamese = VIETNAMESE_MARK.matcher(transcript).find();
        if (vietnamese) return tokens.size();
        return tokens.stream().mapToLong(this::englishSyllables).sum();
    }

    private long englishSyllables(String token) {
        String normalized = Normalizer.normalize(token.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        long groups = ENGLISH_VOWEL_GROUP.matcher(normalized).results().count();
        if (normalized.endsWith("e") && groups > 1 && !normalized.endsWith("le")) groups--;
        return Math.max(1, groups);
    }

    private double segmentValue(Object segment, String key) {
        if (!(segment instanceof Map<?, ?> map)) return 0;
        return number(map.get(key));
    }

    private double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw invalidEvaluation("Giá trị trọng số không hợp lệ");
        }
    }

    private UUID parseQuestionId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw invalidEvaluation("AI trả questionId không hợp lệ");
        }
    }

    private ApiException invalidEvaluation(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_INTERVIEW_EVALUATION", message);
    }

    public record ScoreResult(
            BigDecimal contentScore,
            BigDecimal voiceDeliveryScore,
            BigDecimal rawVoiceDeliveryScore,
            BigDecimal voiceWeight,
            int replayCount,
            BigDecimal replayPenalty,
            int voiceEvidenceQuestionCount,
            int manualFallbackQuestionCount,
            BigDecimal overallScore,
            Map<UUID, QuestionScoreResult> questionResults,
            Map<String, Object> voiceMetrics
    ) {
    }

    public record QuestionScoreResult(
            String evaluationStatus,
            Integer barsLevel,
            BigDecimal questionScore,
            String scoreReason
    ) {
    }

    private record VoiceResult(BigDecimal score, Map<String, Object> metrics, int evidenceQuestionCount) {
    }

    private record VoiceEvidence(UUID questionId, String rawTranscript, Map<?, ?> metrics) {
    }
}
