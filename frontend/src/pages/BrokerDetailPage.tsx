import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import type { BrokerMetricsSample, DiscoveredCluster } from '../types/api'
import type { DashboardContext } from '../layouts/AppLayout'
import { brokerHealth, brokerStateName, fmtUptime, HEALTH_COLORS } from '../utils/brokerHealth'

function fmtBytes(v: number): string {
  if (v < 0) return '—'
  if (v >= 1_073_741_824) return `${(v / 1_073_741_824).toFixed(1)} GB`
  if (v >= 1_048_576) return `${(v / 1_048_576).toFixed(1)} MB`
  if (v >= 1_024) return `${(v / 1_024).toFixed(1)} KB`
  return `${v.toFixed(0)} B`
}

function fmtRate(v: number, unit: string): string {
  if (v < 0) return '—'
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(2)} M${unit}`
  if (v >= 1_000) return `${(v / 1_000).toFixed(2)} K${unit}`
  return `${v.toFixed(2)} ${unit}`
}

function fmtInt(v: number): string {
  if (v < 0) return '—'
  return Math.round(v).toLocaleString()
}

function fmtPct(v: number): string {
  if (v < 0) return '—'
  return `${(v * 100).toFixed(1)}%`
}

function MetricRow({ label, value, alert }: { label: string; value: string; alert?: boolean }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '0.4rem 0',
        borderBottom: '1px solid var(--color-border, #e5e7eb)',
        gap: '1rem',
      }}
    >
      <span style={{ color: 'var(--color-muted)', fontSize: '0.85rem' }}>{label}</span>
      <span
        style={{
          fontWeight: 600,
          fontSize: '0.9rem',
          color: alert ? 'var(--color-down, #ef4444)' : 'inherit',
          whiteSpace: 'nowrap',
        }}
      >
        {value}
      </span>
    </div>
  )
}

export default function BrokerDetailPage() {
  const { clusterId, targetId } = useParams<{ clusterId: string; targetId: string }>()
  const navigate = useNavigate()
  const { clusters, lastScrape, reloadLastScrape, setError } = useOutletContext<DashboardContext>()
  const [refreshing, setRefreshing] = useState(false)

  useEffect(() => {
    if (lastScrape === undefined) void reloadLastScrape()
  }, [lastScrape, reloadLastScrape])

  const cluster = useMemo(
    () => clusters.find((c) => c.clusterId === clusterId),
    [clusters, clusterId],
  )

  const { broker, group }: { broker: BrokerMetricsSample | null; group: DiscoveredCluster | null } =
    useMemo(() => {
      if (!lastScrape || !cluster) return { broker: null, group: null }
      const g = lastScrape.clusters.find((grp) => grp.clusterName === cluster.clusterName)
      if (!g) return { broker: null, group: null }
      const b = g.brokers.find((br) => br.targetId === targetId) ?? null
      return { broker: b, group: g }
    }, [lastScrape, cluster, targetId])

  async function handleScrape() {
    setRefreshing(true)
    setError(null)
    try {
      await apiClient.scrapeMetrics()
      await reloadLastScrape()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Scrape failed')
    } finally {
      setRefreshing(false)
    }
  }

  if (!cluster) {
    return (
      <section className="detail-panel empty-state">
        <h3>Cluster not found</h3>
        <p>The cluster you are looking for does not exist or was removed.</p>
      </section>
    )
  }

  if (!broker) {
    return (
      <>
        <header className="hero">
          <div className="hero__content">
            <span className="eyebrow">Broker detail</span>
            <h1>No scrape data for this broker yet</h1>
            <p>
              Cluster <strong>{cluster.clusterName}</strong> has no recent scrape containing this
              broker. Upload an inventory CSV on the Inventory page and click Scrape Now, or use
              the button below.
            </p>
          </div>
          <div className="hero__actions">
            <button className="primary-button" type="button" onClick={() => void handleScrape()} disabled={refreshing}>
              {refreshing ? 'Scraping…' : 'Scrape now'}
            </button>
            <button className="secondary-button" type="button" onClick={() => void navigate(`/clusters/${clusterId}`)}>
              Back to cluster
            </button>
          </div>
        </header>
      </>
    )
  }

  const health = brokerHealth(broker)
  const colors = HEALTH_COLORS[health.label]
  const heapPct = broker.heapMaxBytes > 0 ? broker.heapUsedBytes / broker.heapMaxBytes : -1
  const isController = Math.round(broker.activeControllerCount) === 1

  return (
    <>
      <header className="hero">
        <div className="hero__content">
          <span className="eyebrow">
            Broker detail — <strong>{cluster.clusterName}</strong>
          </span>
          <h1 style={{ wordBreak: 'break-all' }}>{broker.host}</h1>
          <p>
            Port {broker.metricsPort} · {broker.role}
            {isController && (
              <span style={{ marginLeft: '0.6rem', color: 'var(--color-accent, #f97316)', fontWeight: 700 }}>
                CONTROLLER
              </span>
            )}
          </p>
        </div>
        <div className="hero__actions">
          <button className="primary-button" type="button" onClick={() => void handleScrape()} disabled={refreshing}>
            {refreshing ? 'Scraping…' : 'Refresh (re-scrape)'}
          </button>
          <button className="secondary-button" type="button" onClick={() => void navigate(`/clusters/${clusterId}`)}>
            Back to cluster
          </button>
        </div>
      </header>

      <section className="panel" style={{ padding: '1.25rem', marginBottom: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
          <div>
            <span className="eyebrow">Status</span>
            <h2 style={{ margin: 0, color: colors.fg }}>{health.label}</h2>
            {health.reasons.length > 0 && (
              <p style={{ marginTop: '0.3rem', color: '#b45309' }}>Issues: {health.reasons.join(', ')}</p>
            )}
            {group && (
              <p style={{ marginTop: '0.3rem', color: 'var(--color-muted)', fontSize: '0.85rem' }}>
                {group.brokers.length} brokers in this cluster · scrape latency {broker.latencyMs} ms
              </p>
            )}
          </div>
          <span
            style={{
              padding: '0.35rem 0.9rem',
              borderRadius: '999px',
              fontSize: '0.8rem',
              fontWeight: 700,
              background: colors.bg,
              color: colors.fg,
              whiteSpace: 'nowrap',
            }}
          >
            {health.label}
          </span>
        </div>
      </section>

      <section className="panel" style={{ padding: '1.25rem' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1.5rem' }}>
          <div>
            <div style={{ fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--color-muted)', marginBottom: '0.3rem' }}>Health</div>
            <MetricRow
              label="Broker state"
              value={brokerStateName(broker.brokerState)}
              alert={broker.brokerState >= 0 && Math.round(broker.brokerState) !== 3}
            />
            <MetricRow
              label="Under-replicated partitions"
              value={fmtInt(broker.underReplicatedPartitions)}
              alert={broker.underReplicatedPartitions > 0}
            />
            <MetricRow
              label="Offline partitions"
              value={fmtInt(broker.offlinePartitionsCount)}
              alert={broker.offlinePartitionsCount > 0}
            />
            <MetricRow label="Uptime" value={fmtUptime(broker.uptimeSeconds)} />
            <MetricRow label="ISR shrinks/sec" value={fmtRate(broker.isrShrinksPerSec, '/s')} alert={broker.isrShrinksPerSec > 0} />
            <MetricRow label="ISR expands/sec" value={fmtRate(broker.isrExpandsPerSec, '/s')} />
          </div>

          <div>
            <div style={{ fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--color-muted)', marginBottom: '0.3rem' }}>Throughput</div>
            <MetricRow label="Messages in/sec" value={fmtRate(broker.messagesInPerSec, 'msg/s')} />
            <MetricRow label="Bytes in/sec" value={fmtBytes(broker.bytesInPerSec) + '/s'} />
            <MetricRow label="Bytes out/sec" value={fmtBytes(broker.bytesOutPerSec) + '/s'} />
          </div>

          <div>
            <div style={{ fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--color-muted)', marginBottom: '0.3rem' }}>Capacity</div>
            <MetricRow label="Leader partitions" value={fmtInt(broker.leaderCount)} />
            <MetricRow label="Total partitions" value={fmtInt(broker.partitionCount)} />
            <MetricRow
              label="Request handler idle"
              value={fmtPct(broker.requestHandlerIdle)}
              alert={broker.requestHandlerIdle >= 0 && broker.requestHandlerIdle < 0.2}
            />
          </div>

          {broker.heapUsedBytes >= 0 && (
            <div>
              <div style={{ fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--color-muted)', marginBottom: '0.3rem' }}>JVM</div>
              <MetricRow
                label="Heap used"
                value={`${fmtBytes(broker.heapUsedBytes)} / ${fmtBytes(broker.heapMaxBytes)} (${heapPct >= 0 ? (heapPct * 100).toFixed(0) + '%' : '—'})`}
                alert={heapPct > 0.85}
              />
            </div>
          )}
        </div>
      </section>
    </>
  )
}
