import { useEffect, useMemo, useState } from 'react'
import { apiClient } from './api/client'
import { ComponentStatusBoard } from './components/ComponentStatusBoard'
import { ClusterDetail } from './components/ClusterDetail'
import { ClusterList } from './components/ClusterList'
import { ServiceAccountConsole } from './components/ServiceAccountConsole'
import { SidebarTree } from './components/SidebarTree'
import { StatCard } from './components/StatCard'
import type {
  ClusterEnvironment,
  ClusterHealthDetailResponse,
  ClusterHealthSummaryResponse,
  CreateServiceAccountRequest,
  HealthStatus,
  ServiceAccountResponse,
  ServiceAccountTokenResponse,
} from './types/api'
import { formatLabel } from './utils/formatters'
import './App.css'

type FilterMode = 'ALL' | ClusterEnvironment
type DashboardView = 'OVERVIEW' | Extract<HealthStatus, 'HEALTHY' | 'DEGRADED' | 'DOWN'>

function App() {
  const [clusters, setClusters] = useState<ClusterHealthSummaryResponse[]>([])
  const [serviceAccounts, setServiceAccounts] = useState<ServiceAccountResponse[]>([])
  const [selectedClusterId, setSelectedClusterId] = useState<string | null>(null)
  const [selectedCluster, setSelectedCluster] = useState<ClusterHealthDetailResponse | null>(null)
  const [filter, setFilter] = useState<FilterMode>('ALL')
  const [dashboardView, setDashboardView] = useState<DashboardView>('OVERVIEW')
  const [focusedComponentKind, setFocusedComponentKind] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [refreshingClusterId, setRefreshingClusterId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

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
      const nextClusterId = selectedClusterId ?? clusterResponse[0]?.clusterId ?? null
      setSelectedClusterId(nextClusterId)
      if (nextClusterId) {
        const detail = await apiClient.getClusterHealth(nextClusterId)
        setSelectedCluster(detail)
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Failed to load dashboard')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadDashboard()
  }, [])

  useEffect(() => {
    if (!selectedClusterId) {
      setSelectedCluster(null)
      return
    }
    void apiClient.getClusterHealth(selectedClusterId).then(setSelectedCluster).catch((detailError) => {
      setError(detailError instanceof Error ? detailError.message : 'Failed to load cluster detail')
    })
  }, [selectedClusterId])

  const filteredClusters = useMemo(() => {
    if (filter === 'ALL') return clusters
    return clusters.filter((cluster) => cluster.environment === filter)
  }, [clusters, filter])

  useEffect(() => {
    if (loading) return
    if (filteredClusters.length === 0) {
      setSelectedClusterId(null)
      setSelectedCluster(null)
      return
    }

    const currentClusterVisible = filteredClusters.some((cluster) => cluster.clusterId === selectedClusterId)
    if (!currentClusterVisible) {
      setSelectedClusterId(filteredClusters[0].clusterId)
    }
  }, [filteredClusters, loading, selectedClusterId])

  useEffect(() => {
    if (!focusedComponentKind || !selectedCluster) return
    const componentStillVisible = selectedCluster.components.some((component) => component.kind === focusedComponentKind)
    if (!componentStillVisible) {
      setFocusedComponentKind(null)
    }
  }, [focusedComponentKind, selectedCluster])

  const stats = useMemo(() => {
    const active = filter === 'ALL' ? clusters : filteredClusters
    return {
      totalClusters: active.length,
      healthyComponents: active.flatMap((cluster) => cluster.components).filter((component) => component.status === 'HEALTHY').length,
      degradedComponents: active.flatMap((cluster) => cluster.components).filter((component) => component.status === 'DEGRADED').length,
      downComponents: active.flatMap((cluster) => cluster.components).filter((component) => component.status === 'DOWN').length,
    }
  }, [clusters, filter, filteredClusters])

  const statusPanelCount = useMemo(() => {
    if (dashboardView === 'OVERVIEW') return filteredClusters.length
    return filteredClusters.flatMap((cluster) => cluster.components).filter((component) => component.status === dashboardView).length
  }, [dashboardView, filteredClusters])

  const panelCopy = useMemo(() => {
    if (dashboardView === 'OVERVIEW') {
      return {
        eyebrow: 'Cluster catalog',
        title: 'Environment fleet',
        counter: `${filteredClusters.length} clusters visible`,
      }
    }

    return {
      eyebrow: `${formatLabel(dashboardView)} focus`,
      title: `${formatLabel(dashboardView)} components`,
      counter: `${statusPanelCount} components visible`,
    }
  }, [dashboardView, filteredClusters.length, statusPanelCount])

  async function handleCreateAccount(payload: CreateServiceAccountRequest) {
    await apiClient.createServiceAccount(payload)
    const refreshedAccounts = await apiClient.listServiceAccounts()
    setServiceAccounts(refreshedAccounts)
  }

  async function handleCreateToken(serviceAccountId: string, name: string): Promise<ServiceAccountTokenResponse> {
    const token = await apiClient.createServiceAccountToken(serviceAccountId, {
      name,
      expiresAt: null,
    })
    const refreshedAccounts = await apiClient.listServiceAccounts()
    setServiceAccounts(refreshedAccounts)
    return token
  }

  async function handleRefreshCluster() {
    if (!selectedClusterId) return
    setRefreshingClusterId(selectedClusterId)
    setError(null)
    try {
      await apiClient.refreshClusterHealth(selectedClusterId)
      const [clusterResponse, detail] = await Promise.all([
        apiClient.listClusters(),
        apiClient.getClusterHealth(selectedClusterId),
      ])
      setClusters(clusterResponse)
      setSelectedCluster(detail)
    } catch (refreshError) {
      setError(refreshError instanceof Error ? refreshError.message : 'Failed to refresh cluster health')
    } finally {
      setRefreshingClusterId(null)
    }
  }

  function handleShowOverview() {
    setFilter('ALL')
    setDashboardView('OVERVIEW')
    setFocusedComponentKind(null)
  }

  function handleSelectEnvironment(environment: ClusterEnvironment) {
    setFilter(environment)
  }

  function handleSelectCluster(clusterId: string, environment?: ClusterEnvironment) {
    if (environment) {
      setFilter(environment)
    }
    setSelectedClusterId(clusterId)
    setFocusedComponentKind(null)
  }

  function handleSelectComponent(clusterId: string, componentKind: string, environment?: ClusterEnvironment) {
    if (environment) {
      setFilter(environment)
    }
    setSelectedClusterId(clusterId)
    setFocusedComponentKind(componentKind)
  }

  function handleToggleDashboardView(nextView: DashboardView) {
    setDashboardView((currentView) => (currentView === nextView ? 'OVERVIEW' : nextView))
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
            focusedComponentKind={focusedComponentKind}
            onShowOverview={handleShowOverview}
            onSelectEnvironment={handleSelectEnvironment}
            onSelectCluster={handleSelectCluster}
            onSelectComponent={handleSelectComponent}
          />
        </div>

        <section className="sidebar-note">
          <span className="eyebrow">Mission</span>
          <p>Onboard existing Kafka clusters, observe service health, and expose a stable external health API.</p>
        </section>
      </aside>

      <main className="main-content">
        <header className="hero">
          <div className="hero__content">
            <span className="eyebrow">STP Operations</span>
            <h1>Kafka fleet command surface</h1>
            <p>
              Lenses-inspired operator UX for Confluent clusters with mTLS/Kerberos connectivity, cached health snapshots,
              and tokenized APIs for external systems.
            </p>
          </div>
          <div className="hero__actions">
            <button className="secondary-button" type="button" onClick={() => void loadDashboard()}>
              Refresh dashboard
            </button>
          </div>
        </header>

        <section className="stats-grid">
          <StatCard
            label="Tracked clusters"
            value={stats.totalClusters}
            active={dashboardView === 'OVERVIEW'}
            onClick={() => {
              setDashboardView('OVERVIEW')
              setFocusedComponentKind(null)
            }}
          />
          <StatCard
            label="Healthy components"
            value={stats.healthyComponents}
            tone="healthy"
            active={dashboardView === 'HEALTHY'}
            onClick={() => handleToggleDashboardView('HEALTHY')}
          />
          <StatCard
            label="Degraded components"
            value={stats.degradedComponents}
            tone="degraded"
            active={dashboardView === 'DEGRADED'}
            onClick={() => handleToggleDashboardView('DEGRADED')}
          />
          <StatCard
            label="Down components"
            value={stats.downComponents}
            tone="down"
            active={dashboardView === 'DOWN'}
            onClick={() => handleToggleDashboardView('DOWN')}
          />
        </section>

        {error ? <div className="error-banner">{error}</div> : null}

        {loading ? (
          <div className="loading-card">Loading STP Kafka Mission Control…</div>
        ) : (
          <>
            <section className="content-grid">
              <section className="panel">
                <div className="panel__header">
                  <div>
                    <span className="eyebrow">{panelCopy.eyebrow}</span>
                    <h2>{panelCopy.title}</h2>
                  </div>
                  <small>{panelCopy.counter}</small>
                </div>
                {dashboardView === 'OVERVIEW' ? (
                  <ClusterList
                    clusters={filteredClusters}
                    selectedClusterId={selectedClusterId}
                    onSelect={(clusterId) => handleSelectCluster(clusterId)}
                  />
                ) : (
                  <ComponentStatusBoard
                    clusters={filteredClusters}
                    status={dashboardView}
                    selectedClusterId={selectedClusterId}
                    focusedComponentKind={focusedComponentKind}
                    onSelectComponent={handleSelectComponent}
                  />
                )}
              </section>

              <ClusterDetail
                cluster={selectedCluster}
                focusedComponentKind={focusedComponentKind}
                refreshing={refreshingClusterId === selectedClusterId}
                onClearComponentFocus={() => setFocusedComponentKind(null)}
                onRefresh={handleRefreshCluster}
              />
            </section>

            <ServiceAccountConsole
              accounts={serviceAccounts}
              onCreateAccount={handleCreateAccount}
              onCreateToken={handleCreateToken}
            />
          </>
        )}
      </main>
    </div>
  )
}

export default App
