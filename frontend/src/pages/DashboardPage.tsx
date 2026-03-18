import { useMemo, useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { apiClient } from '../api/client'
import { ClusterList } from '../components/ClusterList'
import { ComponentStatusBoard } from '../components/ComponentStatusBoard'
import { ServiceAccountConsole } from '../components/ServiceAccountConsole'
import { StatCard } from '../components/StatCard'
import type {
  CreateServiceAccountRequest,
  HealthStatus,
  ServiceAccountTokenResponse,
} from '../types/api'
import { formatLabel } from '../utils/formatters'
import type { DashboardContext } from '../layouts/AppLayout'

type DashboardView = 'OVERVIEW' | Extract<HealthStatus, 'HEALTHY' | 'DEGRADED' | 'DOWN'>

export function DashboardPage() {
  const {
    clusters,
    filteredClusters,
    serviceAccounts,
    filter,
    loading,
    error,
    setServiceAccounts,
    reloadClusters,
  } = useOutletContext<DashboardContext>()

  const navigate = useNavigate()
  const [dashboardView, setDashboardView] = useState<DashboardView>('OVERVIEW')
  const [selectedClusterId, setSelectedClusterId] = useState<string | null>(null)
  const [focusedComponentKind, setFocusedComponentKind] = useState<string | null>(null)

  const stats = useMemo(() => {
    const active = filter === 'ALL' ? clusters : filteredClusters
    return {
      totalClusters: active.length,
      healthyClusters: active.filter((c) => c.status === 'HEALTHY').length,
      degradedClusters: active.filter((c) => c.status === 'DEGRADED').length,
      downClusters: active.filter((c) => c.status === 'DOWN').length,
      healthyComponents: active.flatMap((c) => c.components).filter((c) => c.status === 'HEALTHY').length,
      degradedComponents: active.flatMap((c) => c.components).filter((c) => c.status === 'DEGRADED').length,
      downComponents: active.flatMap((c) => c.components).filter((c) => c.status === 'DOWN').length,
    }
  }, [clusters, filter, filteredClusters])

  const statusPanelCount = useMemo(() => {
    if (dashboardView === 'OVERVIEW') return filteredClusters.length
    return filteredClusters.flatMap((c) => c.components).filter((c) => c.status === dashboardView).length
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

  function handleToggleDashboardView(nextView: DashboardView) {
    setDashboardView((current) => (current === nextView ? 'OVERVIEW' : nextView))
  }

  function handleSelectCluster(clusterId: string) {
    setSelectedClusterId(clusterId)
    void navigate(`/clusters/${clusterId}`)
  }

  function handleSelectComponent(clusterId: string, componentKind: string) {
    setSelectedClusterId(clusterId)
    setFocusedComponentKind(componentKind)
    void navigate(`/clusters/${clusterId}`)
  }

  async function handleCreateAccount(payload: CreateServiceAccountRequest) {
    await apiClient.createServiceAccount(payload)
    const refreshedAccounts = await apiClient.listServiceAccounts()
    setServiceAccounts(refreshedAccounts)
  }

  async function handleCreateToken(serviceAccountId: string, name: string): Promise<ServiceAccountTokenResponse> {
    const token = await apiClient.createServiceAccountToken(serviceAccountId, { name, expiresAt: null })
    const refreshedAccounts = await apiClient.listServiceAccounts()
    setServiceAccounts(refreshedAccounts)
    return token
  }

  return (
    <>
      <header className="hero">
        <div className="hero__content">
          <span className="eyebrow">STP Operations</span>
          <h1>Kafka fleet command surface</h1>
          <p>
            Monitor cluster health, onboard new environments, and run self-service Kafka tasks across your fleet.
          </p>
        </div>
        <div className="hero__actions">
          <button className="primary-button" type="button" onClick={() => void navigate('/clusters/new')}>
            Onboard cluster
          </button>
          <button className="secondary-button" type="button" onClick={() => void reloadClusters()}>
            Refresh dashboard
          </button>
        </div>
      </header>

      <section className="stats-grid">
        <StatCard
          label="Tracked clusters"
          value={stats.totalClusters}
          active={dashboardView === 'OVERVIEW'}
          onClick={() => { setDashboardView('OVERVIEW'); setFocusedComponentKind(null) }}
        />
        <StatCard
          label="Healthy clusters"
          value={stats.healthyClusters}
          tone="healthy"
          active={false}
          onClick={() => { setDashboardView('OVERVIEW'); setFocusedComponentKind(null) }}
        />
        <StatCard
          label="Degraded clusters"
          value={stats.degradedClusters}
          tone="degraded"
          active={false}
          onClick={() => { setDashboardView('OVERVIEW'); setFocusedComponentKind(null) }}
        />
        <StatCard
          label="Down clusters"
          value={stats.downClusters}
          tone="down"
          active={false}
          onClick={() => { setDashboardView('OVERVIEW'); setFocusedComponentKind(null) }}
        />
      </section>

      <section className="stats-grid" style={{ marginTop: 0 }}>
        <StatCard
          label="Total components"
          value={stats.healthyComponents + stats.degradedComponents + stats.downComponents}
          active={false}
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
          <section className="content-grid" style={{ gridTemplateColumns: '1fr' }}>
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
                  onSelect={handleSelectCluster}
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
          </section>

          <ServiceAccountConsole
            accounts={serviceAccounts}
            onCreateAccount={handleCreateAccount}
            onCreateToken={handleCreateToken}
          />
        </>
      )}
    </>
  )
}
