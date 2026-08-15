package com.sjp.recruitment.service;

import com.sjp.recruitment.config.AiInterviewProperties;
import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.response.AiInterviewLiveTranscriptionResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.InterviewAnswer;
import com.sjp.recruitment.model.entity.InterviewConversationTurn;
import com.sjp.recruitment.model.entity.InterviewQuestion;
import com.sjp.recruitment.model.entity.InterviewSession;
import com.sjp.recruitment.model.enums.InterviewTurnAnswerStatus;
import com.sjp.recruitment.repository.InterviewAnswerRepository;
import com.sjp.recruitment.repository.InterviewConversationTurnRepository;
import com.sjp.recruitment.repository.InterviewQuestionRepository;
import com.sjp.recruitment.repository.InterviewSessionRepository;
import com.sjp.recruitment.service.ai.AiInterviewRateLimiter;
import com.sjp.recruitment.service.ai.GladiaLiveSessionClient;
import com.sjp.recruitment.service.ai.GladiaLiveSessionRegistry;
import com.sjp.recruitment.service.ai.GladiaTranscriptionContext;
import com.sjp.recruitment.service.ai.TechnicalVocabularyBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class GladiaLiveSessionService {
    private static final Set<Integer> SUPPORTED_SAMPLE_RATES = Set.of(
            8_000, 16_000, 32_000, 44_100, 48_000);

    private final AiInterviewProperties properties;
    private final CandidateService candidateService;
    private final AiInterviewRateLimiter rateLimiter;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final InterviewConversationTurnRepository turnRepository;
    private final TechnicalVocabularyBuilder vocabularyBuilder;
    private final GladiaLiveSessionClient client;
    private final GladiaLiveSessionRegistry registry;

    public GladiaLiveSessionService(
            AiInterviewProperties properties,
            CandidateService candidateService,
            AiInterviewRateLimiter rateLimiter,
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            InterviewAnswerRepository answerRepository,
            InterviewConversationTurnRepository turnRepository,
            TechnicalVocabularyBuilder vocabularyBuilder,
            GladiaLiveSessionClient client,
            GladiaLiveSessionRegistry registry) {
        this.properties = properties;
        this.candidateService = candidateService;
        this.rateLimiter = rateLimiter;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.turnRepository = turnRepository;
        this.vocabularyBuilder = vocabularyBuilder;
        this.client = client;
        this.registry = registry;
    }

    @Transactional(readOnly = true)
    public AiInterviewLiveTranscriptionResponse create(String sessionIdValue, int sampleRate) {
        if (!"gladia_live".equalsIgnoreCase(properties.getAnswerTranscriptionProvider())) {
            throw new ApiException(HttpStatus.CONFLICT, "GLADIA_LIVE_NOT_SELECTED",
                    "Gladia Live chưa được chọn làm nguồn transcript câu trả lời");
        }
        if (!SUPPORTED_SAMPLE_RATES.contains(sampleRate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_SAMPLE_RATE_UNSUPPORTED",
                    "Sample rate của microphone chưa được hỗ trợ");
        }
        UUID sessionId = parseUuid(sessionIdValue);
        CandidateProfile candidate = candidateService.getCurrentCandidateProfile();
        rateLimiter.check(candidate.getId(), "gladia-live-init");
        InterviewSession session = sessionRepository
                .findByIdAndCandidateIdAndDeletedAtIsNull(sessionId, candidate.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                        "Không tìm thấy phiên phỏng vấn"));
        if (session.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_COMPLETED",
                    "Phiên phỏng vấn đã hoàn thành");
        }

        Target target = currentTarget(session);
        GladiaTranscriptionContext context = vocabularyBuilder.build(session, target.question());
        GladiaLiveSessionClient.LiveSession live = client.start(sampleRate, context);
        UUID token = registry.register(candidate.getId(), sessionId, target.type(), target.id(), live.id());
        return new AiInterviewLiveTranscriptionResponse(
                "gladia_live",
                token.toString(),
                live.id(),
                live.websocketUrl(),
                target.type(),
                target.id().toString()
        );
    }

    private Target currentTarget(InterviewSession session) {
        if (session.getDialogueState() != null) {
            InterviewConversationTurn turn = turnRepository.findCurrentBySessionId(session.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INTERVIEW_TURN_NOT_FOUND",
                            "Không tìm thấy lượt hội thoại hiện tại"));
            if (turn.getAssessmentItem() == null
                    || (turn.getAnswerStatus() != InterviewTurnAnswerStatus.WAITING
                    && turn.getAnswerStatus() != InterviewTurnAnswerStatus.REVIEWING)) {
                throw new ApiException(HttpStatus.CONFLICT, "INTERVIEW_TURN_NOT_WAITING",
                        "Lượt hội thoại không ở trạng thái nhận câu trả lời");
            }
            return new Target("turn", turn.getId(), turn.getAssessmentItem());
        }

        InterviewQuestion question = questionRepository.findBySessionIdOrderByOrderIndexAsc(session.getId()).stream()
                .filter(item -> {
                    Optional<InterviewAnswer> answer = answerRepository
                            .findBySessionIdAndQuestionId(session.getId(), item.getId());
                    return answer.map(value -> value.getAnsweredAt() == null).orElse(true);
                })
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NO_OPEN_QUESTION",
                        "Không có câu hỏi đang mở"));
        return new Target("question", question.getId(), question);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SESSION_ID_INVALID",
                    "Mã phiên phỏng vấn không hợp lệ");
        }
    }

    private record Target(String type, UUID id, InterviewQuestion question) {
    }
}
