package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.AuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
