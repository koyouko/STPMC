import type {
  AclListResponse,
  AclOperationResponse,
  AuditPageResponse,
  ClusterConfigResponse,
  ClusterHealthDetailResponse,
  ClusterHealthSummaryResponse,
  ConsumerGroupDeleteResponse,
  ConsumerGroupDescribeResponse,
  ConsumerGroupListResponse,
  CreateClusterRequest,
  CreateServiceAccountRequest,
  CreateServiceAccountTokenRequest,
  CreateTopicResponse,
  DeleteTopicResponse,
  IncreasePartitionsResponse,
  MessageCountResponse,
  OffsetResetResponse,
  RefreshOperationResponse,
  ServiceAccountResponse,
  ServiceAccountTokenResponse,
  TaskCatalogEntry,
  TestConnectionRequest,
  TestConnectionResponse,
  TopicConfigAlterResponse,
  TopicConfigDescribeResponse,
  TopicDataDumpResponse,
  TopicDescribeResponse,
  TopicListResponse,
  TopicPurgeResponse,
  UpdateClusterRequest,
  SchemaSubjectListResponse,
  SchemaSubjectVersionsResponse,
  SchemaResponse,
} from '../types/api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const defaultHeaders = {
  'Content-Type': 'application/json',
  'X-MC-User': 'frontend-operator',
  'X-MC-Roles': 'PLATFORM_ADMIN,OPERATOR,AUDITOR',
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      ...defaultHeaders,
      ...(init?.headers ?? {}),
    },
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || `Request failed with ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const apiClient = {
  listClusters() {
    return request<ClusterHealthSummaryResponse[]>('/api/platform/clusters')
  },
  getClusterHealth(clusterId: string) {
    return request<ClusterHealthDetailResponse>(`/api/platform/clusters/${clusterId}/health`)
  },
  refreshClusterHealth(clusterId: string) {
    return request<RefreshOperationResponse>(`/api/platform/clusters/${clusterId}/health/refresh`, {
      method: 'POST',
    })
  },
  getClusterConfig(clusterId: string) {
    return request<ClusterConfigResponse>(`/api/platform/clusters/${clusterId}/config`)
  },
  createCluster(payload: CreateClusterRequest) {
    return request<ClusterHealthDetailResponse>('/api/platform/clusters', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  updateCluster(clusterId: string, payload: UpdateClusterRequest) {
    return request<ClusterHealthDetailResponse>(`/api/platform/clusters/${clusterId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  deleteCluster(clusterId: string) {
    return request<void>(`/api/platform/clusters/${clusterId}`, {
      method: 'DELETE',
    })
  },
  testConnection(payload: TestConnectionRequest) {
    return request<TestConnectionResponse>('/api/platform/clusters/test-connection', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  listServiceAccounts() {
    return request<ServiceAccountResponse[]>('/api/admin/service-accounts')
  },
  createServiceAccount(payload: CreateServiceAccountRequest) {
    return request<ServiceAccountResponse>('/api/admin/service-accounts', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  createServiceAccountToken(serviceAccountId: string, payload: CreateServiceAccountTokenRequest) {
    return request<ServiceAccountTokenResponse>(`/api/admin/service-accounts/${serviceAccountId}/tokens`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  // ── Self-Service ──────────────────────────────────────────────────

  getTaskCatalog() {
    return request<TaskCatalogEntry[]>('/api/platform/self-service/tasks')
  },
  listTopics(clusterId: string) {
    return request<TopicListResponse>(`/api/platform/self-service/${clusterId}/topics`)
  },
  describeTopic(clusterId: string, topicName: string) {
    return request<TopicDescribeResponse>(`/api/platform/self-service/${clusterId}/topics/describe`, {
      method: 'POST',
      body: JSON.stringify({ topicName }),
    })
  },
  createTopic(clusterId: string, payload: { topicName: string; numPartitions: number; replicationFactor: number; configs?: Record<string, string> }) {
    return request<CreateTopicResponse>(`/api/platform/self-service/${clusterId}/topics/create`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  deleteTopic(clusterId: string, topicName: string) {
    return request<DeleteTopicResponse>(`/api/platform/self-service/${clusterId}/topics/${encodeURIComponent(topicName)}`, {
      method: 'DELETE',
    })
  },
  purgeTopic(clusterId: string, topicName: string) {
    return request<TopicPurgeResponse>(`/api/platform/self-service/${clusterId}/topics/purge`, {
      method: 'POST',
      body: JSON.stringify({ topicName }),
    })
  },
  increasePartitions(clusterId: string, topicName: string, newPartitionCount: number) {
    return request<IncreasePartitionsResponse>(`/api/platform/self-service/${clusterId}/topics/increase-partitions`, {
      method: 'POST',
      body: JSON.stringify({ topicName, newPartitionCount }),
    })
  },
  getMessageCount(clusterId: string, topicName: string) {
    return request<MessageCountResponse>(`/api/platform/self-service/${clusterId}/topics/message-count`, {
      method: 'POST',
      body: JSON.stringify({ topicName }),
    })
  },
  describeTopicConfig(clusterId: string, topicName: string) {
    return request<TopicConfigDescribeResponse>(`/api/platform/self-service/${clusterId}/topics/config/describe`, {
      method: 'POST',
      body: JSON.stringify({ topicName }),
    })
  },
  alterTopicConfig(clusterId: string, topicName: string, configsToSet: Record<string, string>, configsToDelete: string[]) {
    return request<TopicConfigAlterResponse>(`/api/platform/self-service/${clusterId}/topics/config/alter`, {
      method: 'POST',
      body: JSON.stringify({ topicName, configsToSet, configsToDelete }),
    })
  },
  dumpTopicMessages(clusterId: string, topicName: string, maxMessages: number, partition?: number) {
    return request<TopicDataDumpResponse>(`/api/platform/self-service/${clusterId}/topics/data-dump`, {
      method: 'POST',
      body: JSON.stringify({ topicName, maxMessages, partition: partition ?? null }),
    })
  },
  listAcls(clusterId: string) {
    return request<AclListResponse>(`/api/platform/self-service/${clusterId}/acls`)
  },
  describeAcls(clusterId: string, principal?: string, resourceName?: string, resourceType?: string) {
    return request<AclListResponse>(`/api/platform/self-service/${clusterId}/acls/describe`, {
      method: 'POST',
      body: JSON.stringify({ principal, resourceName, resourceType }),
    })
  },
  grantAcl(clusterId: string, payload: { principal: string; resourceName: string; resourceType: string; patternType: string; operation: string; permission?: string }) {
    return request<AclOperationResponse>(`/api/platform/self-service/${clusterId}/acls/grant`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  removeAcl(clusterId: string, payload: { principal: string; resourceName?: string; resourceType?: string; patternType?: string; operation?: string; permission?: string }) {
    return request<AclOperationResponse>(`/api/platform/self-service/${clusterId}/acls/remove`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  listConsumerGroups(clusterId: string) {
    return request<ConsumerGroupListResponse>(`/api/platform/self-service/${clusterId}/consumer-groups`)
  },
  describeConsumerGroup(clusterId: string, groupId: string) {
    return request<ConsumerGroupDescribeResponse>(`/api/platform/self-service/${clusterId}/consumer-groups/describe`, {
      method: 'POST',
      body: JSON.stringify({ groupId }),
    })
  },
  deleteConsumerGroup(clusterId: string, groupId: string) {
    return request<ConsumerGroupDeleteResponse>(`/api/platform/self-service/${clusterId}/consumer-groups/${encodeURIComponent(groupId)}`, {
      method: 'DELETE',
    })
  },
  resetConsumerGroupOffsets(clusterId: string, groupId: string, resetType: string, partitionOffsets?: Record<number, number>) {
    return request<OffsetResetResponse>(`/api/platform/self-service/${clusterId}/consumer-groups/reset-offsets`, {
      method: 'POST',
      body: JSON.stringify({ groupId, resetType, partitionOffsets }),
    })
  },

  // ── Audit ──────────────────────────────────────────────────────────

  getAuditEvents(page: number = 0, size: number = 50, search?: string) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (search) params.set('search', search)
    return request<AuditPageResponse>(`/api/platform/audit?${params}`)
  },

  // ── Schema Registry ─────────────────────────────────────────────

  listSchemaSubjects(clusterId: string) {
    return request<SchemaSubjectListResponse>(`/api/platform/self-service/${clusterId}/schemas/subjects`)
  },
  getSchemaSubjectVersions(clusterId: string, subject: string) {
    return request<SchemaSubjectVersionsResponse>(
      `/api/platform/self-service/${clusterId}/schemas/subjects/${encodeURIComponent(subject)}/versions`,
    )
  },
  getSchemaVersion(clusterId: string, subject: string, version: number) {
    return request<SchemaResponse>(
      `/api/platform/self-service/${clusterId}/schemas/subjects/${encodeURIComponent(subject)}/versions/${version}`,
    )
  },
}
