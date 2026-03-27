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

// ── Metrics Types ─────────────────────────────────────────────────

export interface MetricsTargetResponse {
  targetId: string
  host: string
  metricsPort: number
  role: string
  label: string
  enabled: boolean
  createdAt: string
}

/**
 * Metrics scraped from a single Prometheus JMX exporter endpoint (port 9404).
 * Fields are -1 when the target was unreachable or the metric was not present.
 */
export interface BrokerMetricsSample {
  targetId: string
  host: string
  metricsPort: number
  role: string
  label: string
  reachable: boolean
  errorMessage: string | null
  scrapedAt: string
  latencyMs: number
  messagesInPerSec: number
  bytesInPerSec: number
  bytesOutPerSec: number
  underReplicatedPartitions: number
  activeControllerCount: number
  offlinePartitionsCount: number
  brokerState: number
  leaderCount: number
  partitionCount: number
  isrShrinksPerSec: number
  isrExpandsPerSec: number
  requestHandlerIdle: number
  heapUsedBytes: number
  heapMaxBytes: number
}

export interface ClusterMetricsScrapeResponse {
  clusterId: string
  scrapedAt: string
  targets: BrokerMetricsSample[]
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
