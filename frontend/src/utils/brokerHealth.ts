import type {
  BrokerMetricsSample,
  ClusterHealthSummaryResponse,
  DiscoveredCluster,
  HealthStatus,
  MetricsScrapeResponse,
} from '../types/api'

export type BrokerHealthLabel = 'Healthy' | 'Degraded' | 'Unreachable' | 'Unknown'

export interface BrokerHealthVerdict {
  label: BrokerHealthLabel
  reasons: string[]
}

const BROKER_STATE_NAMES: Record<number, string> = {
  0: 'Unknown',
  1: 'Starting',
  2: 'Recovery',
  3: 'Running',
  6: 'PendingShutdown',
  7: 'Shutdown',
}

export function brokerStateName(state: number): string {
  if (state < 0) return '—'
  return BROKER_STATE_NAMES[Math.round(state)] ?? String(Math.round(state))
}

/** Composite per-broker health verdict. "Healthy" requires reachable +
 *  brokerState=3 (Running) + URP=0 + offline=0. Any violation → Degraded. */
export function brokerHealth(sample: BrokerMetricsSample): BrokerHealthVerdict {
  if (!sample.reachable) {
    return { label: 'Unreachable', reasons: [sample.errorMessage ?? 'no response from JMX /metrics'] }
  }
  const reasons: string[] = []
  if (sample.brokerState >= 0 && Math.round(sample.brokerState) !== 3) {
    reasons.push(`state=${brokerStateName(sample.brokerState)}`)
  }
  if (sample.underReplicatedPartitions > 0) {
    reasons.push(`URP=${Math.round(sample.underReplicatedPartitions)}`)
  }
  if (sample.offlinePartitionsCount > 0) {
    reasons.push(`offline=${Math.round(sample.offlinePartitionsCount)}`)
  }
  return reasons.length === 0 ? { label: 'Healthy', reasons: [] } : { label: 'Degraded', reasons }
}

/** Roll-up across the brokers of a single DiscoveredCluster group. */
export interface ClusterRollup {
  total: number
  healthy: number
  reachable: number
  unreachable: number
  degraded: number
  totalUrp: number
  totalOffline: number
  controllerHost: string | null
  /** Worst-case per-cluster verdict for the sidebar/status badge. */
  overall: BrokerHealthLabel
}

export function rollupCluster(group: DiscoveredCluster): ClusterRollup {
  const verdicts = group.brokers.map(brokerHealth)
  const healthy = verdicts.filter((v) => v.label === 'Healthy').length
  const reachable = group.brokers.filter((b) => b.reachable).length
  const unreachable = verdicts.filter((v) => v.label === 'Unreachable').length
  const degraded = verdicts.filter((v) => v.label === 'Degraded').length
  const totalUrp = group.brokers.reduce(
    (s, b) => s + (b.underReplicatedPartitions > 0 ? b.underReplicatedPartitions : 0),
    0,
  )
  const totalOffline = group.brokers.reduce(
    (s, b) => s + (b.offlinePartitionsCount > 0 ? b.offlinePartitionsCount : 0),
    0,
  )
  const controller = group.brokers.find((b) => Math.round(b.activeControllerCount) === 1)
  const overall: BrokerHealthLabel =
    group.brokers.length === 0 ? 'Unknown'
      : unreachable === group.brokers.length ? 'Unreachable'
      : degraded + unreachable > 0 ? 'Degraded'
      : 'Healthy'
  return {
    total: group.brokers.length,
    healthy,
    reachable,
    unreachable,
    degraded,
    totalUrp,
    totalOffline,
    controllerHost: controller?.host ?? null,
    overall,
  }
}

/** "just now" / "12s ago" / "3m ago" / "1h 15m ago" for elapsed times. */
export function fmtElapsedSince(isoOrDate: string | Date | null | undefined): string {
  if (!isoOrDate) return '—'
  const then = typeof isoOrDate === 'string' ? new Date(isoOrDate).getTime() : isoOrDate.getTime()
  const diffSec = Math.max(0, Math.round((Date.now() - then) / 1000))
  if (diffSec < 2) return 'just now'
  if (diffSec < 60) return `${diffSec}s ago`
  const m = Math.floor(diffSec / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  return `${h}h ${m % 60}m ago`
}

/** "60 s" / "2 m" / "1 h" — for describing a configured interval length. */
export function fmtInterval(ms: number): string {
  if (ms <= 0) return 'disabled'
  if (ms < 60_000) return `${Math.round(ms / 1000)} s`
  if (ms < 3_600_000) return `${Math.round(ms / 60_000)} m`
  return `${Math.round(ms / 3_600_000)} h`
}

export function fmtUptime(seconds: number): string {
  if (seconds < 0) return '—'
  const s = Math.round(seconds)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  if (d > 0) return `${d}d ${h}h`
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s % 60}s`
  return `${s}s`
}

/** Combine HealthService AdminClient status with the scrape-derived rollup
 *  to produce the worst-case status for a cluster. HealthService only knows
 *  "can we reach bootstrap?", so it happily reports HEALTHY while URP > 0 or
 *  brokers are unreachable — the scraper is what catches that nuance. */
export function effectiveClusterStatus(
  cluster: ClusterHealthSummaryResponse,
  scrape: MetricsScrapeResponse | undefined,
): HealthStatus {
  const group = scrape?.clusters.find((g) => g.clusterName === cluster.clusterName)
  if (!group) return cluster.status
  const rollup = rollupCluster(group)
  // Severity order: DOWN (3) > DEGRADED (2) > HEALTHY (1) > UNKNOWN (0).
  const rank: Record<HealthStatus, number> = { DOWN: 3, DEGRADED: 2, HEALTHY: 1, UNKNOWN: 0, NOT_APPLICABLE: 0 }
  const scrapeEquivalent: HealthStatus =
    rollup.overall === 'Unreachable' ? 'DOWN'
      : rollup.overall === 'Degraded' ? 'DEGRADED'
      : rollup.overall === 'Healthy' ? 'HEALTHY'
      : 'UNKNOWN'
  return rank[scrapeEquivalent] > rank[cluster.status] ? scrapeEquivalent : cluster.status
}

/** Color tokens for Healthy/Degraded/Unreachable/Unknown badges. */
export const HEALTH_COLORS: Record<BrokerHealthLabel, { bg: string; fg: string; dot: string }> = {
  Healthy: { bg: 'rgba(34,197,94,0.15)', fg: '#16a34a', dot: '#16a34a' },
  Degraded: { bg: 'rgba(234,179,8,0.18)', fg: '#b45309', dot: '#eab308' },
  Unreachable: { bg: 'rgba(239,68,68,0.15)', fg: '#dc2626', dot: '#dc2626' },
  Unknown: { bg: 'rgba(120,113,108,0.15)', fg: '#78716c', dot: '#78716c' },
}
