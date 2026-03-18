import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate, useOutletContext } from 'react-router-dom'
import { apiClient } from '../api/client'
import type {
  ClusterHealthSummaryResponse,
  ConsumerGroupDescribeResponse,
} from '../types/api'
import { Breadcrumb, ConfirmModal } from '../components/SelfServiceUI'

interface DashboardContext {
  clusters: ClusterHealthSummaryResponse[]
}

export default function ConsumerGroupDetailPage() {
  const { clusterId, groupId: rawGroupId } = useParams<{ clusterId: string; groupId: string }>()
  const groupId = decodeURIComponent(rawGroupId ?? '')
  const navigate = useNavigate()
  const { clusters } = useOutletContext<DashboardContext>()
  const cluster = clusters.find((c) => c.clusterId === clusterId)

  const [detail, setDetail] = useState<ConsumerGroupDescribeResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [confirmModal, setConfirmModal] = useState<{
    title: string; message: string; action: () => Promise<void>; danger: boolean
  } | null>(null)
  const [modalLoading, setModalLoading] = useState(false)

  const approvalType: 'INC' | 'CHANGE' | null = cluster?.environment === 'PROD' ? 'CHANGE' : 'INC'

  const loadData = useCallback(async () => {
    if (!clusterId || !groupId) return
    setLoading(true)
    setError(null)
    try {
      const res = await apiClient.describeConsumerGroup(clusterId, groupId)
      setDetail(res)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [clusterId, groupId])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleDelete = () => {
    setConfirmModal({
      title: 'Delete Consumer Group',
      message: `Are you sure you want to delete consumer group "${groupId}"?`,
      danger: true,
      action: async () => {
        await apiClient.deleteConsumerGroup(clusterId!, groupId)
        navigate(`/self-service/${clusterId}?tab=consumer-groups`)
      },
    })
  }

  const executeModal = async (_approvalRef?: string) => {
    if (!confirmModal) return
    setModalLoading(true)
    try {
      await confirmModal.action()
      setConfirmModal(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
      setConfirmModal(null)
    } finally {
      setModalLoading(false)
    }
  }

  const totalLag = detail?.offsets.reduce((sum, o) => sum + o.lag, 0) ?? 0

  return (
    <div className="detail-page">
      <button className="back-button" onClick={() => navigate(`/self-service/${clusterId}?tab=consumer-groups`)}>
        &larr; Back to Consumer Groups
      </button>
      <Breadcrumb items={[
        { label: 'Self-Service', to: '/self-service' },
        { label: cluster?.clusterName ?? clusterId ?? '', to: `/self-service/${clusterId}?tab=consumer-groups` },
        { label: groupId },
      ]} />

      <div className="detail-page__header">
        <div className="detail-page__title-row">
          <h1>{groupId}</h1>
          {detail && (
            <>
              <span className={`status-pill status-pill--${detail.state === 'Stable' ? 'healthy' : detail.state === 'Empty' ? 'unknown' : 'degraded'}`}>
                {detail.state}
              </span>
            </>
          )}
        </div>
        <div className="detail-page__actions">
          <button className="btn btn--secondary btn--sm"
            onClick={() => navigate(`/self-service/${clusterId}/CONSUMER_GROUP_OFFSETS?group=${encodeURIComponent(groupId)}`)}>
            Reset Offsets
          </button>
          <button className="btn btn--danger btn--sm" onClick={handleDelete}>Delete Group</button>
        </div>
      </div>

      {error && (
        <div className="error-banner" style={{ marginBottom: 16 }}>
          <strong>Error:</strong> {error}
          <button className="btn btn--ghost btn--sm" onClick={() => setError(null)} style={{ marginLeft: 12 }}>Dismiss</button>
        </div>
      )}

      {loading && <div className="loading-state">Loading consumer group details...</div>}

      {!loading && detail && (
        <>
          <div className="detail-stat-row">
            <div className="detail-stat">
              <span className="detail-stat__value">{detail.members.length}</span>
              <span className="detail-stat__label">Members</span>
            </div>
            <div className="detail-stat">
              <span className="detail-stat__value">{detail.coordinator}</span>
              <span className="detail-stat__label">Coordinator</span>
            </div>
            <div className="detail-stat">
              <span className="detail-stat__value">{totalLag.toLocaleString()}</span>
              <span className="detail-stat__label">Total Lag</span>
            </div>
          </div>

          {detail.members.length > 0 && (
            <div className="detail-section">
              <h3 className="detail-section__title">Members ({detail.members.length})</h3>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Member ID</th>
                    <th>Client ID</th>
                    <th>Host</th>
                    <th>Assignments</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.members.map((m) => (
                    <tr key={m.memberId}>
                      <td className="audit-detail-cell" title={m.memberId}>{m.memberId}</td>
                      <td>{m.clientId}</td>
                      <td>{m.host}</td>
                      <td>{m.assignments.map((a) => `${a.topic}:${a.partition}`).join(', ') || '\u2014'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {detail.offsets.length > 0 && (
            <div className="detail-section">
              <h3 className="detail-section__title">Offsets ({detail.offsets.length})</h3>
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Topic</th>
                    <th>Partition</th>
                    <th>Current Offset</th>
                    <th>Log End Offset</th>
                    <th>Lag</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.offsets.map((o, i) => (
                    <tr key={i}>
                      <td>{o.topic}</td>
                      <td>{o.partition}</td>
                      <td>{o.currentOffset.toLocaleString()}</td>
                      <td>{o.logEndOffset.toLocaleString()}</td>
                      <td>
                        <span className={o.lag > 0 ? 'lag-warning' : ''}>
                          {o.lag.toLocaleString()}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      <ConfirmModal
        open={!!confirmModal}
        title={confirmModal?.title ?? ''}
        message={confirmModal?.message ?? ''}
        onConfirm={executeModal}
        onCancel={() => setConfirmModal(null)}
        danger={confirmModal?.danger ?? false}
        loading={modalLoading}
        confirmLabel="Delete"
        approvalType={approvalType}
      />
    </div>
  )
}
