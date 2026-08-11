package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CandidateOfferResponseRequest;
import com.sjp.recruitment.model.entity.Application;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.JobOffer;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.EmployerRepository;
import com.sjp.recruitment.repository.InterviewScheduleRepository;
import com.sjp.recruitment.repository.JobOfferRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationWorkflowOfferStateTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private InterviewScheduleRepository interviewScheduleRepository;
    @Mock private JobOfferRepository jobOfferRepository;
    @Mock private EmailService emailService;
    @Mock private DtoMapper dtoMapper;
    @Mock private ApplicationService applicationService;
    @Mock private EmployerRepository employerRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private ApplicationWorkflowService workflowService;

    @Test
    void rejectsRepeatedResponseBeforeWritingAnything() {
        UUID candidateId = UUID.randomUUID();
        JobOffer offer = offerFor(candidateId, "accepted");
        when(jobOfferRepository.findById(offer.getId())).thenReturn(Optional.of(offer));

        ApiException exception = assertThrows(ApiException.class, () ->
                workflowService.candidateRespondToOffer(
                        offer.getId(),
                        candidateId,
                        new CandidateOfferResponseRequest(CandidateOfferResponseRequest.Decision.REJECT, "changed mind")
                ));

        assertEquals("OFFER_ALREADY_RESPONDED", exception.getCode());
        verify(jobOfferRepository, never()).saveAndFlush(offer);
    }

    private JobOffer offerFor(UUID candidateId, String status) {
        CandidateProfile candidate = new CandidateProfile();
        candidate.setId(candidateId);
        Application application = new Application();
        application.setCandidate(candidate);
        JobOffer offer = new JobOffer();
        offer.setId(UUID.randomUUID());
        offer.setApplication(application);
        offer.setStatus(status);
        return offer;
    }
}
