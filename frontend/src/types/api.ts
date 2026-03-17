export type HealthStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN' | 'NOT_APPLICABLE'
export type ClusterEnvironment = 'PROD' | 'NON_PROD'
export type TokenScope = 'HEALTH_READ' | 'HEALTH_REFRESH' | 'CLUSTER_READ'

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
