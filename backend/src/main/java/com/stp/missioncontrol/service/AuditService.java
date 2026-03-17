package com.stp.missioncontrol.service;

import com.stp.missioncontrol.model.AuditEvent;
import com.stp.missioncontrol.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void record(String actor, String action, String entityType, String entityId, String details) {
        auditEventRepository.save(new AuditEvent(actor, action, entityType, entityId, details));
    }
}
