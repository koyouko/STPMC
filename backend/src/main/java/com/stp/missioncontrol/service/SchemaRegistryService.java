package com.stp.missioncontrol.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stp.missioncontrol.config.AppProperties;
import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.model.MissionControlEnums.ComponentKind;
import com.stp.missioncontrol.model.ServiceEndpoint;
import com.stp.missioncontrol.repository.ClusterRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SchemaRegistryService {

    private static final Logger log = LoggerFactory.getLogger(SchemaRegistryService.class);

    private final ClusterRepository clusterRepository;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SchemaRegistryService(ClusterRepository clusterRepository, AppProperties properties) {
        this.clusterRepository = clusterRepository;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.selfService().kafkaTimeoutMs()))
                .build();
    }

    public ApiDtos.SchemaSubjectListResponse listSubjects(UUID clusterId) {
        String baseUrl = resolveSchemaRegistryUrl(clusterId);
        String json = fetchGet(baseUrl + "/subjects");
        try {
            List<String> subjects = objectMapper.readValue(json, new TypeReference<>() {
            });
            return new ApiDtos.SchemaSubjectListResponse(clusterId, subjects);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse subjects response: " + e.getMessage(), e);
        }
    }

    public ApiDtos.SchemaSubjectVersionsResponse getSubjectVersions(UUID clusterId, String subject) {
        String baseUrl = resolveSchemaRegistryUrl(clusterId);
        String json = fetchGet(baseUrl + "/subjects/" + encodeUri(subject) + "/versions");
        try {
            List<Integer> versions = objectMapper.readValue(json, new TypeReference<>() {
            });
            return new ApiDtos.SchemaSubjectVersionsResponse(clusterId, subject, versions);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse versions response: " + e.getMessage(), e);
        }
    }

    public ApiDtos.SchemaResponse getSchema(UUID clusterId, String subject, int version) {
        String baseUrl = resolveSchemaRegistryUrl(clusterId);
        String json = fetchGet(baseUrl + "/subjects/" + encodeUri(subject) + "/versions/" + version);
        try {
            JsonNode node = objectMapper.readTree(json);
            return new ApiDtos.SchemaResponse(
                    clusterId,
                    node.path("subject").asText(subject),
                    node.path("version").asInt(version),
                    node.path("id").asInt(0),
                    node.path("schemaType").asText("AVRO"),
                    node.path("schema").asText("")
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse schema response: " + e.getMessage(), e);
        }
    }

    private String resolveSchemaRegistryUrl(UUID clusterId) {
        Cluster cluster = clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found: " + clusterId));

        if (!cluster.isActive()) {
            throw new IllegalArgumentException("Cluster is inactive: " + cluster.getName());
        }

        // Look for Schema Registry endpoint on this cluster
        for (ServiceEndpoint ep : cluster.getServiceEndpoints()) {
            if (ep.getKind() == ComponentKind.SCHEMA_REGISTRY && ep.isEnabled()) {
                if (ep.getBaseUrl() != null && !ep.getBaseUrl().isBlank()) {
                    return ep.getBaseUrl().replaceAll("/+$", "");
                }
                if (ep.getHost() != null && ep.getPort() != null) {
                    String protocol = ep.getProtocol() != null ? ep.getProtocol().name().toLowerCase() : "http";
                    return protocol + "://" + ep.getHost() + ":" + ep.getPort();
                }
            }
        }

        // Fallback to local default
        String fallback = properties.defaults().localSchemaRegistryUrl();
        if (fallback != null && !fallback.isBlank()) {
            log.debug("Using fallback Schema Registry URL for cluster {}: {}", clusterId, fallback);
            return fallback.replaceAll("/+$", "");
        }

        throw new IllegalStateException("No Schema Registry configured for cluster: " + cluster.getName());
    }

    private String fetchGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.selfService().kafkaTimeoutMs()))
                    .header("Accept", "application/vnd.schemaregistry.v1+json, application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new RuntimeException("Schema Registry returned HTTP " + response.statusCode() + ": " + response.body());
            }

            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Schema Registry: " + e.getMessage(), e);
        }
    }

    private String encodeUri(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
