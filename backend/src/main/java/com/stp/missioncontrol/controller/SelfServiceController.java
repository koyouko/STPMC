package com.stp.missioncontrol.controller;

import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.MissionControlEnums.SelfServiceCategory;
import com.stp.missioncontrol.model.MissionControlEnums.SelfServiceTaskType;
import com.stp.missioncontrol.service.KafkaAdminService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/self-service")
public class SelfServiceController {

    private final KafkaAdminService kafkaAdminService;

    public SelfServiceController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    @GetMapping("/tasks")
    public List<ApiDtos.TaskCatalogEntry> getTaskCatalog() {
        return List.of(
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_LIST, SelfServiceCategory.TOPIC, "Topic List", "List all topics in the cluster", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_DESCRIBE, SelfServiceCategory.TOPIC, "Topic Describe", "Describe a topic's partitions, replicas, and configuration", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_CREATE, SelfServiceCategory.TOPIC, "Topic Create", "Create a new topic with partition and replication settings", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_DELETE, SelfServiceCategory.TOPIC, "Topic Delete", "Delete a topic and all its data", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_PURGE, SelfServiceCategory.TOPIC, "Topic Purge", "Delete all records from a topic without removing the topic", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_INCREASE_PARTITIONS, SelfServiceCategory.TOPIC, "Increase Partitions", "Increase the number of partitions for a topic", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_MESSAGE_COUNT, SelfServiceCategory.TOPIC, "Message Count", "Count messages across all partitions of a topic", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_CONFIG_DESCRIBE, SelfServiceCategory.TOPIC, "Topic Config Describe", "View all configuration entries for a topic", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_CONFIG_ALTER, SelfServiceCategory.TOPIC, "Topic Config Alter", "Modify topic configuration (retention, compression, etc.)", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.ACL_LIST, SelfServiceCategory.ACL, "ACL List", "List all access control entries in the cluster", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.ACL_DESCRIBE, SelfServiceCategory.ACL, "ACL Describe", "Filter and describe ACLs by principal, resource, or type", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.ACL_GRANT, SelfServiceCategory.ACL, "ACL Grant", "Grant a new ACL permission to a principal", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.ACL_REMOVE, SelfServiceCategory.ACL, "ACL Remove", "Remove ACL permissions matching a filter", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.CONSUMER_GROUP_LIST, SelfServiceCategory.CONSUMER_GROUP, "Consumer Group List", "List all consumer groups in the cluster", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.CONSUMER_GROUP_DESCRIBE, SelfServiceCategory.CONSUMER_GROUP, "Consumer Group Describe", "Describe group members, assignments, offsets, and lag", true),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.CONSUMER_GROUP_DELETE, SelfServiceCategory.CONSUMER_GROUP, "Consumer Group Delete", "Delete an inactive consumer group", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.CONSUMER_GROUP_OFFSETS, SelfServiceCategory.CONSUMER_GROUP, "Offsets Management", "Reset consumer group offsets to earliest, latest, or a specific offset", false),
                new ApiDtos.TaskCatalogEntry(SelfServiceTaskType.TOPIC_DATA_DUMP, SelfServiceCategory.DATA, "Topic Data Dump", "Sample messages from a topic (up to 100 messages)", true)
        );
    }

    // ── Topic Endpoints ───────────────────────────────────────────────

    @GetMapping("/{clusterId}/topics")
    public ApiDtos.TopicListResponse listTopics(@PathVariable UUID clusterId) {
        return kafkaAdminService.listTopics(clusterId);
    }

    @PostMapping("/{clusterId}/topics/describe")
    public ApiDtos.TopicDescribeResponse describeTopic(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.TopicOperationRequest request) {
        return kafkaAdminService.describeTopic(clusterId, request.topicName());
    }

    @PostMapping("/{clusterId}/topics/create")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.CreateTopicResponse createTopic(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.CreateTopicRequest request, Principal principal) {
        return kafkaAdminService.createTopic(clusterId, request, actorName(principal));
    }

    @DeleteMapping("/{clusterId}/topics/{topicName}")
    public ApiDtos.DeleteTopicResponse deleteTopic(@PathVariable UUID clusterId, @PathVariable String topicName, Principal principal) {
        return kafkaAdminService.deleteTopic(clusterId, topicName, actorName(principal));
    }

    @PostMapping("/{clusterId}/topics/purge")
    public ApiDtos.TopicPurgeResponse purgeTopic(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.TopicOperationRequest request, Principal principal) {
        return kafkaAdminService.purgeTopic(clusterId, request.topicName(), actorName(principal));
    }

    @PostMapping("/{clusterId}/topics/increase-partitions")
    public ApiDtos.IncreasePartitionsResponse increasePartitions(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.IncreasePartitionsRequest request, Principal principal) {
        return kafkaAdminService.increasePartitions(clusterId, request, actorName(principal));
    }

    @PostMapping("/{clusterId}/topics/message-count")
    public ApiDtos.MessageCountResponse messageCount(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.TopicOperationRequest request) {
        return kafkaAdminService.getMessageCount(clusterId, request.topicName());
    }

    @PostMapping("/{clusterId}/topics/config/describe")
    public ApiDtos.TopicConfigDescribeResponse describeTopicConfig(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.TopicOperationRequest request) {
        return kafkaAdminService.describeTopicConfig(clusterId, request.topicName());
    }

    @PostMapping("/{clusterId}/topics/config/alter")
    public ApiDtos.TopicConfigAlterResponse alterTopicConfig(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.TopicConfigAlterRequest request, Principal principal) {
        return kafkaAdminService.alterTopicConfig(clusterId, request, actorName(principal));
    }

    @PostMapping("/{clusterId}/topics/data-dump")
    public ApiDtos.TopicDataDumpResponse dumpTopicMessages(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.TopicDataDumpRequest request) {
        return kafkaAdminService.dumpTopicMessages(clusterId, request);
    }

    // ── ACL Endpoints ─────────────────────────────────────────────────

    @GetMapping("/{clusterId}/acls")
    public ApiDtos.AclListResponse listAcls(@PathVariable UUID clusterId) {
        return kafkaAdminService.listAcls(clusterId);
    }

    @PostMapping("/{clusterId}/acls/describe")
    public ApiDtos.AclListResponse describeAcls(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.AclDescribeRequest request) {
        return kafkaAdminService.describeAcls(clusterId, request);
    }

    @PostMapping("/{clusterId}/acls/grant")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.AclOperationResponse grantAcl(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.AclGrantRequest request, Principal principal) {
        return kafkaAdminService.grantAcl(clusterId, request, actorName(principal));
    }

    @PostMapping("/{clusterId}/acls/remove")
    public ApiDtos.AclOperationResponse removeAcl(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.AclRemoveRequest request, Principal principal) {
        return kafkaAdminService.removeAcl(clusterId, request, actorName(principal));
    }

    // ── Consumer Group Endpoints ──────────────────────────────────────

    @GetMapping("/{clusterId}/consumer-groups")
    public ApiDtos.ConsumerGroupListResponse listConsumerGroups(@PathVariable UUID clusterId) {
        return kafkaAdminService.listConsumerGroups(clusterId);
    }

    @PostMapping("/{clusterId}/consumer-groups/describe")
    public ApiDtos.ConsumerGroupDescribeResponse describeConsumerGroup(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.ConsumerGroupRequest request) {
        return kafkaAdminService.describeConsumerGroup(clusterId, request.groupId());
    }

    @DeleteMapping("/{clusterId}/consumer-groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConsumerGroup(@PathVariable UUID clusterId, @PathVariable String groupId, Principal principal) {
        kafkaAdminService.deleteConsumerGroup(clusterId, groupId, actorName(principal));
    }

    @PostMapping("/{clusterId}/consumer-groups/reset-offsets")
    public ApiDtos.OffsetResetResponse resetConsumerGroupOffsets(@PathVariable UUID clusterId, @Valid @RequestBody ApiDtos.OffsetResetRequest request, Principal principal) {
        return kafkaAdminService.resetConsumerGroupOffsets(clusterId, request, actorName(principal));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String actorName(Principal principal) {
        return principal != null ? principal.getName() : "unknown";
    }
}
