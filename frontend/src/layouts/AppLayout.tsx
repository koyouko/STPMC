import { useEffect, useMemo, useState } from 'react'
import { Outlet, useNavigate, useParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import { SidebarTree } from '../components/SidebarTree'
import type {
  ClusterEnvironment,
  ClusterHealthSummaryResponse,
  ServiceAccountResponse,
} from '../types/api'

export type FilterMode = 'ALL' | ClusterEnvironment

export interface DashboardContext {
  clusters: ClusterHealthSummaryResponse[]
  filteredClusters: ClusterHealthSummaryResponse[]
  serviceAccounts: ServiceAccountResponse[]
  filter: FilterMode
  loading: boolean
  error: string | null
  setFilter: (filter: FilterMode) => void
  setServiceAccounts: (accounts: ServiceAccountResponse[]) => void
  setError: (error: string | null) => void
  reloadClusters: () => Promise<void>
}

export function AppLayout() {
  const navigate = useNavigate()
  const params = useParams()
  const [clusters, setClusters] = useState<ClusterHealthSummaryResponse[]>([])
  const [serviceAccounts, setServiceAccounts] = useState<ServiceAccountResponse[]>([])
  const [filter, setFilter] = useState<FilterMode>('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const selectedClusterId = params.clusterId ?? null

  async function loadDashboard() {
    setLoading(true)
    setError(null)
    try {
      const [clusterResponse, accountResponse] = await Promise.all([
        apiClient.listClusters(),
        apiClient.listServiceAccounts(),
      ])
      setClusters(clusterResponse)
      setServiceAccounts(accountResponse)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load dashboard')
    } finally {
      setLoading(false)
    }
  }

  async function reloadClusters() {
    try {
      const clusterResponse = await apiClient.listClusters()
      setClusters(clusterResponse)
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to reload clusters')
    }
  }

  useEffect(() => {
    void loadDashboard()
  }, [])

  const filteredClusters = useMemo(() => {
    if (filter === 'ALL') return clusters
    return clusters.filter((cluster) => cluster.environment === filter)
  }, [clusters, filter])

  function handleShowOverview() {
    setFilter('ALL')
    void navigate('/')
  }

  function handleSelectEnvironment(environment: ClusterEnvironment) {
    setFilter(environment)
  }

  function handleSelectCluster(clusterId: string, environment?: ClusterEnvironment) {
    if (environment) setFilter(environment)
    void navigate(`/clusters/${clusterId}`)
  }

  function handleSelectComponent(clusterId: string, _componentKind: string, environment?: ClusterEnvironment) {
    if (environment) setFilter(environment)
    void navigate(`/clusters/${clusterId}`)
  }

  const dashboardContext: DashboardContext = {
    clusters,
    filteredClusters,
    serviceAccounts,
    filter,
    loading,
    error,
    setFilter,
    setServiceAccounts,
    setError,
    reloadClusters,
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div>
          <div className="brand-lockup">
            <span className="brand-mark">STP</span>
            <div className="brand-copy">
              <strong>Kafka Mission Control</strong>
              <small>Direct control plane for Confluent estates</small>
            </div>
          </div>

          <SidebarTree
            clusters={clusters}
            activeEnvironment={filter}
            selectedClusterId={selectedClusterId}
            focusedComponentKind={null}
            onShowOverview={handleShowOverview}
            onSelectEnvironment={handleSelectEnvironment}
            onSelectCluster={handleSelectCluster}
            onSelectComponent={handleSelectComponent}
            onMetrics={(clusterId) => void navigate(`/clusters/${clusterId}/metrics`)}
            onAuditLog={() => void navigate('/audit')}
          />
        </div>

        <section className="sidebar-note">
          <span className="eyebrow">Mission</span>
          <p>Onboard Kafka clusters, observe health, scrape JMX metrics, and expose a stable external health API.</p>
        </section>
      </aside>

      <main className="main-content">
        <Outlet context={dashboardContext} />
      </main>
    </div>
  )
}
