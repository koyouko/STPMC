import { useState, useEffect, useCallback, useMemo } from 'react'
import { apiClient } from '../api/client'
import type { AuditEventResponse, AuditPageResponse } from '../types/api'

interface ColumnDef<T> {
  key: keyof T | string
  label: string
  sortable?: boolean
  render?: (row: T) => React.ReactNode
}

function SearchBar({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <input
      type="search"
      className="search-input"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
    />
  )
}

function DataTable<T>({
  columns,
  data,
  loading,
  emptyMessage,
  rowKey,
}: {
  columns: ColumnDef<T>[]
  data: T[]
  loading?: boolean
  emptyMessage?: string
  rowKey: (row: T) => string
}) {
  if (loading) return <div className="loading-card">Loading…</div>
  if (data.length === 0) return <div className="loading-card">{emptyMessage ?? 'No data.'}</div>
  return (
    <table className="data-table" style={{ width: '100%' }}>
      <thead>
        <tr>
          {columns.map((col) => (
            <th key={String(col.key)}>{col.label}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((row) => (
          <tr key={rowKey(row)}>
            {columns.map((col) => (
              <td key={String(col.key)}>
                {col.render ? col.render(row) : String(row[col.key as keyof T] ?? '')}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default function AuditLogPage() {
  const [data, setData] = useState<AuditPageResponse | null>(null)
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 350)
    return () => clearTimeout(timer)
  }, [search])

  const loadEvents = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await apiClient.getAuditEvents(page, 50, debouncedSearch || undefined)
      setData(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }, [page, debouncedSearch])

  useEffect(() => {
    loadEvents()
  }, [loadEvents])

  // Reset to page 0 when search changes
  useEffect(() => {
    setPage(0)
  }, [debouncedSearch])

  const getActionBadgeClass = (action: string): string => {
    const lower = action.toLowerCase()
    if (lower.includes('created') || lower.includes('granted')) return 'audit-action-badge--create'
    if (lower.includes('deleted') || lower.includes('removed') || lower.includes('deactivated')) return 'audit-action-badge--delete'
    if (lower.includes('revoked')) return 'audit-action-badge--revoke'
    if (lower.includes('updated') || lower.includes('altered') || lower.includes('reset') || lower.includes('purged') || lower.includes('increased')) return 'audit-action-badge--update'
    if (lower.includes('refresh') || lower.includes('used')) return 'audit-action-badge--refresh'
    return ''
  }

  const formatTimestamp = (ts: string) => {
    const d = new Date(ts)
    return d.toLocaleString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    })
  }

  const columns: ColumnDef<AuditEventResponse>[] = useMemo(() => [
    {
      key: 'createdAt',
      label: 'Timestamp',
      sortable: true,
      render: (row) => (
        <span style={{ fontSize: '0.82rem', whiteSpace: 'nowrap' }}>
          {formatTimestamp(row.createdAt)}
        </span>
      ),
    },
    {
      key: 'actor',
      label: 'Actor',
      sortable: true,
    },
    {
      key: 'action',
      label: 'Action',
      sortable: true,
      render: (row) => (
        <span className={`audit-action-badge ${getActionBadgeClass(row.action)}`}>
          {row.action}
        </span>
      ),
    },
    {
      key: 'entityType',
      label: 'Entity Type',
      sortable: true,
    },
    {
      key: 'entityId',
      label: 'Entity ID',
      sortable: false,
      render: (row) => (
        <span className="audit-detail-cell" title={row.entityId}>
          {row.entityId}
        </span>
      ),
    },
    {
      key: 'details',
      label: 'Details',
      sortable: false,
      render: (row) => (
        <span className="audit-detail-cell" title={row.details ?? ''}>
          {row.details || '\u2014'}
        </span>
      ),
    },
  ], [])

  const events = data?.events ?? []

  return (
    <div className="audit-log-page">
      <span className="eyebrow">Administration</span>
      <h1>Audit Log</h1>
      <p className="page-subtitle">
        Track all platform operations — cluster changes, metric scrapes, and service account activity.
      </p>

      <div className="audit-filters">
        <SearchBar
          value={search}
          onChange={setSearch}
          placeholder="Search by actor, action, or entity type..."
        />
        <button className="btn btn--ghost btn--sm" onClick={loadEvents} disabled={loading}>
          {loading ? 'Loading...' : 'Refresh'}
        </button>
      </div>

      {error && (
        <div className="error-banner" style={{ marginBottom: 16 }}>
          <strong>Error:</strong> {error}
          <button className="btn btn--ghost btn--sm" onClick={() => setError(null)} style={{ marginLeft: 12 }}>
            Dismiss
          </button>
        </div>
      )}

      <DataTable
        columns={columns}
        data={events}
        loading={loading && !data}
        emptyMessage="No audit events found."
        rowKey={(e) => e.id}
      />

      {data && (
        <div className="audit-pagination">
          <span>
            Showing {events.length} of {data.totalElements.toLocaleString()} events
          </span>
          <div className="audit-pagination__controls">
            <button
              className="btn btn--ghost btn--sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </button>
            <span>
              Page {page + 1} of {Math.max(1, data.totalPages)}
            </span>
            <button
              className="btn btn--ghost btn--sm"
              disabled={page >= data.totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
