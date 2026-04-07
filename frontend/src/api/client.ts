import type {
  AuditPageResponse,
  BrokerMetricsSample,
  ClusterConfigResponse,
  ClusterHealthDetailResponse,
  ClusterHealthSummaryResponse,
  CreateClusterRequest,
  CreateServiceAccountRequest,
  CreateServiceAccountTokenRequest,
  MetricsTargetResponse,
  MetricsScrapeResponse,
  RefreshOperationResponse,
  ServiceAccountResponse,
  ServiceAccountTokenResponse,
  TestConnectionRequest,
  TestConnectionResponse,
  UpdateClusterRequest,
} from '../types/api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const defaultHeaders: Record<string, string> = {
  'Content-Type': 'application/json',
  // Dev auth headers are only sent during local development.
  // In production (SAML), authentication is handled by the IdP — these headers are ignored.
  ...(import.meta.env.DEV
    ? {
        'X-MC-User': 'frontend-operator',
      }
    : {}),
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const isFormData = init?.body instanceof FormData
  const headers: Record<string, string> = {
    ...defaultHeaders,
    ...(init?.headers as Record<string, string> ?? {}),
  }
  // Let the browser set Content-Type with boundary for FormData
  if (isFormData) delete headers['Content-Type']

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || `Request failed with ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  if (!text) return undefined as T
  try {
    return JSON.parse(text) as T
  } catch {
    throw new Error(`Expected JSON response but received unexpected format`)
  }
}

export const apiClient = {
  // ── Clusters ──────────────────────────────────────────────────────

  listClusters() {
    return request<ClusterHealthSummaryResponse[]>('/api/platform/clusters')
  },
  getClusterHealth(clusterId: string) {
    return request<ClusterHealthDetailResponse>(`/api/platform/clusters/${encodeURIComponent(clusterId)}/health`)
  },
  refreshClusterHealth(clusterId: string) {
    return request<RefreshOperationResponse>(`/api/platform/clusters/${encodeURIComponent(clusterId)}/health/refresh`, {
      method: 'POST',
    })
  },
  getClusterConfig(clusterId: string) {
    return request<ClusterConfigResponse>(`/api/platform/clusters/${encodeURIComponent(clusterId)}/config`)
  },
  createCluster(payload: CreateClusterRequest) {
    return request<ClusterHealthDetailResponse>('/api/platform/clusters', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },
  updateCluster(clusterId: string, payload: UpdateClusterRequest) {
    return request<ClusterHealthDetailResponse>(`/api/platform/clusters/${encodeURIComponent(clusterId)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },
  deleteCluster(clusterId: string) {
    return request<void>(`/api/platform/clusters/${encodeURIComponent(clusterId)}`, {
      method: 'DELETE',
    })
  },
  testConnection(payload: TestConnectionRequest) {
    return request<TestConnectionResponse>('/api/platform/clusters/test-connection', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  // ── Service Accounts ──────────────────────────────────────────────

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
    return request<ServiceAccountTokenResponse>(`/api/admin/service-accounts/${encodeURIComponent(serviceAccountId)}/tokens`, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  // ── Metrics ───────────────────────────────────────────────────────

  /**
   * Upload a CSV inventory file to replace the entire global metrics target list.
   * Format: clusterName, host, port (optional, default 9404), role (optional, default BROKER), environment (optional, e.g. DEV/SIT/UAT/PTE/PROD, default NON_PROD)
   */
  uploadMetricsInventory(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    // Omit Content-Type to let the browser set multipart/form-data with boundary
    const headers: Record<string, string> = import.meta.env.DEV
      ? { 'X-MC-User': 'frontend-operator' }
      : {}
    return request<MetricsTargetResponse[]>(
      '/api/platform/metrics/targets/upload',
      { method: 'POST', body: formData, headers },
    )
  },

  listMetricsTargets() {
    return request<MetricsTargetResponse[]>('/api/platform/metrics/targets')
  },

  deleteMetricsTarget(targetId: string) {
    return request<void>(`/api/platform/metrics/targets/${encodeURIComponent(targetId)}`, {
      method: 'DELETE',
    })
  },

  /**
   * Trigger an on-demand scrape of all configured JMX targets. Brokers are auto-grouped
   * by the cluster ID discovered from the kafka_server_KafkaServer_ClusterId JMX metric.
   */
  scrapeMetrics() {
    return request<MetricsScrapeResponse>('/api/platform/metrics/scrape')
  },

  // ── Audit ──────────────────────────────────────────────────────────

  getAuditEvents(page: number = 0, size: number = 50, search?: string) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (search) params.set('search', search)
    return request<AuditPageResponse>(`/api/platform/audit?${params}`)
  },
}

// Re-export metrics types so pages don't need a separate import
export type { BrokerMetricsSample, MetricsScrapeResponse }
