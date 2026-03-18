package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.AuditEvent;
import com.stp.missioncontrol.repository.AuditEventRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/audit")
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    public AuditController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @GetMapping
    public ApiDtos.AuditPageResponse getAuditEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 200));
        Page<AuditEvent> result;

        if (search != null && !search.isBlank()) {
            result = auditEventRepository
                    .findByActorContainingIgnoreCaseOrActionContainingIgnoreCaseOrEntityTypeContainingIgnoreCaseOrderByCreatedAtDesc(
                            search, search, search, pageRequest);
        } else {
            result = auditEventRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        }

        List<ApiDtos.AuditEventResponse> events = result.getContent().stream()
                .map(e -> new ApiDtos.AuditEventResponse(
                        e.getId(), e.getActor(), e.getAction(),
                        e.getEntityType(), e.getEntityId(),
                        e.getDetails(), e.getCreatedAt()))
                .toList();

        return new ApiDtos.AuditPageResponse(events, result.getTotalElements(), result.getTotalPages(), page);
    }
}
