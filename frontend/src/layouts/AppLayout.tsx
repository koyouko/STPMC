import { useEffect, useMemo, useState } from 'react'
import { Outlet, useNavigate, useParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import { SidebarTree } from '../components/SidebarTree'
import type {
  ClusterEnvironment,
  ClusterHealthSummaryResponse,
  MetricsScrapeResponse,
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
  /** Latest scrape snapshot (from /last-scrape), used by sidebar + cluster pages. */
  lastScrape: MetricsScrapeResponse | undefined
  /** Background auto-scrape interval in ms (0 = disabled). */
  scrapeIntervalMs: number
  setFilter: (filter: FilterMode) => void
  setServiceAccounts: (accounts: ServiceAccountResponse[]) => void
  setError: (error: string | null) => void
  reloadClusters: () => Promise<void>
  reloadLastScrape: () => Promise<void>
}

export function AppLayout() {
  const navigate = useNavigate()
  const params = useParams()
  const [clusters, setClusters] = useState<ClusterHealthSummaryResponse[]>([])
  const [serviceAccounts, setServiceAccounts] = useState<ServiceAccountResponse[]>([])
  const [filter, setFilter] = useState<FilterMode>('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [lastScrape, setLastScrape] = useState<MetricsScrapeResponse | undefined>(undefined)
  const [scrapeIntervalMs, setScrapeIntervalMs] = useState<number>(0)

  const selectedClusterId = params.clusterId ?? null

  async function loadDashboard() {
    setLoading(true)
    setError(null)
    try {
      const [clusterResponse, accountResponse, scrapeResponse, metricsConfig] = await Promise.all([
        apiClient.listClusters(),
        apiClient.listServiceAccounts(),
        apiClient.getLastScrape().catch(() => undefined),
        apiClient.getMetricsConfig().catch(() => ({ scrapeIntervalMs: 0 })),
      ])
      setClusters(clusterResponse)
      setServiceAccounts(accountResponse)
      setLastScrape(scrapeResponse)
      setScrapeIntervalMs(metricsConfig?.scrapeIntervalMs ?? 0)
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

  async function reloadLastScrape() {
    try {
      const scrapeResponse = await apiClient.getLastScrape()
      setLastScrape(scrapeResponse)
    } catch {
      // non-fatal — sidebar will just fall back to cluster-name-only rendering
    }
  }

  useEffect(() => {
    void loadDashboard()
  }, [])

  // Poll /last-scrape + /clusters to keep sidebar, cluster list, and
  // cluster pages fresh. Auto-onboarding can create new clusters on a
  // scheduled scrape, so both lists must refresh. Disabled when interval=0.
  useEffect(() => {
    if (scrapeIntervalMs <= 0) return
    const pollMs = Math.max(5_000, Math.floor(scrapeIntervalMs / 2))
    const id = window.setInterval(() => {
      void reloadLastScrape()
      void reloadClusters()
    }, pollMs)
    return () => window.clearInterval(id)
  }, [scrapeIntervalMs])

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

  function handleSelectBroker(clusterId: string, targetId: string, environment?: ClusterEnvironment) {
    if (environment) setFilter(environment)
    void navigate(`/clusters/${clusterId}/brokers/${targetId}`)
  }

  const dashboardContext: DashboardContext = {
    clusters,
    filteredClusters,
    serviceAccounts,
    filter,
    loading,
    error,
    lastScrape,
    scrapeIntervalMs,
    setFilter,
    setServiceAccounts,
    setError,
    reloadClusters,
    reloadLastScrape,
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
            scrape={lastScrape}
            selectedTargetId={params.targetId ?? null}
            onShowOverview={handleShowOverview}
            onSelectEnvironment={handleSelectEnvironment}
            onSelectCluster={handleSelectCluster}
            onSelectComponent={handleSelectComponent}
            onSelectBroker={handleSelectBroker}
            onMetrics={() => void navigate('/metrics')}
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
