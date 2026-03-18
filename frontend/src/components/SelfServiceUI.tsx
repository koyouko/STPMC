import { useState, useEffect, useMemo, useCallback, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

// ── Breadcrumb ──────────────────────────────────────────────────────

export interface BreadcrumbItem {
  label: string
  to?: string
}

export function Breadcrumb({ items }: { items: BreadcrumbItem[] }) {
  return (
    <nav className="breadcrumb" aria-label="Breadcrumb">
      {items.map((item, i) => {
        const isLast = i === items.length - 1
        return (
          <span key={i}>
            {i > 0 && <span className="breadcrumb__separator">&gt;</span>}
            {' '}
            {isLast || !item.to ? (
              <span className="breadcrumb__current">{item.label}</span>
            ) : (
              <Link to={item.to}>{item.label}</Link>
            )}
          </span>
        )
      })}
    </nav>
  )
}

// ── Status Overview ─────────────────────────────────────────────────

interface StatusOverviewProps {
  topicCount: number | null
  groupCount: number | null
  aclCount: number | null
  schemaCount?: number | null
}

export function StatusOverview({ topicCount, groupCount, aclCount, schemaCount }: StatusOverviewProps) {
  const cards = [
    { label: 'Topics', count: topicCount },
    { label: 'Consumer Groups', count: groupCount },
    { label: 'ACLs', count: aclCount },
    ...(schemaCount !== undefined ? [{ label: 'Schemas', count: schemaCount }] : []),
  ]

  return (
    <div className="status-overview">
      {cards.map((card) => (
        <div key={card.label} className="status-overview__card">
          <span className={`status-overview__count ${card.count === null ? 'status-overview__count--loading' : ''}`}>
            {card.count !== null ? card.count.toLocaleString() : '...'}
          </span>
          <span className="status-overview__label">{card.label}</span>
        </div>
      ))}
    </div>
  )
}

// ── Tab Bar ─────────────────────────────────────────────────────────

export interface TabDef {
  id: string
  label: string
  count?: number | null
}

interface TabBarProps {
  tabs: TabDef[]
  activeTab: string
  onTabChange: (id: string) => void
}

export function TabBar({ tabs, activeTab, onTabChange }: TabBarProps) {
  return (
    <div className="tab-bar" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.id}
          role="tab"
          aria-selected={activeTab === tab.id}
          className={`tab-bar__tab ${activeTab === tab.id ? 'tab-bar__tab--active' : ''}`}
          onClick={() => onTabChange(tab.id)}
        >
          {tab.label}
          {tab.count != null && (
            <span className="tab-bar__count">({tab.count})</span>
          )}
        </button>
      ))}
    </div>
  )
}

// ── Search Bar ──────────────────────────────────────────────────────

interface SearchBarProps {
  value: string
  onChange: (value: string) => void
  placeholder?: string
}

export function SearchBar({ value, onChange, placeholder = 'Search...' }: SearchBarProps) {
  return (
    <div className="search-bar">
      <span className="search-bar__icon">&#x1F50D;</span>
      <input
        type="text"
        className="search-bar__input"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
      />
    </div>
  )
}

// ── Action Toolbar ──────────────────────────────────────────────────

interface ActionToolbarProps {
  left?: ReactNode
  right?: ReactNode
}

export function ActionToolbar({ left, right }: ActionToolbarProps) {
  return (
    <div className="action-toolbar">
      <div className="action-toolbar__left">{left}</div>
      <div className="action-toolbar__right">{right}</div>
    </div>
  )
}

// ── Data Table ──────────────────────────────────────────────────────

export interface ColumnDef<T = any> {
  key: string
  label: string
  sortable?: boolean
  truncate?: boolean
  render?: (row: T) => ReactNode
}

interface DataTableProps<T = any> {
  columns: ColumnDef<T>[]
  data: T[]
  onRowClick?: (row: T) => void
  selectedKey?: string | null
  rowKey?: (row: T) => string
  emptyMessage?: string
  loading?: boolean
}

export function DataTable<T = any>({
  columns,
  data,
  onRowClick,
  selectedKey,
  rowKey,
  emptyMessage = 'No data found.',
  loading = false,
}: DataTableProps<T>) {
  const [sortKey, setSortKey] = useState<string | null>(null)
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')

  const handleSort = useCallback(
    (key: string) => {
      if (sortKey === key) {
        setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
      } else {
        setSortKey(key)
        setSortDir('asc')
      }
    },
    [sortKey],
  )

  const sortedData = useMemo(() => {
    if (!sortKey) return data
    const col = columns.find((c) => c.key === sortKey)
    if (!col || !col.sortable) return data
    return [...data].sort((a, b) => {
      const av = (a as any)[sortKey]
      const bv = (b as any)[sortKey]
      if (av == null && bv == null) return 0
      if (av == null) return 1
      if (bv == null) return -1
      if (typeof av === 'number' && typeof bv === 'number') {
        return sortDir === 'asc' ? av - bv : bv - av
      }
      const as = String(av).toLowerCase()
      const bs = String(bv).toLowerCase()
      const cmp = as.localeCompare(bs)
      return sortDir === 'asc' ? cmp : -cmp
    })
  }, [data, sortKey, sortDir, columns])

  return (
    <table className="data-table">
      <thead>
        <tr>
          {columns.map((col) => (
            <th
              key={col.key}
              data-sortable={col.sortable ? 'true' : undefined}
              onClick={col.sortable ? () => handleSort(col.key) : undefined}
            >
              {col.label}
              {col.sortable && (
                <span
                  className={`data-table__sort-icon ${sortKey === col.key ? 'data-table__sort-icon--active' : ''}`}
                >
                  {sortKey === col.key ? (sortDir === 'asc' ? ' \u25B2' : ' \u25BC') : ' \u25B4'}
                </span>
              )}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {loading ? (
          <tr>
            <td colSpan={columns.length} className="data-table__loading">
              Loading...
            </td>
          </tr>
        ) : sortedData.length === 0 ? (
          <tr>
            <td colSpan={columns.length} className="data-table__empty">
              {emptyMessage}
            </td>
          </tr>
        ) : (
          sortedData.map((row, i) => {
            const key = rowKey ? rowKey(row) : String(i)
            const isSelected = selectedKey != null && key === selectedKey
            return (
              <tr
                key={key}
                className={[
                  onRowClick ? 'data-table__row--clickable' : '',
                  isSelected ? 'data-table__row--selected' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((col) => (
                  <td key={col.key} className={col.truncate ? 'data-table__cell--truncate' : ''} title={col.truncate ? String((row as any)[col.key] ?? '') : undefined}>
                    {col.render ? col.render(row) : String((row as any)[col.key] ?? '')}
                  </td>
                ))}
              </tr>
            )
          })
        )}
      </tbody>
    </table>
  )
}

// ── CSV Export Button ────────────────────────────────────────────────

interface CsvExportButtonProps {
  data: any[]
  columns: { key: string; label: string }[]
  filename: string
}

export function CsvExportButton({ data, columns, filename }: CsvExportButtonProps) {
  const handleExport = () => {
    const header = columns.map((c) => c.label).join(',')
    const rows = data.map((row) =>
      columns
        .map((c) => {
          const val = String(row[c.key] ?? '')
          return val.includes(',') || val.includes('"') || val.includes('\n')
            ? `"${val.replace(/"/g, '""')}"`
            : val
        })
        .join(','),
    )
    const csv = [header, ...rows].join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${filename}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <button className="btn btn--ghost btn--sm" onClick={handleExport} title="Export CSV">
      &#x2193; CSV
    </button>
  )
}

// ── Detail Panel ────────────────────────────────────────────────────

interface DetailPanelProps {
  title: string
  onClose: () => void
  actions?: ReactNode
  children: ReactNode
}

export function DetailPanel({ title, onClose, actions, children }: DetailPanelProps) {
  return (
    <div className="inline-detail">
      <div className="inline-detail__header">
        <h3 className="inline-detail__title">{title}</h3>
        <button className="inline-detail__close" onClick={onClose}>
          Close
        </button>
      </div>
      {children}
      {actions && <div className="inline-detail__actions">{actions}</div>}
    </div>
  )
}

// ── Confirm Modal ───────────────────────────────────────────────────

interface ConfirmModalProps {
  open: boolean
  title: string
  message: string
  onConfirm: (approvalRef?: string) => void
  onCancel: () => void
  danger?: boolean
  loading?: boolean
  confirmLabel?: string
  /** If set, requires an INC or Change number before confirming */
  approvalType?: 'INC' | 'CHANGE' | null
}

export function ConfirmModal({
  open,
  title,
  message,
  onConfirm,
  onCancel,
  danger = false,
  loading = false,
  confirmLabel = 'Confirm',
  approvalType = null,
}: ConfirmModalProps) {
  const [approvalRef, setApprovalRef] = useState('')

  // Reset when modal opens/closes
  useEffect(() => {
    if (!open) setApprovalRef('')
  }, [open])

  if (!open) return null

  const needsApproval = approvalType != null
  const approvalValid = !needsApproval || approvalRef.trim().length >= 3

  const placeholder = approvalType === 'CHANGE' ? 'CHG00012345' : 'INC00012345'
  const approvalLabel = approvalType === 'CHANGE'
    ? 'Change Number (required for production)'
    : 'INC Number (required for non-production)'

  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2 className="modal__title">{title}</h2>
        <p className="modal__message">{message}</p>
        {needsApproval && (
          <div className="approval-form">
            <span className="approval-form__label">{approvalLabel}</span>
            <input
              className="approval-form__input"
              type="text"
              value={approvalRef}
              onChange={(e) => setApprovalRef(e.target.value)}
              placeholder={placeholder}
              autoFocus
            />
            <span className="approval-form__hint">
              {approvalType === 'CHANGE'
                ? 'A valid ServiceNow Change number is required for production clusters.'
                : 'A valid ServiceNow INC number is required for this operation.'}
            </span>
          </div>
        )}
        <div className="modal__actions">
          <button className="btn btn--secondary" onClick={onCancel} disabled={loading}>
            Cancel
          </button>
          <button
            className={`btn ${danger ? 'btn--danger' : 'btn--primary'}`}
            onClick={() => onConfirm(approvalRef || undefined)}
            disabled={loading || !approvalValid}
          >
            {loading ? 'Processing...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
