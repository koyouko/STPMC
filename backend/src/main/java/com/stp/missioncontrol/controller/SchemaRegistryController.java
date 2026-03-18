package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.service.SchemaRegistryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/self-service/{clusterId}/schemas")
public class SchemaRegistryController {

    private final SchemaRegistryService schemaRegistryService;

    public SchemaRegistryController(SchemaRegistryService schemaRegistryService) {
        this.schemaRegistryService = schemaRegistryService;
    }

    @GetMapping("/subjects")
    public ApiDtos.SchemaSubjectListResponse listSubjects(@PathVariable UUID clusterId) {
        return schemaRegistryService.listSubjects(clusterId);
    }

    @GetMapping("/subjects/{subject}/versions")
    public ApiDtos.SchemaSubjectVersionsResponse getSubjectVersions(
            @PathVariable UUID clusterId,
            @PathVariable String subject) {
        return schemaRegistryService.getSubjectVersions(clusterId, subject);
    }

    @GetMapping("/subjects/{subject}/versions/{version}")
    public ApiDtos.SchemaResponse getSchema(
            @PathVariable UUID clusterId,
            @PathVariable String subject,
            @PathVariable int version) {
        return schemaRegistryService.getSchema(clusterId, subject, version);
    }
}
