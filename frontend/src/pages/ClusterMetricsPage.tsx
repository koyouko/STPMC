import { useEffect, useRef, useState } from 'react'
import { apiClient } from '../api/client'
import type { BrokerMetricsSample, DiscoveredCluster, MetricsScrapeResponse } from '../types/api'
import { useOutletContext } from 'react-router-dom'
import { brokerHealth, brokerStateName, fmtElapsedSince, fmtInterval, fmtUptime, HEALTH_COLORS, rollupCluster } from '../utils/brokerHealth'
import type { DashboardContext } from '../layouts/AppLayout'

// ── Helpers ───────────────────────────────────────────────────────

function fmtRate(v: number, unit: string): string {
  if (v < 0) return '—'
  if (v >= 1_000_000) return `${(v / 1_000_000).toFixed(2)} M${unit}`
  if (v >= 1_000) return `${(v / 1_000).toFixed(2)} K${unit}`
  return `${v.toFixed(2)} ${unit}`
}

function fmtBytes(v: number): string {
  if (v < 0) return '—'
  if (v >= 1_073_741_824) return `${(v / 1_073_741_824).toFixed(1)} GB`
  if (v >= 1_048_576) return `${(v / 1_048_576).toFixed(1)} MB`
  if (v >= 1_024) return `${(v / 1_024).toFixed(1)} KB`
  return `${v.toFixed(0)} B`
}

function fmtInt(v: number): string {
  if (v < 0) return '—'
  return Math.round(v).toLocaleString()
}

function fmtPct(v: number): string {
  if (v < 0) return '—'
  return `${(v * 100).toFixed(1)}%`
}

// brokerStateName, brokerHealth, fmtUptime, rollupCluster, HEALTH_COLORS are
// imported from ../utils/brokerHealth for reuse across sidebar / cluster
// detail / broker detail pages.

// ── Metric row inside a card ───────────────────────────────────────

function MetricRow({ label, value, alert }: { label: string; value: string; alert?: boolean }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '0.3rem 0',
        borderBottom: '1px solid var(--color-border, #e5e7eb)',
        gap: '1rem',
      }}
    >
      <span style={{ color: 'var(--color-muted)', fontSize: '0.8rem' }}>{label}</span>
      <span
        style={{
          fontWeight: 600,
          fontSize: '0.85rem',
          color: alert ? 'var(--color-down, #ef4444)' : 'inherit',
          whiteSpace: 'nowrap',
        }}
      >
        {value}
      </span>
    </div>
  )
}

// ── Single broker card ────────────────────────────────────────────

function BrokerMetricsCard({ sample }: { sample: BrokerMetricsSample }) {
  const heapPct = sample.heapMaxBytes > 0 ? sample.heapUsedBytes / sample.heapMaxBytes : -1
  const isController = sample.activeControllerCount === 1
  const health = brokerHealth(sample)
  const { bg: badgeBg, fg: badgeFg } = HEALTH_COLORS[health.label]

  return (
    <div
      className="panel"
      style={{ padding: '1rem', opacity: sample.reachable ? 1 : 0.6 }}
    >
      {/* Card header — hostname is primary, role/controller is the eyebrow */}
      <div style={{ marginBottom: '0.75rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem' }}>
          <div style={{ minWidth: 0 }}>
            <span
              style={{
                fontSize: '0.7rem',
                fontWeight: 700,
                letterSpacing: '0.08em',
                textTransform: 'uppercase',
                color: 'var(--color-muted)',
              }}
            >
              {sample.role}
              {isController && (
                <span style={{ marginLeft: '0.4rem', color: 'var(--color-accent, #f97316)', fontWeight: 800 }}>
                  CONTROLLER
                </span>
              )}
            </span>
            <div style={{ fontWeight: 700, fontSize: '0.95rem', marginTop: '0.1rem', wordBreak: 'break-all' }}>
              {sample.host}
            </div>
            <div style={{ color: 'var(--color-muted)', fontSize: '0.75rem' }}>
              port {sample.metricsPort} · {sample.reachable ? `${sample.latencyMs} ms scrape` : 'scrape failed'}
            </div>
          </div>

          {/* Composite health badge */}
          <span
            title={health.reasons.length > 0 ? health.reasons.join(', ') : 'All signals healthy'}
            style={{
              padding: '0.2rem 0.5rem',
              borderRadius: '999px',
              fontSize: '0.7rem',
              fontWeight: 700,
              background: badgeBg,
              color: badgeFg,
              whiteSpace: 'nowrap',
            }}
          >
            {health.label}
          </span>
        </div>

        {!sample.reachable && sample.errorMessage && (
          <div
            style={{
              marginTop: '0.5rem',
              fontSize: '0.75rem',
              color: '#dc2626',
              background: 'rgba(239,68,68,0.08)',
              borderRadius: '4px',
              padding: '0.4rem 0.6rem',
            }}
          >
            {sample.errorMessage}
          </div>
        )}

        {health.label === 'Degraded' && health.reasons.length > 0 && (
          <div
            style={{
              marginTop: '0.5rem',
              fontSize: '0.75rem',
              color: '#b45309',
              background: 'rgba(234,179,8,0.08)',
              borderRadius: '4px',
              padding: '0.4rem 0.6rem',
            }}
          >
            Issues: {health.reasons.join(', ')}
          </div>
        )}
      </div>

      {sample.reachable && (
        <>
          {/* Throughput */}
          <div style={{ marginBottom: '0.25rem', fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--color-muted)', letterSpacing: '0.06em' }}>Throughput</div>
          <MetricRow label="Messages in/sec" value={fmtRate(sample.messagesInPerSec, 'msg/s')} />
          <MetricRow label="Bytes in/sec" value={fmtBytes(sample.bytesInPerSec) + '/s'} />
          <MetricRow label="Bytes out/sec" value={fmtBytes(sample.bytesOutPerSec) + '/s'} />

          {/* Health */}
          <div style={{ marginTop: '0.6rem', marginBottom: '0.25rem', fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--color-muted)', letterSpacing: '0.06em' }}>Health</div>
          <MetricRow
            label="Broker state"
            value={brokerStateName(sample.brokerState)}
            alert={sample.brokerState >= 0 && Math.round(sample.brokerState) !== 3}
          />
          <MetricRow
            label="Under-replicated partitions"
            value={fmtInt(sample.underReplicatedPartitions)}
            alert={sample.underReplicatedPartitions > 0}
          />
          <MetricRow
            label="Offline partitions"
            value={fmtInt(sample.offlinePartitionsCount)}
            alert={sample.offlinePartitionsCount > 0}
          />
          <MetricRow label="Uptime" value={fmtUptime(sample.uptimeSeconds)} />
          <MetricRow
            label="ISR shrinks/sec"
            value={fmtRate(sample.isrShrinksPerSec, '/s')}
            alert={sample.isrShrinksPerSec > 0}
          />

          {/* Capacity */}
          <div style={{ marginTop: '0.6rem', marginBottom: '0.25rem', fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--color-muted)', letterSpacing: '0.06em' }}>Capacity</div>
          <MetricRow label="Leader partitions" value={fmtInt(sample.leaderCount)} />
          <MetricRow label="Total partitions" value={fmtInt(sample.partitionCount)} />
          <MetricRow
            label="Request handler idle"
            value={fmtPct(sample.requestHandlerIdle)}
            alert={sample.requestHandlerIdle >= 0 && sample.requestHandlerIdle < 0.2}
          />

          {/* JVM */}
          {sample.heapUsedBytes >= 0 && (
            <>
              <div style={{ marginTop: '0.6rem', marginBottom: '0.25rem', fontSize: '0.7rem', fontWeight: 700, textTransform: 'uppercase', color: 'var(--color-muted)', letterSpacing: '0.06em' }}>JVM</div>
              <MetricRow
                label="Heap used"
                value={`${fmtBytes(sample.heapUsedBytes)} / ${fmtBytes(sample.heapMaxBytes)} (${heapPct >= 0 ? (heapPct * 100).toFixed(0) + '%' : '—'})`}
                alert={heapPct > 0.85}
              />
            </>
          )}
        </>
      )}
    </div>
  )
}

// ── Cluster group section ─────────────────────────────────────────

function ClusterSection({ group }: { group: DiscoveredCluster }) {
  const rollup = rollupCluster(group)
  const { total, reachable, healthy, totalUrp, totalOffline, controllerHost } = rollup

  return (
    <section style={{ marginBottom: '2rem' }}>
      <div style={{ marginBottom: '0.75rem', display: 'flex', alignItems: 'baseline', gap: '1.5rem', flexWrap: 'wrap' }}>
        <div>
          <span className="eyebrow">{group.clusterName ? 'Cluster' : 'Unnamed / Unreachable'}</span>
          <h2 style={{ margin: 0, fontSize: '1rem', letterSpacing: '-0.01em' }}>
            {group.clusterName ?? '—'}
          </h2>
          {group.clusterId && (
            <div style={{ color: 'var(--color-muted)', fontSize: '0.75rem', fontFamily: 'monospace', marginTop: '0.15rem' }}>
              JMX ID: {group.clusterId}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', gap: '1.25rem', flexWrap: 'wrap', fontSize: '0.8rem', color: 'var(--color-muted)' }}>
          <span>
            <strong style={{ color: healthy === total ? '#16a34a' : '#b45309' }}>{healthy}</strong>
            {' / '}{total} healthy
          </span>
          <span>{reachable} / {total} reachable</span>
          <span style={{ color: Math.round(totalUrp) > 0 ? '#dc2626' : 'inherit' }}>URP {Math.round(totalUrp)}</span>
          <span style={{ color: Math.round(totalOffline) > 0 ? '#dc2626' : 'inherit' }}>Offline {Math.round(totalOffline)}</span>
          {controllerHost && (
            <span>Controller: <code style={{ fontFamily: 'monospace' }}>{controllerHost}</code></span>
          )}
        </div>
      </div>

      <div
        className="content-grid"
        style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '1rem' }}
      >
        {group.brokers.map((sample) => (
          <BrokerMetricsCard key={sample.targetId} sample={sample} />
        ))}
      </div>
    </section>
  )
}

// ── Main page ─────────────────────────────────────────────────────

const PAGE_SIZE_OPTIONS = [20, 50, 100] as const
const DEFAULT_PAGE_SIZE = 50

export default function MetricsPage() {
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [targets, setTargets] = useState<import('../types/api').MetricsTargetResponse[]>([])
  const [scrapeResult, setScrapeResult] = useState<MetricsScrapeResponse | null>(null)
  const [uploading, setUploading] = useState(false)
  const [scraping, setScraping] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState<number>(DEFAULT_PAGE_SIZE)
  const { scrapeIntervalMs } = useOutletContext<DashboardContext>()

  useEffect(() => {
    void loadTargets()
  }, [])

  async function loadTargets() {
    try {
      const result = await apiClient.listMetricsTargets()
      setTargets(result)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load targets')
    }
  }

  async function handleFileUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    setError(null)
    try {
      const result = await apiClient.uploadMetricsInventory(file)
      setTargets(result)
      setScrapeResult(null)
      setCurrentPage(1)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed')
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  async function handleDeleteTarget(targetId: string) {
    try {
      await apiClient.deleteMetricsTarget(targetId)
      setTargets((prev) => prev.filter((t) => t.targetId !== targetId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  async function handleScrape() {
    setScraping(true)
    setError(null)
    try {
      const result = await apiClient.scrapeMetrics()
      setScrapeResult(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Scrape failed')
    } finally {
      setScraping(false)
    }
  }

  const totalBrokers = scrapeResult?.clusters.reduce((acc, c) => acc + c.brokers.length, 0) ?? 0
  const reachableBrokers = scrapeResult?.clusters.reduce(
    (acc, c) => acc + c.brokers.filter((b) => b.reachable).length,
    0,
  ) ?? 0
  const discoveredClusterCount = scrapeResult?.clusters.filter((c) => c.clusterName !== null).length ?? 0

  // Sort once per render; slicing into pages is O(pageSize) downstream.
  const sortedTargets = [...targets].sort((a, b) =>
    (a.clusterName ?? '').localeCompare(b.clusterName ?? ''),
  )
  const totalPages = Math.max(1, Math.ceil(sortedTargets.length / pageSize))
  // Clamp if a Remove left the current page out of range.
  const safePage = Math.min(currentPage, totalPages)
  const pageStart = (safePage - 1) * pageSize
  const pageTargets = sortedTargets.slice(pageStart, pageStart + pageSize)
  useEffect(() => {
    if (currentPage !== safePage) setCurrentPage(safePage)
  }, [currentPage, safePage])

  return (
    <>
      <header className="hero">
        <div className="hero__content">
          <span className="eyebrow">Inventory</span>
          <h1>Inventory</h1>
          <p>
            Upload a broker inventory CSV, then click <strong>Scrape Now</strong>. Brokers are
            grouped by the <code>clusterName</code> column in the CSV. If the JMX exporter
            exposes <code>kafka_server_KafkaServer_ClusterId</code>, the discovered ID is shown
            alongside the name as a secondary identifier.
          </p>
        </div>
        <div className="hero__actions">
          <button
            className="primary-button"
            type="button"
            onClick={() => void handleScrape()}
            disabled={scraping || targets.length === 0}
          >
            {scraping ? 'Scraping…' : 'Scrape Now'}
          </button>
        </div>
      </header>

      {error && <div className="error-banner">{error}</div>}

      {/* ── Inventory section ───────────────────────────────────── */}
      <section className="panel" style={{ marginBottom: '1.5rem' }}>
        <div className="panel__header">
          <div>
            <span className="eyebrow">Inventory</span>
            <h2>Broker targets ({targets.length})</h2>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,.txt"
              style={{ display: 'none' }}
              onChange={handleFileUpload}
              disabled={uploading}
            />
            <button
              className="secondary-button"
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
            >
              {uploading ? 'Uploading…' : 'Upload CSV'}
            </button>
          </div>
        </div>

        <div style={{ background: 'var(--color-surface, #f8f5f0)', border: '1px solid var(--color-border, #e0dbd4)', borderRadius: 8, padding: '12px 16px', marginBottom: 16, fontSize: '0.85rem', color: 'var(--color-muted, #78716c)' }}>
          <strong style={{ color: 'var(--color-text, #1c1917)' }}>CSV format:</strong>{' '}
          <code>clusterName, host, port, role, environment</code>
          <br />
          <span style={{ fontSize: '0.82rem' }}>
            <code>clusterName</code> and <code>host</code> are required. Port defaults to 9404, role to BROKER, environment to NON_PROD. Lines starting with <code>#</code> are comments.
          </span>
          <pre style={{ margin: '8px 0 0', padding: '8px 12px', background: 'var(--color-bg, #faf8f5)', borderRadius: 6, fontSize: '0.8rem', lineHeight: 1.5, overflowX: 'auto' }}>{`# clusterName, host, port, role, environment
Prod East,broker1.internal,4000,BROKER,PROD
Prod East,broker2.internal,4000,BROKER,PROD
Dev Cluster,localhost,4000,BROKER,NON_PROD`}</pre>
        </div>

        {targets.length === 0 ? (
          <div className="loading-card" style={{ textAlign: 'center' }}>
            <strong>No targets configured.</strong> Upload a CSV to get started.
          </div>
        ) : (
          <table className="data-table" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>Cluster Name</th>
                <th>Cluster ID</th>
                <th>Host</th>
                <th>Port</th>
                <th>Role</th>
                <th>Environment</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {pageTargets.map((t) => (
                <tr key={t.targetId}>
                  <td>{t.clusterName ?? <span style={{ color: 'var(--color-muted)', fontStyle: 'italic' }}>—</span>}</td>
                  <td>
                    {t.discoveredClusterId
                      ? <code style={{ fontSize: '0.78rem' }}>{t.discoveredClusterId}</code>
                      : <span style={{ color: 'var(--color-muted)', fontStyle: 'italic' }}>—</span>}
                  </td>
                  <td>
                    <code>{t.host}</code>
                  </td>
                  <td>{t.metricsPort}</td>
                  <td>{t.role}</td>
                  <td>
                    <span className={`env-badge env-badge--${(t.environment ?? 'non_prod').toLowerCase().replaceAll('_', '-')}`}>
                      {t.environment ?? 'Non-Prod'}
                    </span>
                  </td>
                  <td style={{ textAlign: 'right' }}>
                    <button
                      type="button"
                      className="secondary-button"
                      style={{ padding: '0.2rem 0.6rem', fontSize: '0.75rem' }}
                      onClick={() => void handleDeleteTarget(t.targetId)}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {targets.length > 0 && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '1rem',
              marginTop: '0.75rem',
              flexWrap: 'wrap',
              fontSize: '0.85rem',
              color: 'var(--color-muted)',
            }}
          >
            <div>
              Showing <strong>{sortedTargets.length === 0 ? 0 : pageStart + 1}</strong>–
              <strong>{pageStart + pageTargets.length}</strong> of{' '}
              <strong>{sortedTargets.length}</strong>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              <label>
                Per page:{' '}
                <select
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value))
                    setCurrentPage(1)
                  }}
                  style={{ padding: '0.2rem 0.4rem' }}
                >
                  {PAGE_SIZE_OPTIONS.map((n) => (
                    <option key={n} value={n}>
                      {n}
                    </option>
                  ))}
                </select>
              </label>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ padding: '0.2rem 0.6rem', fontSize: '0.78rem' }}
                  onClick={() => setCurrentPage(1)}
                  disabled={safePage <= 1}
                >
                  « First
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ padding: '0.2rem 0.6rem', fontSize: '0.78rem' }}
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  disabled={safePage <= 1}
                >
                  ‹ Prev
                </button>
                <span style={{ minWidth: '5rem', textAlign: 'center' }}>
                  Page <strong>{safePage}</strong> / {totalPages}
                </span>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ padding: '0.2rem 0.6rem', fontSize: '0.78rem' }}
                  onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                  disabled={safePage >= totalPages}
                >
                  Next ›
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ padding: '0.2rem 0.6rem', fontSize: '0.78rem' }}
                  onClick={() => setCurrentPage(totalPages)}
                  disabled={safePage >= totalPages}
                >
                  Last »
                </button>
              </div>
            </div>
          </div>
        )}
      </section>

      {/* ── Scrape results ──────────────────────────────────────── */}
      {scrapeResult && (
        <section>
          <div style={{ marginBottom: '1.25rem', display: 'flex', alignItems: 'baseline', gap: '1.5rem', flexWrap: 'wrap' }}>
            <div>
              <span className="eyebrow">Scrape results</span>
              <h2 style={{ margin: 0 }}>
                {discoveredClusterCount} cluster{discoveredClusterCount !== 1 ? 's' : ''} · {reachableBrokers} / {totalBrokers} brokers reachable
              </h2>
            </div>
            <small style={{ color: 'var(--color-muted)' }}>
              at {new Date(scrapeResult.scrapedAt).toLocaleTimeString()} · {fmtElapsedSince(scrapeResult.scrapedAt)}
              {scrapeIntervalMs > 0 && <> · auto-refresh every {fmtInterval(scrapeIntervalMs)}</>}
            </small>
          </div>

          {scrapeResult.clusters.map((group, i) => (
            <ClusterSection key={group.clusterName ?? group.clusterId ?? `unknown-${i}`} group={group} />
          ))}
        </section>
      )}
    </>
  )
}
