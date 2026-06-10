import type {
  ClusterEnvironment,
  ClusterHealthSummaryResponse,
  DiscoveredCluster,
  MetricsScrapeResponse,
} from '../types/api'
import { formatEnvironment, formatLabel } from '../utils/formatters'
import { brokerHealth, HEALTH_COLORS, rollupCluster } from '../utils/brokerHealth'

interface SidebarTreeProps {
  clusters: ClusterHealthSummaryResponse[]
  activeEnvironment: ClusterEnvironment | 'ALL'
  selectedClusterId: string | null
  focusedComponentKind: string | null
  /** Latest scrape snapshot — when present, brokers appear under each cluster as tree leaves. */
  scrape: MetricsScrapeResponse | undefined
  /** Currently focused broker targetId (from the /clusters/:id/brokers/:targetId route). */
  selectedTargetId: string | null
  onShowOverview: () => void
  onSelectEnvironment: (environment: ClusterEnvironment) => void
  onSelectCluster: (clusterId: string, environment: ClusterEnvironment) => void
  onSelectComponent: (clusterId: string, componentKind: string, environment: ClusterEnvironment) => void
  onSelectBroker: (clusterId: string, targetId: string, environment: ClusterEnvironment) => void
  onMetrics?: () => void
  onAuditLog?: () => void
}

/** Match a cluster summary row to its scrape group by name (scrape is keyed by clusterName). */
function findScrapeGroup(
  scrape: MetricsScrapeResponse | undefined,
  clusterName: string,
): DiscoveredCluster | undefined {
  return scrape?.clusters.find((g) => g.clusterName === clusterName)
}

const environments: ClusterEnvironment[] = ['PROD', 'NON_PROD']

export function SidebarTree({
  clusters,
  activeEnvironment,
  selectedClusterId,
  focusedComponentKind,
  scrape,
  selectedTargetId,
  onShowOverview,
  onSelectEnvironment,
  onSelectCluster,
  onSelectComponent,
  onSelectBroker,
  onMetrics,
  onAuditLog,
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


      {onMetrics && (
        <button
          type="button"
          className="sidebar-overview-button"
          onClick={onMetrics}
        >
          <span>Inventory</span>
          <small>Broker telemetry</small>
        </button>
      )}

      {onAuditLog && (
        <button
          type="button"
          className="sidebar-overview-button sidebar-audit-button"
          onClick={onAuditLog}
        >
          <span>Audit Log</span>
          <small>Activity trail</small>
        </button>
      )}

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
                  const scrapeGroup = findScrapeGroup(scrape, cluster.clusterName)
                  const rollup = scrapeGroup ? rollupCluster(scrapeGroup) : null
                  // Prefer scrape-derived overall verdict when we have a snapshot;
                  // fall back to HealthService status (HEALTHY / DEGRADED / DOWN / UNKNOWN).
                  const statusBadge = rollup ? rollup.overall.toUpperCase() : cluster.status
                  const statusClass = rollup ? rollup.overall.toLowerCase() : cluster.status.toLowerCase()

                  return (
                    <div key={cluster.clusterId} className="tree-cluster">
                      <button
                        type="button"
                        className={`tree-cluster__button ${clusterSelected ? 'active' : ''}`}
                        onClick={() => onSelectCluster(cluster.clusterId, environment)}
                      >
                        <span className="tree-cluster__name">{cluster.clusterName}</span>
                        <span className={`tree-cluster__status tree-cluster__status--${statusClass}`}>
                          {statusBadge}
                        </span>
                      </button>

                      {clusterSelected ? (
                        <div className="tree-components">
                          {cluster.components.filter((c) => c.status !== 'NOT_APPLICABLE').map((component) => (
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

                          {/* Broker leaves — one row per broker from the latest scrape */}
                          {scrapeGroup && scrapeGroup.brokers.length > 0 && (
                            <div style={{ marginTop: '0.3rem', display: 'flex', flexDirection: 'column', gap: '0.1rem' }}>
                              <div
                                style={{
                                  fontSize: '0.62rem',
                                  fontWeight: 700,
                                  letterSpacing: '0.08em',
                                  textTransform: 'uppercase',
                                  color: 'var(--color-muted)',
                                  padding: '0.35rem 0.5rem 0.15rem',
                                }}
                              >
                                Brokers {rollup?.healthy ?? 0} / {rollup?.total ?? 0} healthy
                              </div>
                              {scrapeGroup.brokers.map((b) => {
                                const verdict = brokerHealth(b)
                                const isActive = selectedTargetId === b.targetId
                                const dotColor = HEALTH_COLORS[verdict.label].dot
                                const isController = Math.round(b.activeControllerCount) === 1
                                return (
                                  <button
                                    key={b.targetId}
                                    type="button"
                                    className={`tree-component__button ${isActive ? 'active' : ''}`}
                                    onClick={() => onSelectBroker(cluster.clusterId, b.targetId, environment)}
                                    title={verdict.reasons.length > 0 ? verdict.reasons.join(', ') : verdict.label}
                                  >
                                    <span style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', minWidth: 0 }}>
                                      <span
                                        aria-hidden="true"
                                        style={{ width: 8, height: 8, borderRadius: '50%', background: dotColor, flexShrink: 0 }}
                                      />
                                      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {b.host}
                                      </span>
                                      {isController && (
                                        <span
                                          style={{
                                            fontSize: '0.55rem',
                                            fontWeight: 800,
                                            color: 'var(--color-accent, #f97316)',
                                            letterSpacing: '0.08em',
                                          }}
                                        >
                                          CTRL
                                        </span>
                                      )}
                                    </span>
                                  </button>
                                )
                              })}
                            </div>
                          )}
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
