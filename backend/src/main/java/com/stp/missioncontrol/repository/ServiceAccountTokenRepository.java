package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.ServiceAccountToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceAccountTokenRepository extends JpaRepository<ServiceAccountToken, UUID> {

    Optional<ServiceAccountToken> findByTokenHash(String tokenHash);
}
