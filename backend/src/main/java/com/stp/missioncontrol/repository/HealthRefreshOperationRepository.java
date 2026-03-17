package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.HealthRefreshOperation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthRefreshOperationRepository extends JpaRepository<HealthRefreshOperation, UUID> {

    Optional<HealthRefreshOperation> findFirstByClusterIdAndRequestedAtAfterOrderByRequestedAtDesc(UUID clusterId, Instant requestedAfter);
}
