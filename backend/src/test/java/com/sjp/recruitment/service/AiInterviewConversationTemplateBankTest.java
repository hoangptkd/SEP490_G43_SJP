package com.sjp.recruitment.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiInterviewConversationTemplateBankTest {

    private final AiInterviewConversationTemplateBank bank = new AiInterviewConversationTemplateBank();

    @Test
    void createsDeterministicNeutralMessagesWithoutCallingAi() {
        assertThat(bank.opening("Java Backend Developer"))
                .contains("Java Backend Developer")
                .doesNotContain("Rất tốt", "Chính xác");
        assertThat(bank.acknowledgement(4)).isEqualTo(bank.acknowledgement(4));
        assertThat(bank.acknowledgement(4)).doesNotContain("Rất tốt", "Chính xác");
        assertThat(bank.transition("Spring Boot", 2)).contains("Spring Boot");
        assertThat(bank.closing()).contains("đủ thông tin");
    }

    @Test
    void prefetchesOnlyLikelyAcknowledgementThenQuestionsTwoAndThree() {
        List<String> phrases = bank.initialPriorityPhrases(List.of(
                "Câu hỏi 1", "Câu hỏi 2", "Câu hỏi 3", "Câu hỏi 4", "Câu hỏi 5"));

        assertThat(phrases).containsExactly(
                bank.confirmationPrompt(),
                bank.acknowledgement(2),
                bank.transition(null, 2),
                "Câu hỏi 2",
                bank.transition(null, 3),
                "Câu hỏi 3"
        );
        assertThat(phrases).doesNotContain("Câu hỏi 1", "Câu hỏi 4", "Câu hỏi 5");
    }
}
