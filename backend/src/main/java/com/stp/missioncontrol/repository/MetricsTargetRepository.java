package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.MetricsTarget;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetricsTargetRepository extends JpaRepository<MetricsTarget, UUID> {

    List<MetricsTarget> findByEnabledTrue();
}
