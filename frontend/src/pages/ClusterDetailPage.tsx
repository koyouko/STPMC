import { useEffect, useState } from 'react'
import { useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import { ClusterDetail } from '../components/ClusterDetail'
import type { ClusterHealthDetailResponse } from '../types/api'
import type { DashboardContext } from '../layouts/AppLayout'

export function ClusterDetailPage() {
  const { clusterId } = useParams<{ clusterId: string }>()
  const { setError, reloadClusters } = useOutletContext<DashboardContext>()
  const navigate = useNavigate()
  const [cluster, setCluster] = useState<ClusterHealthDetailResponse | null>(null)
  const [focusedComponentKind, setFocusedComponentKind] = useState<string | null>(null)
  const [refreshing, setRefreshing] = useState(false)

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

  async function handleDelete() {
    if (!clusterId) return
    try {
      await apiClient.deleteCluster(clusterId)
      await reloadClusters()
      void navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to deactivate cluster')
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
    </>
  )
}
