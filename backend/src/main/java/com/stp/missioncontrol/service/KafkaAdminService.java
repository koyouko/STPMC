package com.stp.missioncontrol.service;

import com.stp.missioncontrol.config.AppProperties;
import com.stp.missioncontrol.dto.ApiDtos;
import com.stp.missioncontrol.model.Cluster;
import com.stp.missioncontrol.repository.ClusterRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.springframework.stereotype.Service;

@Service
public class KafkaAdminService {

    private final ClusterRepository clusterRepository;
    private final KafkaClientFactory kafkaClientFactory;
    private final AuditService auditService;
    private final AppProperties properties;

    public KafkaAdminService(
            ClusterRepository clusterRepository,
            KafkaClientFactory kafkaClientFactory,
            AuditService auditService,
            AppProperties properties
    ) {
        this.clusterRepository = clusterRepository;
        this.kafkaClientFactory = kafkaClientFactory;
        this.auditService = auditService;
        this.properties = properties;
    }

    // ── Topic Operations ──────────────────────────────────────────────

    public ApiDtos.TopicListResponse listTopics(UUID clusterId) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            Set<String> names = admin.listTopics().names().get(timeout, TimeUnit.MILLISECONDS);
            Map<String, TopicDescription> descriptions = admin.describeTopics(names).allTopicNames().get(timeout, TimeUnit.MILLISECONDS);
            List<ApiDtos.TopicSummary> topics = descriptions.values().stream()
                    .map(desc -> new ApiDtos.TopicSummary(desc.name(), desc.isInternal(), desc.partitions().size()))
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                    .toList();
            return new ApiDtos.TopicListResponse(clusterId, topics);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list topics: " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.TopicDescribeResponse describeTopic(UUID clusterId, String topicName) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            TopicDescription desc = admin.describeTopics(List.of(topicName)).allTopicNames()
                    .get(timeout, TimeUnit.MILLISECONDS).get(topicName);
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            Config config = admin.describeConfigs(List.of(resource)).all()
                    .get(timeout, TimeUnit.MILLISECONDS).get(resource);

            List<ApiDtos.TopicPartitionInfo> partitions = desc.partitions().stream()
                    .map(p -> new ApiDtos.TopicPartitionInfo(
                            p.partition(),
                            p.leader() != null ? p.leader().id() : -1,
                            p.replicas().stream().map(n -> n.id()).toList(),
                            p.isr().stream().map(n -> n.id()).toList()
                    ))
                    .toList();

            Map<String, String> configs = config.entries().stream()
                    .filter(entry -> !entry.isDefault())
                    .collect(Collectors.toMap(ConfigEntry::name, ConfigEntry::value, (a, b) -> a, LinkedHashMap::new));

            int replicationFactor = desc.partitions().isEmpty() ? 0 : desc.partitions().get(0).replicas().size();
            return new ApiDtos.TopicDescribeResponse(clusterId, topicName, desc.partitions().size(), replicationFactor, partitions, configs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to describe topic '" + topicName + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.CreateTopicResponse createTopic(UUID clusterId, ApiDtos.CreateTopicRequest request, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            NewTopic newTopic = new NewTopic(request.topicName(), request.numPartitions(), request.replicationFactor());
            if (request.configs() != null && !request.configs().isEmpty()) {
                newTopic.configs(request.configs());
            }
            admin.createTopics(List.of(newTopic)).all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "TOPIC_CREATED", "Topic", request.topicName(),
                    "Cluster " + clusterId + ", partitions=" + request.numPartitions() + ", rf=" + request.replicationFactor());
            return new ApiDtos.CreateTopicResponse(clusterId, request.topicName(), "Topic created successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create topic '" + request.topicName() + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.DeleteTopicResponse deleteTopic(UUID clusterId, String topicName, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            admin.deleteTopics(List.of(topicName)).all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "TOPIC_DELETED", "Topic", topicName, "Cluster " + clusterId);
            return new ApiDtos.DeleteTopicResponse(clusterId, topicName, "Topic deleted successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete topic '" + topicName + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.TopicPurgeResponse purgeTopic(UUID clusterId, String topicName, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            TopicDescription desc = admin.describeTopics(List.of(topicName)).allTopicNames()
                    .get(timeout, TimeUnit.MILLISECONDS).get(topicName);
            Map<TopicPartition, RecordsToDelete> records = new HashMap<>();
            for (TopicPartitionInfo p : desc.partitions()) {
                records.put(new TopicPartition(topicName, p.partition()), RecordsToDelete.beforeOffset(Long.MAX_VALUE));
            }
            admin.deleteRecords(records).all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "TOPIC_PURGED", "Topic", topicName, "Cluster " + clusterId);
            return new ApiDtos.TopicPurgeResponse(clusterId, topicName, "All records purged from " + desc.partitions().size() + " partitions");
        } catch (Exception e) {
            throw new RuntimeException("Failed to purge topic '" + topicName + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.IncreasePartitionsResponse increasePartitions(UUID clusterId, ApiDtos.IncreasePartitionsRequest request, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            TopicDescription desc = admin.describeTopics(List.of(request.topicName())).allTopicNames()
                    .get(timeout, TimeUnit.MILLISECONDS).get(request.topicName());
            int currentCount = desc.partitions().size();
            if (request.newPartitionCount() <= currentCount) {
                throw new IllegalArgumentException("New partition count (" + request.newPartitionCount() + ") must be greater than current (" + currentCount + ")");
            }
            admin.createPartitions(Map.of(request.topicName(), NewPartitions.increaseTo(request.newPartitionCount())))
                    .all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "PARTITIONS_INCREASED", "Topic", request.topicName(),
                    "Cluster " + clusterId + ", " + currentCount + " → " + request.newPartitionCount());
            return new ApiDtos.IncreasePartitionsResponse(clusterId, request.topicName(), currentCount, request.newPartitionCount());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to increase partitions for '" + request.topicName() + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.MessageCountResponse getMessageCount(UUID clusterId, String topicName) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        String previousKrb5 = System.getProperty("java.security.krb5.conf");
        try (KafkaConsumer<byte[], byte[]> consumer = kafkaClientFactory.createConsumer(cluster, "mission-control-count-" + UUID.randomUUID(), timeout)) {
            List<TopicPartition> partitions = consumer.partitionsFor(topicName).stream()
                    .map(info -> new TopicPartition(topicName, info.partition()))
                    .toList();
            Map<TopicPartition, Long> beginOffsets = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            Map<Integer, Long> partitionCounts = new LinkedHashMap<>();
            long total = 0;
            for (TopicPartition tp : partitions) {
                long count = endOffsets.getOrDefault(tp, 0L) - beginOffsets.getOrDefault(tp, 0L);
                partitionCounts.put(tp.partition(), count);
                total += count;
            }
            return new ApiDtos.MessageCountResponse(clusterId, topicName, partitionCounts, total);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get message count for '" + topicName + "': " + unwrapMessage(e), e);
        } finally {
            kafkaClientFactory.restoreKrb5(previousKrb5);
        }
    }

    public ApiDtos.TopicConfigDescribeResponse describeTopicConfig(UUID clusterId, String topicName) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            Config config = admin.describeConfigs(List.of(resource)).all()
                    .get(timeout, TimeUnit.MILLISECONDS).get(resource);
            List<ApiDtos.TopicConfigEntry> entries = config.entries().stream()
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                    .map(entry -> new ApiDtos.TopicConfigEntry(
                            entry.name(),
                            entry.isSensitive() ? "********" : entry.value(),
                            entry.source().name(),
                            entry.isDefault(),
                            entry.isSensitive(),
                            entry.isReadOnly()
                    ))
                    .toList();
            return new ApiDtos.TopicConfigDescribeResponse(clusterId, topicName, entries);
        } catch (Exception e) {
            throw new RuntimeException("Failed to describe config for topic '" + topicName + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.TopicConfigAlterResponse alterTopicConfig(UUID clusterId, ApiDtos.TopicConfigAlterRequest request, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, request.topicName());
            List<AlterConfigOp> ops = new ArrayList<>();
            if (request.configsToSet() != null) {
                request.configsToSet().forEach((key, value) ->
                        ops.add(new AlterConfigOp(new ConfigEntry(key, value), AlterConfigOp.OpType.SET)));
            }
            if (request.configsToDelete() != null) {
                request.configsToDelete().forEach(key ->
                        ops.add(new AlterConfigOp(new ConfigEntry(key, null), AlterConfigOp.OpType.DELETE)));
            }
            admin.incrementalAlterConfigs(Map.of(resource, ops)).all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "TOPIC_CONFIG_ALTERED", "Topic", request.topicName(),
                    "Cluster " + clusterId + ", set=" + (request.configsToSet() != null ? request.configsToSet().keySet() : "[]")
                            + ", deleted=" + (request.configsToDelete() != null ? request.configsToDelete() : "[]"));
            Map<String, String> updated = request.configsToSet() != null ? request.configsToSet() : Map.of();
            return new ApiDtos.TopicConfigAlterResponse(clusterId, request.topicName(), "Topic configuration updated", updated);
        } catch (Exception e) {
            throw new RuntimeException("Failed to alter config for topic '" + request.topicName() + "': " + unwrapMessage(e), e);
        }
    }

    // ── ACL Operations ────────────────────────────────────────────────

    public ApiDtos.AclListResponse listAcls(UUID clusterId) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            Collection<AclBinding> bindings = admin.describeAcls(AclBindingFilter.ANY).values()
                    .get(timeout, TimeUnit.MILLISECONDS);
            List<ApiDtos.AclEntry> entries = bindings.stream()
                    .map(this::toAclEntry)
                    .sorted((a, b) -> a.principal().compareToIgnoreCase(b.principal()))
                    .toList();
            return new ApiDtos.AclListResponse(clusterId, entries);
        } catch (Exception e) {
            if (isSecurityDisabled(e)) {
                return new ApiDtos.AclListResponse(clusterId, List.of());
            }
            throw new RuntimeException("Failed to list ACLs: " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.AclListResponse describeAcls(UUID clusterId, ApiDtos.AclDescribeRequest request) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            ResourcePatternFilter resourceFilter = new ResourcePatternFilter(
                    request.resourceType() != null ? ResourceType.fromString(request.resourceType()) : ResourceType.ANY,
                    request.resourceName(),
                    PatternType.ANY
            );
            AccessControlEntryFilter entryFilter = new AccessControlEntryFilter(
                    request.principal(),
                    null,
                    AclOperation.ANY,
                    AclPermissionType.ANY
            );
            AclBindingFilter filter = new AclBindingFilter(resourceFilter, entryFilter);
            Collection<AclBinding> bindings = admin.describeAcls(filter).values()
                    .get(timeout, TimeUnit.MILLISECONDS);
            List<ApiDtos.AclEntry> entries = bindings.stream()
                    .map(this::toAclEntry)
                    .toList();
            return new ApiDtos.AclListResponse(clusterId, entries);
        } catch (Exception e) {
            if (isSecurityDisabled(e)) {
                return new ApiDtos.AclListResponse(clusterId, List.of());
            }
            throw new RuntimeException("Failed to describe ACLs: " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.AclOperationResponse grantAcl(UUID clusterId, ApiDtos.AclGrantRequest request, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            AclBinding binding = new AclBinding(
                    new ResourcePattern(
                            ResourceType.fromString(request.resourceType()),
                            request.resourceName(),
                            PatternType.fromString(request.patternType())
                    ),
                    new AccessControlEntry(
                            request.principal(),
                            "*",
                            AclOperation.fromString(request.operation()),
                            request.permission() != null ? AclPermissionType.fromString(request.permission()) : AclPermissionType.ALLOW
                    )
            );
            admin.createAcls(List.of(binding)).all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "ACL_GRANTED", "ACL", request.resourceName(),
                    "Principal=" + request.principal() + ", op=" + request.operation() + ", cluster=" + clusterId);
            return new ApiDtos.AclOperationResponse(clusterId, "ACL granted successfully", 1);
        } catch (Exception e) {
            if (isSecurityDisabled(e)) {
                throw new RuntimeException("ACL operations are not available — no Authorizer is configured on this broker", e);
            }
            throw new RuntimeException("Failed to grant ACL: " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.AclOperationResponse removeAcl(UUID clusterId, ApiDtos.AclRemoveRequest request, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            ResourcePatternFilter resourceFilter = new ResourcePatternFilter(
                    request.resourceType() != null ? ResourceType.fromString(request.resourceType()) : ResourceType.ANY,
                    request.resourceName(),
                    request.patternType() != null ? PatternType.fromString(request.patternType()) : PatternType.ANY
            );
            AccessControlEntryFilter entryFilter = new AccessControlEntryFilter(
                    request.principal(),
                    null,
                    request.operation() != null ? AclOperation.fromString(request.operation()) : AclOperation.ANY,
                    request.permission() != null ? AclPermissionType.fromString(request.permission()) : AclPermissionType.ANY
            );
            AclBindingFilter filter = new AclBindingFilter(resourceFilter, entryFilter);
            // describeAcls first to count what will be removed
            Collection<AclBinding> existing = admin.describeAcls(filter).values().get(timeout, TimeUnit.MILLISECONDS);
            int count = existing.size();
            admin.deleteAcls(List.of(filter)).all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "ACL_REMOVED", "ACL", request.resourceName() != null ? request.resourceName() : "*",
                    "Principal=" + request.principal() + ", removed=" + count + ", cluster=" + clusterId);
            return new ApiDtos.AclOperationResponse(clusterId, "ACL(s) removed successfully", count);
        } catch (Exception e) {
            if (isSecurityDisabled(e)) {
                throw new RuntimeException("ACL operations are not available — no Authorizer is configured on this broker", e);
            }
            throw new RuntimeException("Failed to remove ACL: " + unwrapMessage(e), e);
        }
    }

    // ── Consumer Group Operations ─────────────────────────────────────

    public ApiDtos.ConsumerGroupListResponse listConsumerGroups(UUID clusterId) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            Collection<ConsumerGroupListing> listings = admin.listConsumerGroups().all()
                    .get(timeout, TimeUnit.MILLISECONDS);
            List<ApiDtos.ConsumerGroupSummary> groups = listings.stream()
                    .map(listing -> new ApiDtos.ConsumerGroupSummary(
                            listing.groupId(),
                            listing.state().map(Enum::name).orElse("UNKNOWN"),
                            listing.type().map(Enum::name).orElse("UNKNOWN")
                    ))
                    .sorted((a, b) -> a.groupId().compareToIgnoreCase(b.groupId()))
                    .toList();
            return new ApiDtos.ConsumerGroupListResponse(clusterId, groups);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list consumer groups: " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.ConsumerGroupDescribeResponse describeConsumerGroup(UUID clusterId, String groupId) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            ConsumerGroupDescription desc = admin.describeConsumerGroups(List.of(groupId)).all()
                    .get(timeout, TimeUnit.MILLISECONDS).get(groupId);

            List<ApiDtos.ConsumerGroupMemberInfo> members = desc.members().stream()
                    .map(member -> new ApiDtos.ConsumerGroupMemberInfo(
                            member.consumerId(),
                            member.clientId(),
                            member.host(),
                            member.assignment().topicPartitions().stream()
                                    .map(tp -> new ApiDtos.TopicPartitionAssignment(tp.topic(), tp.partition()))
                                    .toList()
                    ))
                    .toList();

            // Get offsets and end offsets for lag calculation
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committedOffsets =
                    admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                            .get(timeout, TimeUnit.MILLISECONDS);

            Map<TopicPartition, OffsetSpec> offsetSpecs = new HashMap<>();
            committedOffsets.keySet().forEach(tp -> offsetSpecs.put(tp, OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    admin.listOffsets(offsetSpecs).all().get(timeout, TimeUnit.MILLISECONDS);

            List<ApiDtos.ConsumerGroupOffsetInfo> offsets = committedOffsets.entrySet().stream()
                    .map(entry -> {
                        TopicPartition tp = entry.getKey();
                        long currentOffset = entry.getValue().offset();
                        long logEndOffset = endOffsets.containsKey(tp) ? endOffsets.get(tp).offset() : currentOffset;
                        return new ApiDtos.ConsumerGroupOffsetInfo(
                                tp.topic(), tp.partition(), currentOffset, logEndOffset, logEndOffset - currentOffset);
                    })
                    .sorted((a, b) -> {
                        int cmp = a.topic().compareTo(b.topic());
                        return cmp != 0 ? cmp : Integer.compare(a.partition(), b.partition());
                    })
                    .toList();

            return new ApiDtos.ConsumerGroupDescribeResponse(
                    clusterId, groupId, desc.state().name(),
                    desc.coordinator().host() + ":" + desc.coordinator().port(),
                    members, offsets);
        } catch (Exception e) {
            throw new RuntimeException("Failed to describe consumer group '" + groupId + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.ConsumerGroupDeleteResponse deleteConsumerGroup(UUID clusterId, String groupId, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            admin.deleteConsumerGroups(List.of(groupId)).all().get(timeout, TimeUnit.MILLISECONDS);
            auditService.record(actor, "CONSUMER_GROUP_DELETED", "ConsumerGroup", groupId, "Cluster " + clusterId);
            return new ApiDtos.ConsumerGroupDeleteResponse(clusterId, groupId, "Consumer group deleted successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete consumer group '" + groupId + "': " + unwrapMessage(e), e);
        }
    }

    public ApiDtos.OffsetResetResponse resetConsumerGroupOffsets(UUID clusterId, ApiDtos.OffsetResetRequest request, String actor) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        try (AdminClient admin = kafkaClientFactory.createAdminClient(cluster, timeout)) {
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committed =
                    admin.listConsumerGroupOffsets(request.groupId()).partitionsToOffsetAndMetadata()
                            .get(timeout, TimeUnit.MILLISECONDS);

            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> newOffsets = new HashMap<>();

            switch (request.resetType().toLowerCase()) {
                case "earliest" -> {
                    Map<TopicPartition, OffsetSpec> specs = new HashMap<>();
                    committed.keySet().forEach(tp -> specs.put(tp, OffsetSpec.earliest()));
                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliest = admin.listOffsets(specs).all().get(timeout, TimeUnit.MILLISECONDS);
                    earliest.forEach((tp, info) -> newOffsets.put(tp, new org.apache.kafka.clients.consumer.OffsetAndMetadata(info.offset())));
                }
                case "latest" -> {
                    Map<TopicPartition, OffsetSpec> specs = new HashMap<>();
                    committed.keySet().forEach(tp -> specs.put(tp, OffsetSpec.latest()));
                    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = admin.listOffsets(specs).all().get(timeout, TimeUnit.MILLISECONDS);
                    latest.forEach((tp, info) -> newOffsets.put(tp, new org.apache.kafka.clients.consumer.OffsetAndMetadata(info.offset())));
                }
                case "specific" -> {
                    if (request.partitionOffsets() == null || request.partitionOffsets().isEmpty()) {
                        throw new IllegalArgumentException("partitionOffsets required for specific reset type");
                    }
                    // Determine topic from committed offsets
                    String topic = committed.keySet().stream().map(TopicPartition::topic).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("No committed offsets found for group"));
                    request.partitionOffsets().forEach((partition, offset) ->
                            newOffsets.put(new TopicPartition(topic, partition), new org.apache.kafka.clients.consumer.OffsetAndMetadata(offset)));
                }
                default -> throw new IllegalArgumentException("Invalid resetType: " + request.resetType() + ". Must be earliest, latest, or specific");
            }

            admin.alterConsumerGroupOffsets(request.groupId(), newOffsets).all().get(timeout, TimeUnit.MILLISECONDS);

            Map<String, Map<Integer, Long>> result = new HashMap<>();
            newOffsets.forEach((tp, meta) -> result.computeIfAbsent(tp.topic(), k -> new LinkedHashMap<>())
                    .put(tp.partition(), meta.offset()));

            auditService.record(actor, "OFFSETS_RESET", "ConsumerGroup", request.groupId(),
                    "Cluster " + clusterId + ", type=" + request.resetType());
            return new ApiDtos.OffsetResetResponse(clusterId, request.groupId(), result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset offsets for group '" + request.groupId() + "': " + unwrapMessage(e), e);
        }
    }

    // ── Data Operations ───────────────────────────────────────────────

    public ApiDtos.TopicDataDumpResponse dumpTopicMessages(UUID clusterId, ApiDtos.TopicDataDumpRequest request) {
        Cluster cluster = resolveCluster(clusterId);
        int timeout = properties.selfService().kafkaTimeoutMs();
        int maxMessages = Math.min(request.maxMessages(), 100);
        String previousKrb5 = System.getProperty("java.security.krb5.conf");
        try (KafkaConsumer<byte[], byte[]> consumer = kafkaClientFactory.createConsumer(
                cluster, "mission-control-dump-" + UUID.randomUUID(), timeout)) {

            List<TopicPartition> partitions;
            if (request.partition() != null) {
                partitions = List.of(new TopicPartition(request.topicName(), request.partition()));
            } else {
                partitions = consumer.partitionsFor(request.topicName()).stream()
                        .map(info -> new TopicPartition(request.topicName(), info.partition()))
                        .toList();
            }

            consumer.assign(partitions);
            // Seek to end - maxMessages per partition (or beginning if fewer messages)
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
            Map<TopicPartition, Long> beginOffsets = consumer.beginningOffsets(partitions);
            for (TopicPartition tp : partitions) {
                long end = endOffsets.getOrDefault(tp, 0L);
                long begin = beginOffsets.getOrDefault(tp, 0L);
                long seekTo = Math.max(begin, end - maxMessages);
                consumer.seek(tp, seekTo);
            }

            List<ApiDtos.DumpedMessage> messages = new ArrayList<>();
            long deadline = System.currentTimeMillis() + timeout;
            while (messages.size() < maxMessages && System.currentTimeMillis() < deadline) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    if (messages.size() >= maxMessages) break;
                    Map<String, String> headers = new LinkedHashMap<>();
                    record.headers().forEach(h -> headers.put(h.key(), h.value() != null ? new String(h.value(), StandardCharsets.UTF_8) : null));
                    messages.add(new ApiDtos.DumpedMessage(
                            record.partition(),
                            record.offset(),
                            record.key() != null ? new String(record.key(), StandardCharsets.UTF_8) : null,
                            record.value() != null ? new String(record.value(), StandardCharsets.UTF_8) : null,
                            record.timestamp(),
                            headers
                    ));
                }
                if (records.isEmpty()) break;
            }
            return new ApiDtos.TopicDataDumpResponse(clusterId, request.topicName(), messages);
        } catch (Exception e) {
            throw new RuntimeException("Failed to dump messages from '" + request.topicName() + "': " + unwrapMessage(e), e);
        } finally {
            kafkaClientFactory.restoreKrb5(previousKrb5);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Cluster resolveCluster(UUID clusterId) {
        Cluster cluster = clusterRepository.findDetailedById(clusterId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster not found: " + clusterId));
        if (!cluster.isActive()) {
            throw new IllegalArgumentException("Cluster is not active: " + clusterId);
        }
        return cluster;
    }

    private ApiDtos.AclEntry toAclEntry(AclBinding binding) {
        return new ApiDtos.AclEntry(
                binding.pattern().resourceType().name(),
                binding.pattern().name(),
                binding.pattern().patternType().name(),
                binding.entry().principal(),
                binding.entry().host(),
                binding.entry().operation().name(),
                binding.entry().permissionType().name()
        );
    }

    private String unwrapMessage(Exception e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }

    private boolean isSecurityDisabled(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause instanceof org.apache.kafka.common.errors.SecurityDisabledException
                || (cause.getMessage() != null && cause.getMessage().contains("No Authorizer"));
    }
}
