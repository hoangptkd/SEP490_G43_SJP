package com.sjp.recruitment.repository;

import com.sjp.recruitment.model.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findTopByUserIdOrderByStartedAtDesc(UUID userId);
}
