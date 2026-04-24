import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import { ClusterDetail } from '../components/ClusterDetail'
import type { ClusterHealthDetailResponse } from '../types/api'
import type { DashboardContext } from '../layouts/AppLayout'
import { brokerHealth, fmtElapsedSince, fmtInterval, fmtUptime, HEALTH_COLORS, rollupCluster } from '../utils/brokerHealth'

export function ClusterDetailPage() {
  const { clusterId } = useParams<{ clusterId: string }>()
  const { setError, reloadClusters, lastScrape, reloadLastScrape, scrapeIntervalMs } = useOutletContext<DashboardContext>()
  const navigate = useNavigate()
  const [cluster, setCluster] = useState<ClusterHealthDetailResponse | null>(null)
  const [focusedComponentKind, setFocusedComponentKind] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)
  const [scraping, setScraping] = useState(false)

  // Match the scrape group to this cluster by name (scrape groups by clusterName).
  const scrapeGroup = useMemo(
    () => (cluster ? lastScrape?.clusters.find((g) => g.clusterName === cluster.clusterName) : undefined),
    [lastScrape, cluster],
  )
  const rollup = useMemo(() => (scrapeGroup ? rollupCluster(scrapeGroup) : null), [scrapeGroup])

  async function handleScrape() {
    setScraping(true)
    setError(null)
    try {
      await apiClient.scrapeMetrics()
      await reloadLastScrape()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Scrape failed')
    } finally {
      setScraping(false)
    }
  }

  useEffect(() => {
    if (!clusterId) return
    void apiClient.getClusterHealth(clusterId).then(setCluster).catch((err) => {
      setError(err instanceof Error ? err.message : 'Failed to load cluster detail')
    })
  }, [clusterId, setError])

  async function handleRefresh() {
    if (!clusterId) return
    setRefreshing(true)
    setError(null)
    try {
      await apiClient.refreshClusterHealth(clusterId)
      const [detail] = await Promise.all([
        apiClient.getClusterHealth(clusterId),
        reloadClusters(),
      ])
      setCluster(detail)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to refresh cluster health')
    } finally {
      setRefreshing(false)
    }
  }

  return (
    <>
      <header className="hero">
        <div className="hero__content">
          <span className="eyebrow">Cluster detail</span>
          <h1>{cluster?.clusterName ?? 'Loading…'}</h1>
        </div>
        <div className="hero__actions">
          <button className="primary-button" type="button" onClick={() => void navigate(`/clusters/${clusterId}/edit`)}>
            Edit cluster
          </button>
          <button className="secondary-button" type="button" onClick={() => void navigate('/')}>
            Back to fleet
          </button>
        </div>
      </header>

      <ClusterDetail
        cluster={cluster}
        focusedComponentKind={focusedComponentKind}
        refreshing={refreshing}
        onClearComponentFocus={() => setFocusedComponentKind(null)}
        onRefresh={handleRefresh}
      />

      {/* ── Broker inventory (from latest scrape snapshot) ─────────────────── */}
      {cluster && (
        <section className="panel" style={{ marginTop: '1.5rem', padding: '1.25rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1rem', flexWrap: 'wrap' }}>
            <div>
              <span className="eyebrow">Broker inventory</span>
              <h2 style={{ margin: 0 }}>
                {rollup
                  ? (
                    <>
                      <span style={{ color: HEALTH_COLORS[rollup.overall].fg }}>{rollup.overall.toUpperCase()}</span>
                      <span style={{ marginLeft: '0.75rem', color: 'var(--color-muted)', fontSize: '1rem', fontWeight: 500 }}>
                        {rollup.healthy} / {rollup.total} brokers healthy
                      </span>
                    </>
                  )
                  : 'No scrape data'}
              </h2>
              {rollup && (
                <div style={{ marginTop: '0.4rem', display: 'flex', gap: '1rem', flexWrap: 'wrap', fontSize: '0.85rem', color: 'var(--color-muted)' }}>
                  <span>{rollup.reachable} / {rollup.total} reachable</span>
                  <span style={{ color: Math.round(rollup.totalUrp) > 0 ? '#dc2626' : 'inherit' }}>URP {Math.round(rollup.totalUrp)}</span>
                  <span style={{ color: Math.round(rollup.totalOffline) > 0 ? '#dc2626' : 'inherit' }}>Offline {Math.round(rollup.totalOffline)}</span>
                  {rollup.controllerHost && <span>Controller: <code style={{ fontFamily: 'monospace' }}>{rollup.controllerHost}</code></span>}
                </div>
              )}
              {lastScrape && (
                <div style={{ marginTop: '0.3rem', fontSize: '0.78rem', color: 'var(--color-muted)' }}>
                  {scrapeIntervalMs > 0 ? <>Auto-refresh every <strong>{fmtInterval(scrapeIntervalMs)}</strong> · </> : <>Auto-refresh <strong>disabled</strong> · </>}
                  last scrape <strong>{fmtElapsedSince(lastScrape.scrapedAt)}</strong>
                </div>
              )}
            </div>
            <button className="secondary-button" type="button" onClick={() => void handleScrape()} disabled={scraping}>
              {scraping ? 'Scraping…' : 'Scrape now'}
            </button>
          </div>

          {!scrapeGroup || scrapeGroup.brokers.length === 0 ? (
            <div className="loading-card" style={{ textAlign: 'center' }}>
              <strong>No broker data.</strong> Click <em>Scrape now</em> or upload an inventory CSV on the Inventory page.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '0.75rem' }}>
              {scrapeGroup.brokers.map((b) => {
                const verdict = brokerHealth(b)
                const colors = HEALTH_COLORS[verdict.label]
                const isController = Math.round(b.activeControllerCount) === 1
                return (
                  <button
                    key={b.targetId}
                    type="button"
                    onClick={() => void navigate(`/clusters/${clusterId}/brokers/${b.targetId}`)}
                    className="panel"
                    style={{
                      padding: '0.85rem',
                      textAlign: 'left',
                      cursor: 'pointer',
                      background: 'var(--color-bg, inherit)',
                      borderLeft: `4px solid ${colors.dot}`,
                      transition: 'transform 0.1s',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem' }}>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: '0.62rem', fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--color-muted)' }}>
                          {b.role}
                          {isController && <span style={{ marginLeft: '0.3rem', color: 'var(--color-accent, #f97316)' }}>· CTRL</span>}
                        </div>
                        <div style={{ fontWeight: 700, fontSize: '0.9rem', marginTop: '0.1rem', wordBreak: 'break-all' }}>{b.host}</div>
                        <div style={{ color: 'var(--color-muted)', fontSize: '0.72rem' }}>port {b.metricsPort}</div>
                      </div>
                      <span
                        style={{
                          padding: '0.15rem 0.5rem',
                          borderRadius: '999px',
                          fontSize: '0.65rem',
                          fontWeight: 700,
                          background: colors.bg,
                          color: colors.fg,
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {verdict.label}
                      </span>
                    </div>
                    <div style={{ marginTop: '0.5rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.3rem 0.6rem', fontSize: '0.72rem' }}>
                      <span style={{ color: 'var(--color-muted)' }}>URP</span>
                      <span style={{ textAlign: 'right', color: b.underReplicatedPartitions > 0 ? '#dc2626' : 'inherit', fontWeight: 600 }}>
                        {b.underReplicatedPartitions >= 0 ? Math.round(b.underReplicatedPartitions) : '—'}
                      </span>
                      <span style={{ color: 'var(--color-muted)' }}>Offline</span>
                      <span style={{ textAlign: 'right', color: b.offlinePartitionsCount > 0 ? '#dc2626' : 'inherit', fontWeight: 600 }}>
                        {b.offlinePartitionsCount >= 0 ? Math.round(b.offlinePartitionsCount) : '—'}
                      </span>
                      <span style={{ color: 'var(--color-muted)' }}>Uptime</span>
                      <span style={{ textAlign: 'right', fontWeight: 600 }}>{fmtUptime(b.uptimeSeconds)}</span>
                    </div>
                  </button>
                )
              })}
            </div>
          )}
        </section>
      )}
    </>
  )
}
