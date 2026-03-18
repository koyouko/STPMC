package com.stp.missioncontrol.repository;

import com.stp.missioncontrol.model.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditEvent> findByActorContainingIgnoreCaseOrActionContainingIgnoreCaseOrEntityTypeContainingIgnoreCaseOrderByCreatedAtDesc(
            String actor, String action, String entityType, Pageable pageable);
}
