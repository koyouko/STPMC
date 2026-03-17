package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.model.MissionControlEnums.ClusterEnvironment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClusterRepository extends JpaRepository<Cluster, UUID> {

    @Override
    @EntityGraph(attributePaths = {"listeners", "listeners.authProfile", "serviceEndpoints", "healthSnapshot", "healthSnapshot.components"})
    List<Cluster> findAll();

    @EntityGraph(attributePaths = {"listeners", "listeners.authProfile", "serviceEndpoints", "healthSnapshot", "healthSnapshot.components"})
    Optional<Cluster> findDetailedById(UUID id);

    @EntityGraph(attributePaths = {"listeners", "listeners.authProfile", "serviceEndpoints", "healthSnapshot", "healthSnapshot.components"})
    List<Cluster> findByEnvironment(ClusterEnvironment environment);
}
