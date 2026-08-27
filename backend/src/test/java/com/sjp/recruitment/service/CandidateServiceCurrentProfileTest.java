package com.sjp.recruitment.service;

import com.sjp.recruitment.exception.ApiException;
import com.sjp.recruitment.model.dto.request.CandidateProfileRequest;
import com.sjp.recruitment.model.dto.response.CandidateProfileResponse;
import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.Skill;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void updateProfile_rejectsNonCandidate() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(User.UserRole.EMPLOYER);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        when(authService.getCurrentUser()).thenReturn(user);

        ApiException ex = assertThrows(ApiException.class, () -> candidateService.updateProfile(profileRequest("An")));
        assertEquals("CANDIDATE_REQUIRED", ex.getCode());
    }

    @Test
    void updateProfile_rejectsUnverifiedEmail() {
        User user = candidateUser();
        user.setEmailVerified(false);
        when(authService.getCurrentUser()).thenReturn(user);

        ApiException ex = assertThrows(ApiException.class, () -> candidateService.updateProfile(profileRequest("An")));
        assertEquals("EMAIL_NOT_VERIFIED", ex.getCode());
    }

    @Test
    void updateProfile_rejectsMissingProfile() {
        User user = candidateUser();
        when(authService.getCurrentUser()).thenReturn(user);
        when(candidateProfileRepository.findWithSkillsByUserId(user.getId())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> candidateService.updateProfile(profileRequest("An")));
        assertEquals("CANDIDATE_PROFILE_NOT_FOUND", ex.getCode());
    }

    @Test
    void updateProfile_savesNameSkillsAndMapsResponse() {
        User user = candidateUser();
        CandidateProfile profile = new CandidateProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
        CandidateProfileResponse expected = org.mockito.Mockito.mock(CandidateProfileResponse.class);

        when(authService.getCurrentUser()).thenReturn(user);
        when(candidateProfileRepository.findWithSkillsByUserId(user.getId())).thenReturn(Optional.of(profile));
        when(skillRepository.findByNameIgnoreCase("Java")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateCvRepository.existsByCandidateIdAndDeletedAtIsNull(profile.getId())).thenReturn(true);
        when(candidateProfileRepository.save(profile)).thenReturn(profile);
        when(dtoMapper.toCandidateProfileResponse(profile, true, List.of())).thenReturn(expected);

        CandidateProfileResponse result = candidateService.updateProfile(profileRequest("Nguyen Van A"));

        assertSame(expected, result);
        assertEquals("Nguyen Van A", user.getFullName());
        verify(candidateSkillRepository).deleteByCandidateId(profile.getId());
        verify(candidateProfileRepository).save(profile);
    }

    private static User candidateUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setRole(User.UserRole.CANDIDATE);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private static CandidateProfileRequest profileRequest(String fullName) {
        return new CandidateProfileRequest(
                fullName, "0901234567", null, "Ha Noi", "Bio", List.of("Java"),
                "Backend", 2, "junior", null, null, List.of(), List.of(), List.of(), List.of()
        );
    }
}
