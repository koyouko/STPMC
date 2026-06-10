import type { ClusterHealthSummaryResponse, MetricsScrapeResponse } from '../types/api'
import { formatEnvironment, formatTimestamp } from '../utils/formatters'
import { effectiveClusterStatus } from '../utils/brokerHealth'

interface ClusterListProps {
  clusters: ClusterHealthSummaryResponse[]
  selectedClusterId: string | null
  /** Latest scrape — when present, the pill shows the composite
   *  (HealthService vs scrape-derived) worst-case status. */
  scrape?: MetricsScrapeResponse | undefined
  onSelect: (clusterId: string) => void
}

export function ClusterList({ clusters, selectedClusterId, scrape, onSelect }: ClusterListProps) {
  return (
    <div className="cluster-list">
      {clusters.map((cluster) => {
        const status = effectiveClusterStatus(cluster, scrape)
        return (
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
              <span className={`status-pill status-pill--${status.toLowerCase()}`}>{status}</span>
            </div>
            <p>{cluster.summaryMessage}</p>
            <div className="cluster-card__meta">
              <span>{cluster.connectionMode}</span>
              <span>{formatTimestamp(cluster.lastCheckedAt, 'Awaiting first snapshot')}</span>
            </div>
          </button>
        )
      })}
    </div>
  )
}
