import type { ClusterHealthSummaryResponse } from '../types/api'
import { formatEnvironment, formatTimestamp } from '../utils/formatters'

interface ClusterListProps {
  clusters: ClusterHealthSummaryResponse[]
  selectedClusterId: string | null
  onSelect: (clusterId: string) => void
}

export function ClusterList({ clusters, selectedClusterId, onSelect }: ClusterListProps) {
  return (
    <div className="cluster-list">
      {clusters.map((cluster) => (
        <button
          key={cluster.clusterId}
          className={`cluster-card ${selectedClusterId === cluster.clusterId ? 'cluster-card--selected' : ''}`}
          onClick={() => onSelect(cluster.clusterId)}
          type="button"
        >
          <div className="cluster-card__header">
            <div>
              <span className={`env-badge env-badge--${cluster.environment.toLowerCase()}`}>
                {formatEnvironment(cluster.environment)}
              </span>
              <h3>{cluster.clusterName}</h3>
            </div>
            <span className={`status-pill status-pill--${cluster.status.toLowerCase()}`}>{cluster.status}</span>
          </div>
          <p>{cluster.summaryMessage}</p>
          <div className="cluster-card__meta">
            <span>{cluster.connectionMode}</span>
            <span>{formatTimestamp(cluster.lastCheckedAt, 'Awaiting first snapshot')}</span>
          </div>
        </button>
      ))}
    </div>
  )
}
