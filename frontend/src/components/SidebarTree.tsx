import type { ClusterEnvironment, ClusterHealthSummaryResponse } from '../types/api'
import { formatEnvironment, formatLabel } from '../utils/formatters'

interface SidebarTreeProps {
  clusters: ClusterHealthSummaryResponse[]
  activeEnvironment: ClusterEnvironment | 'ALL'
  selectedClusterId: string | null
  focusedComponentKind: string | null
  onShowOverview: () => void
  onSelectEnvironment: (environment: ClusterEnvironment) => void
  onSelectCluster: (clusterId: string, environment: ClusterEnvironment) => void
  onSelectComponent: (clusterId: string, componentKind: string, environment: ClusterEnvironment) => void
}

const environments: ClusterEnvironment[] = ['PROD', 'NON_PROD']

export function SidebarTree({
  clusters,
  activeEnvironment,
  selectedClusterId,
  focusedComponentKind,
  onShowOverview,
  onSelectEnvironment,
  onSelectCluster,
  onSelectComponent,
}: SidebarTreeProps) {
  return (
    <div className="sidebar-tree">
      <button
        type="button"
        className={`sidebar-overview-button ${activeEnvironment === 'ALL' ? 'active' : ''}`}
        onClick={onShowOverview}
      >
        <span>Fleet overview</span>
        <small>All clusters</small>
      </button>

      {environments.map((environment) => {
        const environmentClusters = clusters.filter((cluster) => cluster.environment === environment)

        return (
          <section key={environment} className="tree-section">
            <button
              type="button"
              className={`tree-section__header ${activeEnvironment === environment ? 'active' : ''}`}
              onClick={() => onSelectEnvironment(environment)}
            >
              <span>{formatEnvironment(environment)}</span>
              <small>{environmentClusters.length} cluster(s)</small>
            </button>

            <div className="tree-section__body">
              {environmentClusters.length === 0 ? (
                <div className="tree-empty-state">No clusters available</div>
              ) : (
                environmentClusters.map((cluster) => {
                  const clusterSelected = cluster.clusterId === selectedClusterId

                  return (
                    <div key={cluster.clusterId} className="tree-cluster">
                      <button
                        type="button"
                        className={`tree-cluster__button ${clusterSelected ? 'active' : ''}`}
                        onClick={() => onSelectCluster(cluster.clusterId, environment)}
                      >
                        <span className="tree-cluster__name">{cluster.clusterName}</span>
                        <span className={`tree-cluster__status tree-cluster__status--${cluster.status.toLowerCase()}`}>
                          {cluster.status}
                        </span>
                      </button>

                      {clusterSelected ? (
                        <div className="tree-components">
                          {cluster.components.map((component) => (
                            <button
                              key={`${cluster.clusterId}-${component.kind}`}
                              type="button"
                              className={`tree-component__button ${
                                focusedComponentKind === component.kind ? 'active' : ''
                              }`}
                              onClick={() => onSelectComponent(cluster.clusterId, component.kind, environment)}
                            >
                              <span>{formatLabel(component.kind)}</span>
                              <span className={`tree-component__dot tree-component__dot--${component.status.toLowerCase()}`} />
                            </button>
                          ))}
                        </div>
                      ) : null}
                    </div>
                  )
                })
              )}
            </div>
          </section>
        )
      })}
    </div>
  )
}
