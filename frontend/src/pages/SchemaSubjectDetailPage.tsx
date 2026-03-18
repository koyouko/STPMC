import { useState, useEffect, useCallback } from 'react'
import { useParams, useOutletContext, useNavigate } from 'react-router-dom'
import { apiClient } from '../api/client'
import type {
  ClusterHealthSummaryResponse,
  SchemaResponse,
} from '../types/api'
import { Breadcrumb, DataTable, type ColumnDef } from '../components/SelfServiceUI'

interface DashboardContext {
  clusters: ClusterHealthSummaryResponse[]
}

export default function SchemaSubjectDetailPage() {
  const { clusterId, subject: rawSubject } = useParams<{ clusterId: string; subject: string }>()
  const subject = decodeURIComponent(rawSubject ?? '')
  const navigate = useNavigate()
  const { clusters } = useOutletContext<DashboardContext>()
  const cluster = clusters.find((c) => c.clusterId === clusterId)

  const [versions, setVersions] = useState<number[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [selectedVersion, setSelectedVersion] = useState<number | null>(null)
  const [schema, setSchema] = useState<SchemaResponse | null>(null)
  const [schemaLoading, setSchemaLoading] = useState(false)

  const loadVersions = useCallback(async () => {
    if (!clusterId || !subject) return
    setLoading(true)
    setError(null)
    try {
      const res = await apiClient.getSchemaSubjectVersions(clusterId, subject)
      setVersions(res.versions)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [clusterId, subject])

  useEffect(() => {
    loadVersions()
  }, [loadVersions])

  const handleVersionClick = async (row: { version: number }) => {
    if (selectedVersion === row.version) {
      setSelectedVersion(null)
      setSchema(null)
      return
    }
    setSelectedVersion(row.version)
    setSchema(null)
    setSchemaLoading(true)
    try {
      const res = await apiClient.getSchemaVersion(clusterId!, subject, row.version)
      setSchema(res)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setSchemaLoading(false)
    }
  }

  const versionRows = versions.map((v) => ({ version: v }))

  const columns: ColumnDef<{ version: number }>[] = [
    { key: 'version', label: 'Version', sortable: true },
  ]

  const formatSchema = (raw: string): string => {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2)
    } catch {
      return raw
    }
  }

  return (
    <div className="detail-page">
      <button className="back-button" onClick={() => navigate(`/self-service/${clusterId}?tab=schemas`)}>
        &larr; Back to Schema Registry
      </button>
      <Breadcrumb items={[
        { label: 'Self-Service', to: '/self-service' },
        { label: cluster?.clusterName ?? clusterId ?? '', to: `/self-service/${clusterId}?tab=schemas` },
        { label: subject },
      ]} />

      <div className="detail-page__header">
        <div className="detail-page__title-row">
          <h1>{subject}</h1>
          <span className="detail-badge">{versions.length} version{versions.length !== 1 ? 's' : ''}</span>
        </div>
      </div>

      {error && (
        <div className="error-banner" style={{ marginBottom: 16 }}>
          <strong>Error:</strong> {error}
          <button className="btn btn--ghost btn--sm" onClick={() => setError(null)} style={{ marginLeft: 12 }}>Dismiss</button>
        </div>
      )}

      <div className="detail-section">
        <h3 className="detail-section__title">Versions</h3>
        <DataTable
          columns={columns}
          data={versionRows}
          onRowClick={handleVersionClick}
          selectedKey={selectedVersion != null ? String(selectedVersion) : null}
          rowKey={(r) => String(r.version)}
          loading={loading}
          emptyMessage="No versions found."
        />
      </div>

      {selectedVersion !== null && (
        <div className="detail-section">
          <h3 className="detail-section__title">Schema &mdash; Version {selectedVersion}</h3>
          {schemaLoading ? (
            <div className="loading-state">Loading schema...</div>
          ) : schema ? (
            <div>
              <div className="detail-stat-row" style={{ marginBottom: 16 }}>
                <div className="detail-stat">
                  <span className="detail-stat__value">{schema.id}</span>
                  <span className="detail-stat__label">Schema ID</span>
                </div>
                <div className="detail-stat">
                  <span className="detail-stat__value">{schema.schemaType}</span>
                  <span className="detail-stat__label">Type</span>
                </div>
              </div>
              <pre className="schema-content">{formatSchema(schema.schema)}</pre>
            </div>
          ) : null}
        </div>
      )}
    </div>
  )
}
