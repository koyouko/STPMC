import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate, useOutletContext } from 'react-router-dom'
import { apiClient } from '../api/client'
import type {
  ClusterHealthSummaryResponse,
  TopicDescribeResponse,
  TopicConfigDescribeResponse,
  MessageCountResponse,
  DumpedMessage,
} from '../types/api'
import JSZip from 'jszip'
import { Breadcrumb, TabBar, ConfirmModal } from '../components/SelfServiceUI'
import { MessageRow } from './selfServiceShared'

interface DashboardContext {
  clusters: ClusterHealthSummaryResponse[]
}

type TabId = 'overview' | 'configuration' | 'messages'

export default function TopicDetailPage() {
  const { clusterId, topicName: rawTopicName } = useParams<{ clusterId: string; topicName: string }>()
  const topicName = decodeURIComponent(rawTopicName ?? '')
  const navigate = useNavigate()
  const { clusters } = useOutletContext<DashboardContext>()
  const cluster = clusters.find((c) => c.clusterId === clusterId)

  const [activeTab, setActiveTab] = useState<TabId>('overview')

  // Data
  const [describe, setDescribe] = useState<TopicDescribeResponse | null>(null)
  const [config, setConfig] = useState<TopicConfigDescribeResponse | null>(null)
  const [messageCount, setMessageCount] = useState<MessageCountResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Messages / Data extraction tab
  const [dumpMax, setDumpMax] = useState(100)
  const [dumpPartition, setDumpPartition] = useState('all')
  const [dumpMessages, setDumpMessages] = useState<DumpedMessage[] | null>(null)
  const [dumpLoading, setDumpLoading] = useState(false)
  const [downloading, setDownloading] = useState(false)

  // Modal
  const [confirmModal, setConfirmModal] = useState<{
    title: string; message: string; action: (approvalRef?: string) => Promise<void>; danger: boolean
  } | null>(null)
  const [modalLoading, setModalLoading] = useState(false)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  // Destructive actions require INC (non-prod) or Change (prod) approval
  const approvalType: 'INC' | 'CHANGE' | null = cluster?.environment === 'PROD' ? 'CHANGE' : 'INC'

  const loadData = useCallback(async () => {
    if (!clusterId || !topicName) return
    setLoading(true)
    setError(null)
    try {
      const [desc, cfg, count] = await Promise.all([
        apiClient.describeTopic(clusterId, topicName),
        apiClient.describeTopicConfig(clusterId, topicName),
        apiClient.getMessageCount(clusterId, topicName),
      ])
      setDescribe(desc)
      setConfig(cfg)
      setMessageCount(count)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [clusterId, topicName])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleDump = async () => {
    if (!clusterId || !topicName) return
    setDumpLoading(true)
    setDumpMessages(null)
    try {
      const partition = dumpPartition !== 'all' ? Number(dumpPartition) : undefined
      const res = await apiClient.dumpTopicMessages(clusterId, topicName, dumpMax || 100, partition)
      setDumpMessages(res.messages)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setDumpLoading(false)
    }
  }

  const handleDownloadZip = async () => {
    if (!dumpMessages || dumpMessages.length === 0) return
    setDownloading(true)
    try {
      const zip = new JSZip()
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
      const partLabel = dumpPartition !== 'all' ? `_p${dumpPartition}` : '_all'

      // Add messages as JSON
      zip.file(
        `${topicName}${partLabel}_messages.json`,
        JSON.stringify(dumpMessages, null, 2),
      )

      // Add a summary file
      const summary = {
        topic: topicName,
        cluster: cluster?.clusterName ?? clusterId,
        partition: dumpPartition === 'all' ? 'all' : Number(dumpPartition),
        messageCount: dumpMessages.length,
        extractedAt: new Date().toISOString(),
        offsetRange: dumpMessages.length > 0
          ? { min: Math.min(...dumpMessages.map((m) => m.offset)), max: Math.max(...dumpMessages.map((m) => m.offset)) }
          : null,
      }
      zip.file('extraction_summary.json', JSON.stringify(summary, null, 2))

      const blob = await zip.generateAsync({ type: 'blob' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${topicName}${partLabel}_${timestamp}.zip`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to generate zip')
    } finally {
      setDownloading(false)
    }
  }

  const handleDelete = () => {
    setConfirmModal({
      title: 'Delete Topic',
      message: `Are you sure you want to delete topic "${topicName}"? This action cannot be undone.`,
      danger: true,
      action: async () => {
        await apiClient.deleteTopic(clusterId!, topicName)
        navigate(`/self-service/${clusterId}?tab=topics`)
      },
    })
  }

  const handlePurge = () => {
    setConfirmModal({
      title: 'Purge Topic',
      message: `Are you sure you want to purge all messages from topic "${topicName}"?`,
      danger: true,
      action: async () => {
        await apiClient.purgeTopic(clusterId!, topicName)
        setSuccessMsg(`Topic "${topicName}" purged successfully.`)
        await loadData()
      },
    })
  }

  const executeModal = async (approvalRef?: string) => {
    if (!confirmModal) return
    setModalLoading(true)
    try {
      await confirmModal.action(approvalRef)
      setConfirmModal(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setConfirmModal(null)
    } finally {
      setModalLoading(false)
    }
  }

  return (
    <div className="detail-page">
      <button className="back-button" onClick={() => navigate(`/self-service/${clusterId}?tab=topics`)}>
        &larr; Back to Topics
      </button>
      <Breadcrumb items={[
        { label: 'Self-Service', to: '/self-service' },
        { label: cluster?.clusterName ?? clusterId ?? '', to: `/self-service/${clusterId}?tab=topics` },
        { label: topicName },
      ]} />

      <div className="detail-page__header">
        <div className="detail-page__title-row">
          <h1>{topicName}</h1>
          {describe && (
            <>
              <span className="detail-badge">{describe.partitions} partitions</span>
              <span className="detail-badge">RF {describe.replicationFactor}</span>
            </>
          )}
        </div>
        <div className="detail-page__actions">
          <button className="btn btn--secondary btn--sm"
            onClick={() => navigate(`/self-service/${clusterId}/TOPIC_INCREASE_PARTITIONS?topic=${encodeURIComponent(topicName)}`)}>
            + Partitions
          </button>
          <button className="btn btn--secondary btn--sm" onClick={handlePurge}>Purge</button>
          <button className="btn btn--danger btn--sm" onClick={handleDelete}>Delete</button>
        </div>
      </div>

      {successMsg && (
        <div className="confirm-banner" style={{ marginBottom: 16 }}>
          {successMsg}
          <button className="btn btn--ghost btn--sm" onClick={() => setSuccessMsg(null)} style={{ marginLeft: 12 }}>Dismiss</button>
        </div>
      )}

      {error && (
        <div className="error-banner" style={{ marginBottom: 16 }}>
          <strong>Error:</strong> {error}
          <button className="btn btn--ghost btn--sm" onClick={() => setError(null)} style={{ marginLeft: 12 }}>Dismiss</button>
        </div>
      )}

      <TabBar
        tabs={[
          { id: 'overview', label: 'Overview' },
          { id: 'configuration', label: 'Configuration', count: config?.configs.length },
          { id: 'messages', label: 'Messages', count: messageCount ? messageCount.totalCount : undefined },
        ]}
        activeTab={activeTab}
        onTabChange={(t) => setActiveTab(t as TabId)}
      />

      {loading && <div className="loading-state">Loading topic details...</div>}

      {/* ── Overview Tab ─────────────────────────────────────────── */}
      {!loading && activeTab === 'overview' && describe && (
        <div className="detail-section">
          {messageCount && (
            <div className="detail-stat-row">
              <div className="detail-stat">
                <span className="detail-stat__value">{messageCount.totalCount.toLocaleString()}</span>
                <span className="detail-stat__label">Total Messages</span>
              </div>
              <div className="detail-stat">
                <span className="detail-stat__value">{describe.partitions}</span>
                <span className="detail-stat__label">Partitions</span>
              </div>
              <div className="detail-stat">
                <span className="detail-stat__value">{describe.replicationFactor}</span>
                <span className="detail-stat__label">Replication Factor</span>
              </div>
            </div>
          )}

          <h3 className="detail-section__title">Partition Details</h3>
          <table className="data-table">
            <thead>
              <tr>
                <th>Partition</th>
                <th>Leader</th>
                <th>Replicas</th>
                <th>ISR</th>
                {messageCount && <th>Messages</th>}
              </tr>
            </thead>
            <tbody>
              {describe.partitionInfos.map((p) => (
                <tr key={p.partition}>
                  <td>{p.partition}</td>
                  <td>{p.leader}</td>
                  <td>{p.replicas.join(', ')}</td>
                  <td>{p.isr.join(', ')}</td>
                  {messageCount && (
                    <td>{(messageCount.partitionCounts[p.partition] ?? 0).toLocaleString()}</td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Configuration Tab ────────────────────────────────────── */}
      {!loading && activeTab === 'configuration' && config && (
        <div className="detail-section">
          <div className="detail-section__header-row">
            <h3 className="detail-section__title">Topic Configuration</h3>
            <button className="btn btn--primary btn--sm"
              onClick={() => navigate(`/self-service/${clusterId}/TOPIC_CONFIG_ALTER?topic=${encodeURIComponent(topicName)}`)}>
              Alter Config
            </button>
          </div>
          <table className="data-table">
            <thead>
              <tr>
                <th>Config</th>
                <th>Value</th>
                <th>Source</th>
                <th>Default</th>
                <th>Read Only</th>
              </tr>
            </thead>
            <tbody>
              {config.configs.map((c) => (
                <tr key={c.name}>
                  <td><strong>{c.name}</strong></td>
                  <td>{c.isSensitive ? '******' : c.value}</td>
                  <td><span className="detail-badge detail-badge--muted">{c.source}</span></td>
                  <td>{c.isDefault ? 'Yes' : <strong>No</strong>}</td>
                  <td>{c.isReadOnly ? 'Yes' : 'No'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ── Data Extraction Tab ────────────────────────────────── */}
      {!loading && activeTab === 'messages' && (
        <div className="detail-section">
          <h3 className="detail-section__title">Data Extraction</h3>
          <p style={{ color: 'var(--ink-soft)', fontSize: '0.85rem', marginBottom: 16 }}>
            Extract messages from this topic. Select a specific partition or extract from the entire topic.
          </p>
          <div className="data-tab-form">
            <div className="form-field">
              <label htmlFor="dump-partition">Source</label>
              <select id="dump-partition" value={dumpPartition} onChange={(e) => setDumpPartition(e.target.value)}>
                <option value="all">Entire topic (all partitions)</option>
                {describe?.partitionInfos.map((p) => (
                  <option key={p.partition} value={String(p.partition)}>
                    Partition {p.partition}
                    {messageCount ? ` (${(messageCount.partitionCounts[p.partition] ?? 0).toLocaleString()} msgs)` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-field">
              <label htmlFor="dump-max">Max Messages</label>
              <input id="dump-max" type="number" value={dumpMax}
                onChange={(e) => setDumpMax(Number(e.target.value))} max={100} min={1} />
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button className="btn btn--primary" onClick={handleDump} disabled={dumpLoading}>
                {dumpLoading ? 'Extracting...' : 'Extract Messages'}
              </button>
            </div>
          </div>

          {dumpMessages !== null && (
            <div style={{ marginTop: 24 }}>
              <div className="detail-section__header-row">
                <h3 className="detail-section__title">
                  Extracted Messages ({dumpMessages.length})
                </h3>
                {dumpMessages.length > 0 && (
                  <button className="btn btn--secondary btn--sm" onClick={handleDownloadZip} disabled={downloading}>
                    {downloading ? 'Generating...' : 'Download ZIP'}
                  </button>
                )}
              </div>
              {dumpMessages.length === 0 ? (
                <p style={{ color: 'var(--ink-soft)' }}>No messages found in this topic.</p>
              ) : (
                <div className="data-dump-result">
                  {dumpMessages.map((msg, i) => (
                    <MessageRow key={i} message={msg} index={i} />
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      <ConfirmModal
        open={!!confirmModal}
        title={confirmModal?.title ?? ''}
        message={confirmModal?.message ?? ''}
        onConfirm={executeModal}
        onCancel={() => setConfirmModal(null)}
        danger={confirmModal?.danger ?? false}
        loading={modalLoading}
        confirmLabel={confirmModal?.danger ? 'Delete' : 'Confirm'}
        approvalType={approvalType}
      />
    </div>
  )
}
