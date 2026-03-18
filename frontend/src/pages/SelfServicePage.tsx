import { useNavigate, useOutletContext } from 'react-router-dom'
import type { ClusterHealthSummaryResponse } from '../types/api'
import { formatLabel, formatEnvironment } from '../utils/formatters'

interface DashboardContext {
  clusters: ClusterHealthSummaryResponse[]
  filteredClusters: ClusterHealthSummaryResponse[]
  filter: string
  setFilter: (f: string) => void
  serviceAccounts: any[]
  setServiceAccounts: (s: any[]) => void
  setError: (e: string | null) => void
  reloadClusters: () => Promise<void>
}

export default function SelfServicePage() {
  const navigate = useNavigate()
  const { clusters } = useOutletContext<DashboardContext>()

  return (
    <div className="self-service-page">
      <section className="hero">
        <span className="eyebrow">Self-Service</span>
        <h1>Kafka Self-Service Console</h1>
        <p>Select a cluster to run operational tasks</p>
      </section>

      {clusters.length === 0 ? (
        <div className="empty-state">
          <p>No clusters available.</p>
        </div>
      ) : (
        <div className="cluster-grid">
          {clusters.map((cluster) => (
            <div
              key={cluster.clusterId}
              className="cluster-card"
              onClick={() => navigate(`/self-service/${cluster.clusterId}`)}
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault()
                  navigate(`/self-service/${cluster.clusterId}`)
                }
              }}
            >
              <h3 className="cluster-card__name">{cluster.clusterName}</h3>
              <div className="cluster-card__meta">
                <span
                  className={`env-badge env-badge--${cluster.environment.toLowerCase()}`}
                >
                  {formatEnvironment(cluster.environment)}
                </span>
                <span
                  className={`status-pill status-pill--${cluster.status.toLowerCase()}`}
                >
                  {formatLabel(cluster.status)}
                </span>
              </div>
              <p className="cluster-card__summary">{cluster.summaryMessage}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
