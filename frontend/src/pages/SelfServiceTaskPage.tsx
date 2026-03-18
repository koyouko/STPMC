import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate, useOutletContext, useSearchParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import type {
  ClusterHealthSummaryResponse,
  SelfServiceTaskType,
  TopicSummary,
  ConsumerGroupSummary,
} from '../types/api'
import { formatLabel } from '../utils/formatters'
import {
  TASK_CONFIGS,
  TASK_TYPE_TO_TAB,
  renderResultForTask,
  type FieldDef,
} from './selfServiceShared'
import { Breadcrumb } from '../components/SelfServiceUI'

interface DashboardContext {
  clusters: ClusterHealthSummaryResponse[]
  filteredClusters: ClusterHealthSummaryResponse[]
  filter: string
  setFilter: (f: string) => void
  serviceAccounts: any[]
  setServiceAccounts: (s: any[]) => void
  setError: (e: string | null) => void
  reloadClusters: () => Promise<void>
}

export default function SelfServiceTaskPage() {
  const { clusterId, taskType } = useParams<{ clusterId: string; taskType: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { clusters } = useOutletContext<DashboardContext>()

  const cluster = clusters.find((c) => c.clusterId === clusterId)
  const config = TASK_CONFIGS[taskType as SelfServiceTaskType]

  // Pre-fill from query params (e.g., ?topic=myTopic or ?group=myGroup)
  const prefillTopic = searchParams.get('topic') ?? ''
  const prefillGroup = searchParams.get('group') ?? ''

  // Dynamic option lists
  const [topics, setTopics] = useState<TopicSummary[]>([])
  const [groups, setGroups] = useState<ConsumerGroupSummary[]>([])
  const [dynamicLoading, setDynamicLoading] = useState(false)

  // Form state
  const buildDefaults = useCallback(() => {
    const defaults: Record<string, any> = {}
    if (config) {
      for (const f of config.fields) {
        // Pre-fill topic/group fields from query params
        if (f.dynamicOptions === 'topics' && prefillTopic) {
          defaults[f.name] = prefillTopic
        } else if (f.dynamicOptions === 'groups' && prefillGroup) {
          defaults[f.name] = prefillGroup
        } else {
          defaults[f.name] = f.defaultValue ?? (f.type === 'checkbox' ? false : '')
        }
      }
    }
    return defaults
  }, [config, prefillTopic, prefillGroup])

  const [params, setParams] = useState<Record<string, any>>(buildDefaults)
  const [result, setResult] = useState<any>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmed, setConfirmed] = useState(false)

  // Load dynamic options on mount
  useEffect(() => {
    if (!clusterId || !config) return

    const needsTopics = config.fields.some((f) => f.dynamicOptions === 'topics')
    const needsGroups = config.fields.some((f) => f.dynamicOptions === 'groups')

    if (!needsTopics && !needsGroups) return

    let cancelled = false
    setDynamicLoading(true)

    const promises: Promise<void>[] = []

    if (needsTopics) {
      promises.push(
        apiClient.listTopics(clusterId).then((res) => {
          if (!cancelled) setTopics(res.topics)
        }),
      )
    }
    if (needsGroups) {
      promises.push(
        apiClient.listConsumerGroups(clusterId).then((res) => {
          if (!cancelled) setGroups(res.groups)
        }),
      )
    }

    Promise.all(promises)
      .catch((err) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err))
      })
      .finally(() => {
        if (!cancelled) setDynamicLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [clusterId, config])

  // Reset form when task type changes
  useEffect(() => {
    setParams(buildDefaults())
    setResult(null)
    setError(null)
    setConfirmed(false)
  }, [taskType, buildDefaults])

  if (!config) {
    return (
      <div className="self-service-task-page">
        <div className="error-banner">Unknown task type: {taskType}</div>
      </div>
    )
  }

  const setParam = (name: string, value: any) => {
    setParams((prev) => ({ ...prev, [name]: value }))
  }

  const handleExecute = async () => {
    if (!config.readOnly && !confirmed) {
      setConfirmed(true)
      return
    }

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const res = await config.execute(clusterId!, params)
      setResult(res)
      setConfirmed(false)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setLoading(false)
    }
  }

  const getOptionsForField = (field: FieldDef): string[] => {
    if (field.dynamicOptions === 'topics') return topics.map((t) => t.name)
    if (field.dynamicOptions === 'groups') return groups.map((g) => g.groupId)
    return field.options ?? []
  }

  const renderForm = () => (
    <div className="form-grid">
      {config.fields.map((field) => {
        if (field.showWhen && !field.showWhen(params)) return null

        const fieldId = `field-${field.name}`
        const value = params[field.name] ?? ''

        return (
          <div key={field.name} className="form-field">
            <label htmlFor={fieldId}>
              {field.label}
              {field.required && <span className="required-star"> *</span>}
            </label>

            {field.type === 'select' ? (
              <select
                id={fieldId}
                value={value}
                onChange={(e) => setParam(field.name, e.target.value)}
                required={field.required}
              >
                <option value="">-- Select --</option>
                {getOptionsForField(field).map((opt) => (
                  <option key={opt} value={opt}>
                    {opt}
                  </option>
                ))}
              </select>
            ) : field.type === 'textarea' ? (
              <textarea
                id={fieldId}
                value={value}
                onChange={(e) => setParam(field.name, e.target.value)}
                required={field.required}
                rows={4}
              />
            ) : field.type === 'checkbox' ? (
              <input
                id={fieldId}
                type="checkbox"
                checked={!!value}
                onChange={(e) => setParam(field.name, e.target.checked)}
              />
            ) : (
              <input
                id={fieldId}
                type={field.type}
                value={value}
                onChange={(e) => setParam(field.name, e.target.value)}
                required={field.required}
                max={field.max}
              />
            )}
          </div>
        )
      })}
    </div>
  )

  const backTab = TASK_TYPE_TO_TAB[taskType as SelfServiceTaskType] || 'topics'

  return (
    <div className="self-service-task-page">
      <section style={{ marginBottom: 20 }}>
        <Breadcrumb
          items={[
            { label: 'Self-Service', to: '/self-service' },
            { label: cluster?.clusterName ?? clusterId ?? '', to: `/self-service/${clusterId}?tab=${backTab}` },
            { label: config.displayName },
          ]}
        />
        <div className="console-header__title-row" style={{ marginTop: 8 }}>
          <h1>{config.displayName}</h1>
          <span className={`task-mode-badge ${config.readOnly ? 'task-mode-badge--read' : 'task-mode-badge--write'}`}>
            {config.readOnly ? 'Read Only' : 'Write'}
          </span>
        </div>
        <p style={{ color: 'var(--ink-soft)', fontSize: '0.9rem', margin: '4px 0 0' }}>
          {formatLabel(config.category)} &middot; {cluster?.clusterName ?? clusterId}
        </p>
      </section>

      {dynamicLoading && <div className="loading-state">Loading options...</div>}

      {config.fields.length > 0 && renderForm()}

      {!config.readOnly && confirmed && (
        <div className="confirm-banner confirm-banner--danger">
          <strong>Warning:</strong> This is a write operation. Click Execute again to confirm.
          <button className="btn btn--secondary" onClick={() => setConfirmed(false)}>
            Cancel
          </button>
        </div>
      )}

      <div className="action-bar">
        <button
          className={`btn ${confirmed ? 'btn--danger' : 'btn--primary'}`}
          onClick={handleExecute}
          disabled={loading}
        >
          {loading ? 'Executing...' : confirmed ? 'Confirm Execute' : 'Execute'}
        </button>
      </div>

      {error && (
        <div className="error-banner">
          <strong>Error:</strong> {error}
        </div>
      )}

      {result && (
        <section className="result-section">
          <h2>Result</h2>
          {renderResultForTask(taskType as SelfServiceTaskType, result)}
          <div style={{ marginTop: 20 }}>
            <button
              className="btn btn--secondary"
              onClick={() => navigate(`/self-service/${clusterId}?tab=${backTab}`)}
            >
              &larr; Back to Console
            </button>
          </div>
        </section>
      )}
    </div>
  )
}
