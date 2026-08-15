package com.sjp.recruitment.service;

import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.ApplicationRepository;
import com.sjp.recruitment.repository.CandidateCvRepository;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.CandidateSkillRepository;
import com.sjp.recruitment.repository.CvVersionRepository;
import com.sjp.recruitment.repository.JobRepository;
import com.sjp.recruitment.repository.NotificationRepository;
import com.sjp.recruitment.repository.SavedJobRepository;
import com.sjp.recruitment.repository.SkillRepository;
import com.sjp.recruitment.repository.SubscriptionRepository;
import com.sjp.recruitment.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateServiceCurrentProfileTest {

    @Mock private AuthService authService;
    @Mock private DtoMapper dtoMapper;
    @Mock private CandidateProfileRepository candidateProfileRepository;
    @Mock private CandidateCvRepository candidateCvRepository;
    @Mock private CvVersionRepository cvVersionRepository;
    @Mock private SavedJobRepository savedJobRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CandidateRealtimeEventPublisher realtimeEventPublisher;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private CandidateSkillRepository candidateSkillRepository;
    @Mock private StorageService storageService;
    @Mock private FeatureLimitService featureLimitService;
    @Mock private VietnamProvinceCatalog vietnamProvinceCatalog;

    @InjectMocks private CandidateService candidateService;

    @Test
    void currentProfileLoadsSkillsBeforeLeavingTransaction() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setRole(User.UserRole.CANDIDATE);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);

        CandidateProfile profile = new CandidateProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);

        when(authService.getCurrentUser()).thenReturn(user);
        when(candidateProfileRepository.findWithSkillsByUserId(userId)).thenReturn(Optional.of(profile));

        CandidateProfile result = candidateService.getCurrentCandidateProfile();

        assertSame(profile, result);
        verify(candidateProfileRepository).findWithSkillsByUserId(userId);
        verify(candidateProfileRepository, never()).findByUserId(userId);
    }
}
