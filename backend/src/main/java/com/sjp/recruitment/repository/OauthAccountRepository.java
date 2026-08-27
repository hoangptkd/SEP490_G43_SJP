package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.OauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OauthAccountRepository extends JpaRepository<OauthAccount, UUID> {
    Optional<OauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);
}
