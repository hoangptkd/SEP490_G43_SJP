package com.sjp.recruitment.config;

import com.sjp.recruitment.model.entity.CandidateProfile;
import com.sjp.recruitment.model.entity.User;
import com.sjp.recruitment.repository.CandidateProfileRepository;
import com.sjp.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.data-seeder.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_CANDIDATE_EMAIL = "candidate.demo@sjp.local";
    private static final String DEMO_CANDIDATE_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedDemoCandidate();
    }

    private void seedDemoCandidate() {
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository.findByEmail(DEMO_CANDIDATE_EMAIL)
                .orElseGet(() -> {
                    User created = new User();
                    created.setEmail(DEMO_CANDIDATE_EMAIL);
                    created.setCreatedAt(now);
                    return created;
                });

        user.setPasswordHash(passwordEncoder.encode(DEMO_CANDIDATE_PASSWORD));
        user.setFullName("Demo Candidate");
        user.setPhone("0900000000");
        user.setRole(User.UserRole.CANDIDATE);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setUpdatedAt(now);

        User savedUser = userRepository.save(user);

        candidateProfileRepository.findByUserId(savedUser.getId())
                .orElseGet(() -> {
                    CandidateProfile profile = new CandidateProfile();
                    profile.setUser(savedUser);
                    profile.setHeadline("Demo Candidate");
                    profile.setBio("Demo profile for local development");
                    profile.setLocation("Ha Noi");
                    profile.setExperienceYears(1);
                    profile.setExperienceLevel("FRESHER");
                    profile.setCreatedAt(now);
                    profile.setUpdatedAt(now);
                    return candidateProfileRepository.save(profile);
                });
    }
}
