import { useEffect, useRef, useState } from 'react'
import { apiClient } from '../api/client'
import type { BrokerMetricsSample, DiscoveredCluster, MetricsScrapeResponse } from '../types/api'

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

function brokerStateName(state: number): string {
  if (state < 0) return '—'
  const names: Record<number, string> = { 0: 'Unknown', 1: 'Starting', 2: 'Recovery', 3: 'Running', 6: 'PendingShutdown', 7: 'Shutdown' }
  return names[Math.round(state)] ?? String(Math.round(state))
}

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

  return (
    <div
      className="panel"
      style={{ padding: '1rem', opacity: sample.reachable ? 1 : 0.6 }}
    >
      {/* Card header */}
      <div style={{ marginBottom: '0.75rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem' }}>
          <div>
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
            <div style={{ fontWeight: 700, fontSize: '0.95rem', marginTop: '0.1rem' }}>{sample.label}</div>
            <div style={{ color: 'var(--color-muted)', fontSize: '0.75rem' }}>
              {sample.host}:{sample.metricsPort}
            </div>
          </div>

          {/* Reachability badge */}
          <span
            style={{
              padding: '0.2rem 0.5rem',
              borderRadius: '999px',
              fontSize: '0.7rem',
              fontWeight: 600,
              background: sample.reachable
                ? 'rgba(34,197,94,0.15)'
                : 'rgba(239,68,68,0.15)',
              color: sample.reachable ? '#16a34a' : '#dc2626',
              whiteSpace: 'nowrap',
            }}
          >
            {sample.reachable ? `${sample.latencyMs} ms` : 'Unreachable'}
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
            label="Under-replicated partitions"
            value={fmtInt(sample.underReplicatedPartitions)}
            alert={sample.underReplicatedPartitions > 0}
          />
          <MetricRow
            label="Offline partitions"
            value={fmtInt(sample.offlinePartitionsCount)}
            alert={sample.offlinePartitionsCount > 0}
          />
          <MetricRow label="Broker state" value={brokerStateName(sample.brokerState)} />
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
  const reachable = group.brokers.filter((b) => b.reachable).length
  const total = group.brokers.length

  return (
    <section style={{ marginBottom: '2rem' }}>
      <div style={{ marginBottom: '0.75rem', display: 'flex', alignItems: 'baseline', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <span className="eyebrow">{group.clusterId ? 'Cluster' : 'Unreachable / Unknown'}</span>
          <h2 style={{ margin: 0, fontSize: '1rem', fontFamily: 'monospace', letterSpacing: '-0.01em' }}>
            {group.clusterId ?? '—'}
          </h2>
        </div>
        <span style={{ color: 'var(--color-muted)', fontSize: '0.85rem' }}>
          {reachable} / {total} brokers reachable
        </span>
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

export default function MetricsPage() {
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [targets, setTargets] = useState<import('../types/api').MetricsTargetResponse[]>([])
  const [scrapeResult, setScrapeResult] = useState<MetricsScrapeResponse | null>(null)
  const [uploading, setUploading] = useState(false)
  const [scraping, setScraping] = useState(false)
  const [error, setError] = useState<string | null>(null)

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
  const discoveredClusterCount = scrapeResult?.clusters.filter((c) => c.clusterId !== null).length ?? 0

  return (
    <>
      <header className="hero">
        <div className="hero__content">
          <span className="eyebrow">Metrics</span>
          <h1>JMX Metrics</h1>
          <p>
            Upload a broker inventory CSV, then click <strong>Scrape Now</strong>. Each broker's
            cluster ID is read automatically from the{' '}
            <code>kafka_server_KafkaServer_ClusterId</code> JMX metric — no manual cluster
            registration required.
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

        {targets.length === 0 ? (
          <div className="loading-card" style={{ textAlign: 'left' }}>
            <strong>No targets configured.</strong>
            <br />
            Upload a CSV file where each line is: <code>host, port, role, label</code>
            <br />
            <span style={{ color: 'var(--color-muted)', fontSize: '0.85rem' }}>
              Port defaults to 9404 · role defaults to BROKER · lines starting with # are comments
            </span>
          </div>
        ) : (
          <table className="data-table" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>Host</th>
                <th>Port</th>
                <th>Role</th>
                <th>Label</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {targets.map((t) => (
                <tr key={t.targetId}>
                  <td>
                    <code>{t.host}</code>
                  </td>
                  <td>{t.metricsPort}</td>
                  <td>{t.role}</td>
                  <td>{t.label}</td>
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
              at {new Date(scrapeResult.scrapedAt).toLocaleTimeString()}
            </small>
          </div>

          {scrapeResult.clusters.map((group, i) => (
            <ClusterSection key={group.clusterId ?? `unknown-${i}`} group={group} />
          ))}
        </section>
      )}
    </>
  )
}
