package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.ClusterHealthSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterHealthSnapshotRepository extends JpaRepository<ClusterHealthSnapshot, UUID> {

    Optional<ClusterHealthSnapshot> findByClusterId(UUID clusterId);
}
