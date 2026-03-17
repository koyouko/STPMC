import type { ClusterHealthSummaryResponse, HealthStatus } from '../types/api'
import { formatEnvironment, formatLabel } from '../utils/formatters'

interface ComponentStatusBoardProps {
  clusters: ClusterHealthSummaryResponse[]
  status: Extract<HealthStatus, 'HEALTHY' | 'DEGRADED' | 'DOWN'>
  selectedClusterId: string | null
  focusedComponentKind: string | null
  onSelectComponent: (clusterId: string, componentKind: string) => void
}

export function ComponentStatusBoard({
  clusters,
  status,
  selectedClusterId,
  focusedComponentKind,
  onSelectComponent,
}: ComponentStatusBoardProps) {
  const matchingClusters = clusters
    .map((cluster) => ({
      cluster,
      components: cluster.components.filter((component) => component.status === status),
    }))
    .filter((entry) => entry.components.length > 0)

  if (matchingClusters.length === 0) {
    return (
      <div className="empty-state component-status-empty-state">
        <h3>No {status.toLowerCase()} components</h3>
        <p>The current environment filter does not have any components in this health state.</p>
      </div>
    )
  }

  return (
    <div className="component-status-board">
      {matchingClusters.map(({ cluster, components }) => (
        <article key={cluster.clusterId} className={`status-cluster-group status-cluster-group--${status.toLowerCase()}`}>
          <div className="status-cluster-group__header">
            <div>
              <span className={`env-badge env-badge--${cluster.environment.toLowerCase()}`}>
                {formatEnvironment(cluster.environment)}
              </span>
              <h3>{cluster.clusterName}</h3>
            </div>
            <small>{components.length} matching component(s)</small>
          </div>

          <div className="status-component-list">
            {components.map((component) => {
              const active = selectedClusterId === cluster.clusterId && focusedComponentKind === component.kind

              return (
                <button
                  key={`${cluster.clusterId}-${component.kind}`}
                  type="button"
                  className={`status-component-row ${active ? 'active' : ''}`}
                  onClick={() => onSelectComponent(cluster.clusterId, component.kind)}
                >
                  <div className="status-component-row__body">
                    <strong>{formatLabel(component.kind)}</strong>
                    <small>{component.endpoint ?? 'Not configured'}</small>
                  </div>
                  <span className={`status-pill status-pill--${component.status.toLowerCase()}`}>{component.status}</span>
                </button>
              )
            })}
          </div>
        </article>
      ))}
    </div>
  )
}
