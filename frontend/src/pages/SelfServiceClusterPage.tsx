import { useState, useEffect, useCallback, useMemo } from 'react'
import { useParams, useNavigate, useOutletContext, useSearchParams } from 'react-router-dom'
import { apiClient } from '../api/client'
import type {
  ClusterHealthSummaryResponse,
  TopicSummary,
  AclEntry,
  ConsumerGroupSummary,
} from '../types/api'
import { formatEnvironment } from '../utils/formatters'
import {
  Breadcrumb,
  StatusOverview,
  TabBar,
  SearchBar,
  ActionToolbar,
  DataTable,
  CsvExportButton,
  type ColumnDef,
} from '../components/SelfServiceUI'

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

type TabId = 'topics' | 'acls' | 'consumer-groups' | 'schemas'

export default function SelfServiceClusterPage() {
  const { clusterId } = useParams<{ clusterId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { clusters } = useOutletContext<DashboardContext>()

  const cluster = clusters.find((c) => c.clusterId === clusterId)

  // ── Tab state ──────────────────────────────────────────────────────
  const initialTab = (searchParams.get('tab') as TabId) || 'topics'
  const [activeTab, setActiveTab] = useState<TabId>(initialTab)
  const [searchQuery, setSearchQuery] = useState('')
  const [showInternal, setShowInternal] = useState(false)

  // ── Data caches ────────────────────────────────────────────────────
  const [topics, setTopics] = useState<TopicSummary[] | null>(null)
  const [acls, setAcls] = useState<AclEntry[] | null>(null)
  const [groups, setGroups] = useState<ConsumerGroupSummary[] | null>(null)
  const [subjects, setSubjects] = useState<string[] | null>(null)
  const [tabLoading, setTabLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)


  // ── Load all data on mount ─────────────────────────────────────────
  const loadTopics = useCallback(async () => {
    if (!clusterId) return
    try {
      const res = await apiClient.listTopics(clusterId)
      setTopics(res.topics)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [clusterId])

  const loadAcls = useCallback(async () => {
    if (!clusterId) return
    try {
      const res = await apiClient.listAcls(clusterId)
      setAcls(res.acls)
    } catch (err) {
      // ACLs may fail if authorizer not configured — show empty with warning
      setAcls([])
    }
  }, [clusterId])

  const loadGroups = useCallback(async () => {
    if (!clusterId) return
    try {
      const res = await apiClient.listConsumerGroups(clusterId)
      setGroups(res.groups)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }, [clusterId])

  const loadSubjects = useCallback(async () => {
    if (!clusterId) return
    try {
      const res = await apiClient.listSchemaSubjects(clusterId)
      setSubjects(res.subjects)
    } catch {
      // Schema Registry may not be configured — show empty
      setSubjects([])
    }
  }, [clusterId])

  const loadAll = useCallback(async () => {
    setTabLoading(true)
    setError(null)
    await Promise.all([loadTopics(), loadAcls(), loadGroups(), loadSubjects()])
    setTabLoading(false)
  }, [loadTopics, loadAcls, loadGroups, loadSubjects])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  // ── Refresh active tab ─────────────────────────────────────────────
  const refreshTab = useCallback(async () => {
    setTabLoading(true)
    setError(null)
    if (activeTab === 'topics') await loadTopics()
    else if (activeTab === 'acls') await loadAcls()
    else if (activeTab === 'consumer-groups') await loadGroups()
    else if (activeTab === 'schemas') await loadSubjects()
    setTabLoading(false)
  }, [activeTab, loadTopics, loadAcls, loadGroups, loadSubjects])

  // ── Tab change handler ─────────────────────────────────────────────
  const handleTabChange = (tab: string) => {
    setActiveTab(tab as TabId)
    setSearchQuery('')
  }

  // ── Row click handlers (navigate to detail pages) ──────────────────
  const handleTopicClick = (topic: TopicSummary) => {
    navigate(`/self-service/${clusterId}/topics/${encodeURIComponent(topic.name)}`)
  }

  const handleGroupClick = (group: ConsumerGroupSummary) => {
    navigate(`/self-service/${clusterId}/consumer-groups/${encodeURIComponent(group.groupId)}`)
  }

  const handleSubjectClick = (subject: string) => {
    navigate(`/self-service/${clusterId}/schemas/${encodeURIComponent(subject)}`)
  }

  // ── Filtered data ──────────────────────────────────────────────────
  const filteredTopics = useMemo(() => {
    if (!topics) return []
    let result = topics
    if (!showInternal) {
      result = result.filter((t) => !t.name.startsWith('_'))
    }
    if (searchQuery) {
      const q = searchQuery.toLowerCase()
      result = result.filter((t) => t.name.toLowerCase().includes(q))
    }
    return result
  }, [topics, searchQuery, showInternal])

  const userTopicCount = topics ? topics.filter((t) => !t.name.startsWith('_')).length : null
  const internalTopicCount = topics ? topics.filter((t) => t.name.startsWith('_')).length : null

  const filteredAcls = useMemo(() => {
    if (!acls) return []
    if (!searchQuery) return acls
    const q = searchQuery.toLowerCase()
    return acls.filter(
      (a) => a.principal.toLowerCase().includes(q) || a.resourceName.toLowerCase().includes(q),
    )
  }, [acls, searchQuery])

  const filteredGroups = useMemo(() => {
    if (!groups) return []
    if (!searchQuery) return groups
    const q = searchQuery.toLowerCase()
    return groups.filter((g) => g.groupId.toLowerCase().includes(q))
  }, [groups, searchQuery])

  const filteredSubjects = useMemo(() => {
    if (!subjects) return []
    if (!searchQuery) return subjects
    const q = searchQuery.toLowerCase()
    return subjects.filter((s) => s.toLowerCase().includes(q))
  }, [subjects, searchQuery])

  // ── Column definitions ─────────────────────────────────────────────
  const topicColumns: ColumnDef<TopicSummary>[] = useMemo(
    () => [
      { key: 'name', label: 'Topic Name', sortable: true, truncate: true },
      { key: 'partitions', label: 'Partitions', sortable: true },
      {
        key: 'internal', label: 'Internal', sortable: true,
        render: (row) => (
          <span className={`status-pill status-pill--${row.internal ? 'degraded' : 'healthy'}`}>
            {row.internal ? 'Yes' : 'No'}
          </span>
        ),
      },
    ], [],
  )

  const aclColumns: ColumnDef<AclEntry>[] = useMemo(
    () => [
      { key: 'principal', label: 'Principal', sortable: true },
      { key: 'resourceType', label: 'Resource Type', sortable: true },
      { key: 'resourceName', label: 'Resource Name', sortable: true },
      { key: 'patternType', label: 'Pattern', sortable: true },
      { key: 'operation', label: 'Operation', sortable: true },
      { key: 'permission', label: 'Permission', sortable: true },
      { key: 'host', label: 'Host' },
    ], [],
  )

  const groupColumns: ColumnDef<ConsumerGroupSummary>[] = useMemo(
    () => [
      { key: 'groupId', label: 'Consumer Group ID', sortable: true, truncate: true },
      { key: 'state', label: 'Status', sortable: true },
      { key: 'type', label: 'Protocol', sortable: true },
    ], [],
  )

  const subjectColumns: ColumnDef<{ name: string }>[] = useMemo(
    () => [
      { key: 'name', label: 'Subject Name', sortable: true },
    ], [],
  )

  const subjectRows = filteredSubjects.map((s) => ({ name: s }))

  // ── Render ─────────────────────────────────────────────────────────
  return (
    <div className="self-service-console">
      <Breadcrumb items={[
        { label: 'Self-Service', to: '/self-service' },
        { label: cluster?.clusterName ?? clusterId ?? '' },
      ]} />

      <div className="console-header__title-row">
        <h1>{cluster?.clusterName ?? clusterId}</h1>
        {cluster && (
          <span className={`env-badge env-badge--${cluster.environment.toLowerCase()}`}>
            {formatEnvironment(cluster.environment)}
          </span>
        )}
        {cluster && (
          <span className={`status-pill status-pill--${cluster.status.toLowerCase()}`}>
            {cluster.status}
          </span>
        )}
      </div>

      <StatusOverview
        topicCount={userTopicCount}
        groupCount={groups ? groups.length : null}
        aclCount={acls ? acls.length : null}
        schemaCount={subjects ? subjects.length : null}
      />

      <TabBar
        tabs={[
          { id: 'topics', label: 'Topics', count: showInternal ? topics?.length : userTopicCount ?? undefined },
          { id: 'acls', label: 'ACLs', count: acls?.length },
          { id: 'consumer-groups', label: 'Consumer Groups', count: groups?.length },
          { id: 'schemas', label: 'Schema Registry', count: subjects?.length },
        ]}
        activeTab={activeTab}
        onTabChange={handleTabChange}
      />

      {error && (
        <div className="error-banner" style={{ marginBottom: 16 }}>
          <strong>Error:</strong> {error}
          <button className="btn btn--ghost btn--sm" onClick={() => setError(null)} style={{ marginLeft: 12 }}>Dismiss</button>
        </div>
      )}

      {/* ── Topics Tab ──────────────────────────────────────────────── */}
      {activeTab === 'topics' && (
        <div>
          <ActionToolbar
            left={
              <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                <SearchBar value={searchQuery} onChange={setSearchQuery} placeholder="Search topics..." />
                <label className="internal-toggle">
                  <input type="checkbox" checked={showInternal} onChange={(e) => setShowInternal(e.target.checked)} />
                  <span>Show internal ({internalTopicCount ?? 0})</span>
                </label>
              </div>
            }
            right={
              <>
                <button className="btn btn--primary btn--sm"
                  onClick={() => navigate(`/self-service/${clusterId}/TOPIC_CREATE`)}>
                  + Create Topic
                </button>
                <CsvExportButton data={filteredTopics}
                  columns={[{ key: 'name', label: 'Topic Name' }, { key: 'partitions', label: 'Partitions' }, { key: 'internal', label: 'Internal' }]}
                  filename={`topics-${cluster?.clusterName || clusterId}`} />
                <button className="btn btn--ghost btn--sm" onClick={refreshTab} disabled={tabLoading}>
                  {tabLoading ? 'Loading...' : 'Refresh'}
                </button>
              </>
            }
          />
          <DataTable columns={topicColumns} data={filteredTopics} onRowClick={handleTopicClick}
            rowKey={(t) => t.name} loading={tabLoading && !topics} emptyMessage="No topics found." />
        </div>
      )}

      {/* ── ACLs Tab ────────────────────────────────────────────────── */}
      {activeTab === 'acls' && (
        <div>
          <ActionToolbar
            left={<SearchBar value={searchQuery} onChange={setSearchQuery} placeholder="Search by principal or resource..." />}
            right={
              <>
                <button className="btn btn--primary btn--sm"
                  onClick={() => navigate(`/self-service/${clusterId}/ACL_GRANT`)}>
                  + Grant ACL
                </button>
                <button className="btn btn--secondary btn--sm"
                  onClick={() => navigate(`/self-service/${clusterId}/ACL_REMOVE`)}>
                  Remove ACL
                </button>
                <CsvExportButton data={filteredAcls}
                  columns={[{ key: 'principal', label: 'Principal' }, { key: 'resourceType', label: 'Resource Type' }, { key: 'resourceName', label: 'Resource Name' }, { key: 'patternType', label: 'Pattern' }, { key: 'operation', label: 'Operation' }, { key: 'permission', label: 'Permission' }, { key: 'host', label: 'Host' }]}
                  filename={`acls-${cluster?.clusterName || clusterId}`} />
                <button className="btn btn--ghost btn--sm" onClick={refreshTab} disabled={tabLoading}>
                  {tabLoading ? 'Loading...' : 'Refresh'}
                </button>
              </>
            }
          />
          <DataTable columns={aclColumns} data={filteredAcls}
            loading={tabLoading && !acls} emptyMessage="No ACLs found." />
        </div>
      )}

      {/* ── Consumer Groups Tab ─────────────────────────────────────── */}
      {activeTab === 'consumer-groups' && (
        <div>
          <ActionToolbar
            left={<SearchBar value={searchQuery} onChange={setSearchQuery} placeholder="Search consumer groups..." />}
            right={
              <>
                <CsvExportButton data={filteredGroups}
                  columns={[{ key: 'groupId', label: 'Consumer Group ID' }, { key: 'state', label: 'Status' }, { key: 'type', label: 'Protocol' }]}
                  filename={`consumer-groups-${cluster?.clusterName || clusterId}`} />
                <button className="btn btn--ghost btn--sm" onClick={refreshTab} disabled={tabLoading}>
                  {tabLoading ? 'Loading...' : 'Refresh'}
                </button>
              </>
            }
          />
          <DataTable columns={groupColumns} data={filteredGroups} onRowClick={handleGroupClick}
            rowKey={(g) => g.groupId} loading={tabLoading && !groups} emptyMessage="No consumer groups found." />
        </div>
      )}

      {/* ── Schema Registry Tab ─────────────────────────────────────── */}
      {activeTab === 'schemas' && (
        <div>
          <ActionToolbar
            left={<SearchBar value={searchQuery} onChange={setSearchQuery} placeholder="Search subjects..." />}
            right={
              <button className="btn btn--ghost btn--sm" onClick={refreshTab} disabled={tabLoading}>
                {tabLoading ? 'Loading...' : 'Refresh'}
              </button>
            }
          />
          <DataTable columns={subjectColumns} data={subjectRows}
            onRowClick={(row) => handleSubjectClick(row.name)}
            rowKey={(r) => r.name} loading={tabLoading && !subjects} emptyMessage="No schema subjects found." />
        </div>
      )}

    </div>
  )
}
