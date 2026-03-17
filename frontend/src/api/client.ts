import type {
  ClusterHealthDetailResponse,
  ClusterHealthSummaryResponse,
  CreateServiceAccountRequest,
  CreateServiceAccountTokenRequest,
  RefreshOperationResponse,
  ServiceAccountResponse,
  ServiceAccountTokenResponse,
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
}
