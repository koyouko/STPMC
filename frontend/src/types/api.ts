export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN' | 'NOT_APPLICABLE'
export type ClusterEnvironment = 'PROD' | 'NON_PROD'
export type TokenScope = 'HEALTH_READ' | 'HEALTH_REFRESH' | 'CLUSTER_READ'
export type AuthProfileType = 'PLAINTEXT' | 'MTLS_SSL' | 'SASL_GSSAPI'
export type ServiceEndpointProtocol = 'HTTP' | 'HTTPS' | 'TCP'
export type ComponentKind = 'KAFKA' | 'ZOOKEEPER' | 'SCHEMA_REGISTRY' | 'CONTROL_CENTER' | 'PROMETHEUS' | 'KRAFT' | 'MDS'

export interface ComponentHealthResponse {
  kind: string
  status: HealthStatus
  checkSource: string
  endpoint: string | null
  latencyMs: number | null
  message: string
  version: string | null
  lastCheckedAt: string | null
}

export interface ClusterHealthSummaryResponse {
  clusterId: string
  clusterName: string
  environment: ClusterEnvironment
  connectionMode: string
  status: HealthStatus
  summaryMessage: string
  lastCheckedAt: string | null
  staleAfter: string | null
  components: ComponentHealthResponse[]
}

export interface ClusterHealthDetailResponse extends ClusterHealthSummaryResponse {}

export interface RefreshOperationResponse {
  operationId: string
  status: string
  clusterId: string
  message: string
  requestedAt: string
}

export interface ServiceAccountTokenResponse {
  tokenId: string
  name: string
  tokenPrefix: string
  createdAt: string
  expiresAt: string | null
  lastUsedAt: string | null
  revoked: boolean
  rawToken: string | null
}

export interface ServiceAccountResponse {
  id: string
  name: string
  description: string
  active: boolean
  createdAt: string
  scopes: TokenScope[]
  allowedEnvironments: ClusterEnvironment[]
  allowedClusterIds: string[]
  tokens: ServiceAccountTokenResponse[]
}

export interface CreateServiceAccountRequest {
  name: string
  description: string
  scopes: TokenScope[]
  allowedEnvironments: ClusterEnvironment[]
  allowedClusterIds: string[]
}

export interface CreateServiceAccountTokenRequest {
  name: string
  expiresAt: string | null
}

export interface AuthProfileRequest {
  name: string
  type: AuthProfileType
  securityProtocol: string
  truststorePath?: string
  truststorePasswordFile?: string
  keystorePath?: string
  keystorePasswordFile?: string
  keyPasswordFile?: string
  principal?: string
  keytabPath?: string
  krb5ConfigPath?: string
  saslServiceName?: string
}

export interface ClusterListenerRequest {
  name: string
  host: string
  port: number
  preferred: boolean
  authProfile: AuthProfileRequest
}

export interface ServiceEndpointRequest {
  kind: ComponentKind
  protocol: ServiceEndpointProtocol
  baseUrl?: string
  host?: string
  port?: number
  healthPath?: string
  version?: string
}

export interface CreateClusterRequest {
  name: string
  environment: ClusterEnvironment
  description?: string
  listeners: ClusterListenerRequest[]
  serviceEndpoints?: ServiceEndpointRequest[]
}

export interface UpdateClusterRequest {
  name?: string
  description?: string
  environment?: ClusterEnvironment
  listeners?: ClusterListenerRequest[]
  serviceEndpoints?: ServiceEndpointRequest[]
}

export interface AuthProfileResponse {
  id: string
  name: string
  type: AuthProfileType
  securityProtocol: string
  truststorePath: string | null
  keystorePath: string | null
  principal: string | null
  keytabPath: string | null
  krb5ConfigPath: string | null
  saslServiceName: string | null
}

export interface ClusterListenerResponse {
  id: string
  name: string
  host: string
  port: number
  preferred: boolean
  authProfile: AuthProfileResponse
}

export interface ServiceEndpointResponse {
  id: string
  kind: ComponentKind
  protocol: ServiceEndpointProtocol
  baseUrl: string | null
  host: string | null
  port: number | null
  healthPath: string | null
  version: string | null
  enabled: boolean
}

export interface ClusterConfigResponse {
  clusterId: string
  name: string
  description: string | null
  environment: ClusterEnvironment
  connectionMode: string
  active: boolean
  createdAt: string
  updatedAt: string
  listeners: ClusterListenerResponse[]
  serviceEndpoints: ServiceEndpointResponse[]
}

export interface TestConnectionRequest {
  bootstrapServers: string
  authProfile: AuthProfileRequest
}

export interface TestConnectionResponse {
  success: boolean
  clusterId: string | null
  nodeCount: number
  latencyMs: number
  errorMessage: string | null
}

// ── Self-Service Types ────────────────────────────────────────────

export type SelfServiceTaskType =
  | 'TOPIC_LIST' | 'TOPIC_DESCRIBE' | 'TOPIC_CREATE' | 'TOPIC_DELETE'
  | 'TOPIC_PURGE' | 'TOPIC_INCREASE_PARTITIONS' | 'TOPIC_MESSAGE_COUNT'
  | 'TOPIC_CONFIG_DESCRIBE' | 'TOPIC_CONFIG_ALTER'
  | 'ACL_LIST' | 'ACL_DESCRIBE' | 'ACL_GRANT' | 'ACL_REMOVE'
  | 'CONSUMER_GROUP_LIST' | 'CONSUMER_GROUP_DESCRIBE' | 'CONSUMER_GROUP_DELETE' | 'CONSUMER_GROUP_OFFSETS'
  | 'TOPIC_DATA_DUMP'

export type SelfServiceCategory = 'TOPIC' | 'ACL' | 'CONSUMER_GROUP' | 'DATA'

export interface TaskCatalogEntry {
  taskType: SelfServiceTaskType
  category: SelfServiceCategory
  displayName: string
  description: string
  readOnly: boolean
}

export interface TopicSummary {
  name: string
  internal: boolean
  partitions: number
}

export interface TopicListResponse {
  clusterId: string
  topics: TopicSummary[]
}

export interface TopicPartitionInfo {
  partition: number
  leader: number
  replicas: number[]
  isr: number[]
}

export interface TopicDescribeResponse {
  clusterId: string
  topicName: string
  partitions: number
  replicationFactor: number
  partitionInfos: TopicPartitionInfo[]
  configs: Record<string, string>
}

export interface CreateTopicResponse {
  clusterId: string
  topicName: string
  message: string
}

export interface DeleteTopicResponse {
  clusterId: string
  topicName: string
  message: string
}

export interface TopicPurgeResponse {
  clusterId: string
  topicName: string
  message: string
}

export interface IncreasePartitionsResponse {
  clusterId: string
  topicName: string
  previousCount: number
  newCount: number
}

export interface MessageCountResponse {
  clusterId: string
  topicName: string
  partitionCounts: Record<number, number>
  totalCount: number
}

export interface TopicConfigEntry {
  name: string
  value: string
  source: string
  isDefault: boolean
  isSensitive: boolean
  isReadOnly: boolean
}

export interface TopicConfigDescribeResponse {
  clusterId: string
  topicName: string
  configs: TopicConfigEntry[]
}

export interface TopicConfigAlterResponse {
  clusterId: string
  topicName: string
  message: string
  updatedConfigs: Record<string, string>
}

export interface AclEntry {
  resourceType: string
  resourceName: string
  patternType: string
  principal: string
  host: string
  operation: string
  permission: string
}

export interface AclListResponse {
  clusterId: string
  acls: AclEntry[]
}

export interface AclOperationResponse {
  clusterId: string
  message: string
  affectedCount: number
}

export interface ConsumerGroupSummary {
  groupId: string
  state: string
  type: string
}

export interface ConsumerGroupListResponse {
  clusterId: string
  groups: ConsumerGroupSummary[]
}

export interface TopicPartitionAssignment {
  topic: string
  partition: number
}

export interface ConsumerGroupMemberInfo {
  memberId: string
  clientId: string
  host: string
  assignments: TopicPartitionAssignment[]
}

export interface ConsumerGroupOffsetInfo {
  topic: string
  partition: number
  currentOffset: number
  logEndOffset: number
  lag: number
}

export interface ConsumerGroupDescribeResponse {
  clusterId: string
  groupId: string
  state: string
  coordinator: string
  members: ConsumerGroupMemberInfo[]
  offsets: ConsumerGroupOffsetInfo[]
}

export interface ConsumerGroupDeleteResponse {
  clusterId: string
  groupId: string
  message: string
}

export interface OffsetResetResponse {
  clusterId: string
  groupId: string
  updatedOffsets: Record<string, Record<number, number>>
}

export interface DumpedMessage {
  partition: number
  offset: number
  key: string | null
  value: string | null
  timestamp: number
  headers: Record<string, string>
}

export interface TopicDataDumpResponse {
  clusterId: string
  topicName: string
  messages: DumpedMessage[]
}

// ── Audit Types ──────────────────────────────────────────────────

export interface AuditEventResponse {
  id: string
  actor: string
  action: string
  entityType: string
  entityId: string
  details: string | null
  createdAt: string
}

export interface AuditPageResponse {
  events: AuditEventResponse[]
  totalElements: number
  totalPages: number
  currentPage: number
}

// ── Schema Registry Types ────────────────────────────────────────

export interface SchemaSubjectListResponse {
  clusterId: string
  subjects: string[]
}

export interface SchemaSubjectVersionsResponse {
  clusterId: string
  subject: string
  versions: number[]
}

export interface SchemaResponse {
  clusterId: string
  subject: string
  version: number
  id: number
  schemaType: string
  schema: string
}
