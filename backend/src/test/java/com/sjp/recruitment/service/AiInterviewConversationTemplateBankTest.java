package com.sjp.recruitment.service;

import org.junit.jupiter.api.Test;

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
}
