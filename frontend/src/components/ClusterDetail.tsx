import type { ClusterHealthDetailResponse } from '../types/api'
import { formatEnvironment, formatLabel, formatTimestamp } from '../utils/formatters'

interface ClusterDetailProps {
  cluster: ClusterHealthDetailResponse | null
  focusedComponentKind: string | null
  refreshing: boolean
  onClearComponentFocus: () => void
  onRefresh: () => void
}

export function ClusterDetail({ cluster, focusedComponentKind, refreshing, onClearComponentFocus, onRefresh }: ClusterDetailProps) {
  if (!cluster) {
    return (
      <section className="detail-panel empty-state">
        <h3>Select a cluster</h3>
        <p>Choose a cluster from the catalog to inspect component health, freshness, and integration readiness.</p>
      </section>
    )
  }

  const focusedComponent = focusedComponentKind
    ? cluster.components.find((component) => component.kind === focusedComponentKind) ?? null
    : null
  const orderedComponents = focusedComponent
    ? [
        focusedComponent,
        ...cluster.components.filter((component) => component.kind !== focusedComponent.kind),
      ]
    : cluster.components

  return (
    <section className="detail-panel">
      <div className="detail-panel__header">
        <div className="detail-panel__intro">
          <span className={`env-badge env-badge--${cluster.environment.toLowerCase()}`}>
            {formatEnvironment(cluster.environment)}
          </span>
          <h2>{cluster.clusterName}</h2>
          <p>{cluster.summaryMessage}</p>
          <div className="detail-panel__meta">
            <span className="meta-pill">
              <strong>Mode</strong>
              {cluster.connectionMode}
            </span>
            <span className="meta-pill">
              <strong>Observed</strong>
              {formatTimestamp(cluster.lastCheckedAt)}
            </span>
            <span className="meta-pill">
              <strong>Components</strong>
              {cluster.components.length}
            </span>
          </div>
        </div>
        <div className="detail-panel__actions">
          <button className="secondary-button" type="button" onClick={onRefresh} disabled={refreshing}>
            {refreshing ? 'Refreshing…' : 'Refresh health'}
          </button>
          <div className={`status-orb status-orb--${cluster.status.toLowerCase()}`}>
            <span>{cluster.status}</span>
            <small>Last checked {formatTimestamp(cluster.lastCheckedAt)}</small>
          </div>
        </div>
      </div>

      {focusedComponent ? (
        <section className={`focused-component focused-component--${focusedComponent.status.toLowerCase()}`}>
          <div className="focused-component__header">
            <div>
              <span className="eyebrow">Focused component</span>
              <h3>{formatLabel(focusedComponent.kind)}</h3>
            </div>
            <button className="ghost-button" type="button" onClick={onClearComponentFocus}>
              Show full cluster
            </button>
          </div>
          <div className="focused-component__meta">
            <code className={`endpoint-chip ${focusedComponent.endpoint ? '' : 'endpoint-chip--muted'}`}>
              {focusedComponent.endpoint ?? 'Not configured'}
            </code>
            <span className={`status-pill status-pill--${focusedComponent.status.toLowerCase()}`}>{focusedComponent.status}</span>
          </div>
          <p>{focusedComponent.message}</p>
        </section>
      ) : null}

      <div className="component-grid">
        {orderedComponents.map((component) => (
          <article
            key={`${component.kind}-${component.endpoint}`}
            className={`component-card ${focusedComponentKind === component.kind ? 'component-card--focused' : ''}`}
          >
            <div className="component-card__header">
              <div className="component-card__identity">
                <span className="component-kind">{formatLabel(component.kind)}</span>
                <strong>{formatLabel(component.kind)}</strong>
                <code className={`endpoint-chip ${component.endpoint ? '' : 'endpoint-chip--muted'}`}>
                  {component.endpoint ?? 'Not configured'}
                </code>
              </div>
              <span className={`status-pill status-pill--${component.status.toLowerCase()}`}>{component.status}</span>
            </div>
            <dl>
              <div>
                <dt>Source</dt>
                <dd>{formatLabel(component.checkSource)}</dd>
              </div>
              <div>
                <dt>Latency</dt>
                <dd>{component.latencyMs != null ? `${component.latencyMs} ms` : 'n/a'}</dd>
              </div>
              <div>
                <dt>Version</dt>
                <dd>{component.version ?? 'Unknown'}</dd>
              </div>
              <div>
                <dt>Observed</dt>
                <dd>{formatTimestamp(component.lastCheckedAt)}</dd>
              </div>
            </dl>
            <p className="component-message">{component.message}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
