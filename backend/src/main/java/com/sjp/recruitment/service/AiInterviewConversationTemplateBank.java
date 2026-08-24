package com.sjp.recruitment.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiInterviewConversationTemplateBank {

    private static final String CONFIRMATION_PROMPT = "Bạn đã trả lời xong chưa?";

    private static final List<String> ACKNOWLEDGEMENTS = List.of(
            "Được rồi.",
            "Mình hiểu rồi.",
            "Cảm ơn bạn đã chia sẻ."
    );

    private static final List<String> TRANSITIONS = List.of(
            "Bây giờ mình muốn chuyển sang một khía cạnh khác%s.",
            "Tiếp theo, mình muốn trao đổi thêm về một chủ đề khác%s.",
            "Mình sẽ chuyển sang nội dung tiếp theo%s."
    );

    public String opening(String targetRole) {
        String role = normalize(targetRole);
        return role.isBlank()
                ? "Chào bạn, cảm ơn bạn đã tham gia buổi phỏng vấn hôm nay. Mình sẽ trao đổi với bạn về một số kinh nghiệm và kỹ năng liên quan đến vị trí bạn đang hướng tới."
                : "Chào bạn, cảm ơn bạn đã tham gia buổi phỏng vấn hôm nay. Mình sẽ trao đổi với bạn về một số kinh nghiệm và kỹ năng liên quan đến vị trí " + role + ".";
    }

    public String acknowledgement(long seed) {
        return pick(ACKNOWLEDGEMENTS, seed);
    }

    public String transition(String topic, long seed) {
        String normalizedTopic = normalize(topic);
        String suffix = normalizedTopic.isBlank() ? "" : " liên quan đến " + normalizedTopic;
        return pick(TRANSITIONS, seed).formatted(suffix);
    }

    public String closing() {
        return "Cảm ơn bạn. Mình đã có đủ thông tin cho buổi phỏng vấn hôm nay.";
    }

    public String confirmationPrompt() {
        return CONFIRMATION_PROMPT;
    }

    public List<String> initialPriorityPhrases(List<String> coreQuestions) {
        List<String> phrases = new ArrayList<>();
        phrases.add(confirmationPrompt());
        phrases.add(acknowledgement(2));
        addQuestionTransition(phrases, coreQuestions, 2);
        addQuestionTransition(phrases, coreQuestions, 3);
        return List.copyOf(phrases);
    }

    private void addQuestionTransition(
            List<String> phrases,
            List<String> coreQuestions,
            int orderIndex
    ) {
        if (coreQuestions == null || coreQuestions.size() < orderIndex) return;
        String question = normalize(coreQuestions.get(orderIndex - 1));
        if (question.isBlank()) return;
        phrases.add(transition(null, orderIndex));
        phrases.add(question);
    }

    private String pick(List<String> templates, long seed) {
        return templates.get(Math.floorMod(seed, templates.size()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
