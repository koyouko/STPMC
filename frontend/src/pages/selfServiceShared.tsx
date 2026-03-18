import { useState } from 'react'
import { apiClient } from '../api/client'
import type {
  SelfServiceTaskType,
  SelfServiceCategory,
  TopicListResponse,
  TopicDescribeResponse,
  TopicConfigDescribeResponse,
  TopicConfigAlterResponse,
  TopicDataDumpResponse,
  CreateTopicResponse,
  DeleteTopicResponse,
  TopicPurgeResponse,
  IncreasePartitionsResponse,
  MessageCountResponse,
  AclListResponse,
  AclOperationResponse,
  ConsumerGroupListResponse,
  ConsumerGroupDescribeResponse,
  ConsumerGroupDeleteResponse,
  OffsetResetResponse,
  DumpedMessage,
} from '../types/api'

// ── Field Definition ────────────────────────────────────────────────

export interface FieldDef {
  name: string
  label: string
  type: 'text' | 'number' | 'select' | 'textarea' | 'checkbox'
  options?: string[]
  required?: boolean
  defaultValue?: string | number | boolean
  max?: number
  dynamicOptions?: 'topics' | 'groups'
  showWhen?: (params: Record<string, any>) => boolean
}

// ── Task Config ─────────────────────────────────────────────────────

export interface TaskConfig {
  displayName: string
  category: SelfServiceCategory
  readOnly: boolean
  fields: FieldDef[]
  execute: (clusterId: string, params: Record<string, any>) => Promise<any>
}

export const TASK_CONFIGS: Record<SelfServiceTaskType, TaskConfig> = {
  TOPIC_LIST: {
    displayName: 'List Topics',
    category: 'TOPIC',
    readOnly: true,
    fields: [],
    execute: (cid) => apiClient.listTopics(cid),
  },
  TOPIC_DESCRIBE: {
    displayName: 'Describe Topic',
    category: 'TOPIC',
    readOnly: true,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
    ],
    execute: (cid, p) => apiClient.describeTopic(cid, p.topicName),
  },
  TOPIC_CREATE: {
    displayName: 'Create Topic',
    category: 'TOPIC',
    readOnly: false,
    fields: [
      { name: 'topicName', label: 'Topic Name', type: 'text', required: true },
      { name: 'numPartitions', label: 'Partitions', type: 'number', defaultValue: 3, required: true },
      { name: 'replicationFactor', label: 'Replication Factor', type: 'number', defaultValue: 1, required: true },
      { name: 'configs', label: 'Configs (JSON)', type: 'textarea' },
    ],
    execute: (cid, p) => {
      const configs = p.configs ? JSON.parse(p.configs) : undefined
      return apiClient.createTopic(cid, {
        topicName: p.topicName,
        numPartitions: Number(p.numPartitions),
        replicationFactor: Number(p.replicationFactor),
        configs,
      })
    },
  },
  TOPIC_DELETE: {
    displayName: 'Delete Topic',
    category: 'TOPIC',
    readOnly: false,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
    ],
    execute: (cid, p) => apiClient.deleteTopic(cid, p.topicName),
  },
  TOPIC_PURGE: {
    displayName: 'Purge Topic',
    category: 'TOPIC',
    readOnly: false,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
    ],
    execute: (cid, p) => apiClient.purgeTopic(cid, p.topicName),
  },
  TOPIC_INCREASE_PARTITIONS: {
    displayName: 'Increase Partitions',
    category: 'TOPIC',
    readOnly: false,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
      { name: 'newPartitionCount', label: 'New Partition Count', type: 'number', required: true },
    ],
    execute: (cid, p) =>
      apiClient.increasePartitions(cid, p.topicName, Number(p.newPartitionCount)),
  },
  TOPIC_MESSAGE_COUNT: {
    displayName: 'Message Count',
    category: 'TOPIC',
    readOnly: true,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
    ],
    execute: (cid, p) => apiClient.getMessageCount(cid, p.topicName),
  },
  TOPIC_CONFIG_DESCRIBE: {
    displayName: 'Describe Topic Config',
    category: 'TOPIC',
    readOnly: true,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
    ],
    execute: (cid, p) => apiClient.describeTopicConfig(cid, p.topicName),
  },
  TOPIC_CONFIG_ALTER: {
    displayName: 'Alter Topic Config',
    category: 'TOPIC',
    readOnly: false,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
      { name: 'configsToSet', label: 'Configs to Set (JSON)', type: 'textarea' },
      { name: 'configsToDelete', label: 'Configs to Delete (comma-separated)', type: 'text' },
    ],
    execute: (cid, p) => {
      const configsToSet = p.configsToSet ? JSON.parse(p.configsToSet) : {}
      const configsToDelete = p.configsToDelete
        ? (p.configsToDelete as string).split(',').map((s: string) => s.trim()).filter(Boolean)
        : []
      return apiClient.alterTopicConfig(cid, p.topicName, configsToSet, configsToDelete)
    },
  },
  ACL_LIST: {
    displayName: 'List ACLs',
    category: 'ACL',
    readOnly: true,
    fields: [],
    execute: (cid) => apiClient.listAcls(cid),
  },
  ACL_DESCRIBE: {
    displayName: 'Describe ACLs',
    category: 'ACL',
    readOnly: true,
    fields: [
      { name: 'principal', label: 'Principal', type: 'text' },
      { name: 'resourceName', label: 'Resource Name', type: 'text' },
      { name: 'resourceType', label: 'Resource Type', type: 'select', options: ['', 'TOPIC', 'GROUP', 'CLUSTER', 'TRANSACTIONAL_ID'] },
    ],
    execute: (cid, p) =>
      apiClient.describeAcls(cid, p.principal || undefined, p.resourceName || undefined, p.resourceType || undefined),
  },
  ACL_GRANT: {
    displayName: 'Grant ACL',
    category: 'ACL',
    readOnly: false,
    fields: [
      { name: 'principal', label: 'Principal', type: 'text', required: true },
      { name: 'resourceName', label: 'Resource Name', type: 'text', required: true },
      { name: 'resourceType', label: 'Resource Type', type: 'select', required: true, options: ['TOPIC', 'GROUP', 'CLUSTER', 'TRANSACTIONAL_ID'] },
      { name: 'patternType', label: 'Pattern Type', type: 'select', required: true, options: ['LITERAL', 'PREFIXED'] },
      { name: 'operation', label: 'Operation', type: 'select', required: true, options: ['READ', 'WRITE', 'CREATE', 'DELETE', 'ALTER', 'DESCRIBE', 'CLUSTER_ACTION', 'ALL'] },
      { name: 'permission', label: 'Permission', type: 'select', required: true, options: ['ALLOW', 'DENY'] },
    ],
    execute: (cid, p) =>
      apiClient.grantAcl(cid, {
        principal: p.principal,
        resourceName: p.resourceName,
        resourceType: p.resourceType,
        patternType: p.patternType,
        operation: p.operation,
        permission: p.permission,
      }),
  },
  ACL_REMOVE: {
    displayName: 'Remove ACL',
    category: 'ACL',
    readOnly: false,
    fields: [
      { name: 'principal', label: 'Principal', type: 'text', required: true },
      { name: 'resourceName', label: 'Resource Name', type: 'text' },
      { name: 'resourceType', label: 'Resource Type', type: 'select', options: ['', 'TOPIC', 'GROUP', 'CLUSTER', 'TRANSACTIONAL_ID'] },
      { name: 'patternType', label: 'Pattern Type', type: 'select', options: ['', 'LITERAL', 'PREFIXED'] },
      { name: 'operation', label: 'Operation', type: 'select', options: ['', 'READ', 'WRITE', 'CREATE', 'DELETE', 'ALTER', 'DESCRIBE', 'CLUSTER_ACTION', 'ALL'] },
      { name: 'permission', label: 'Permission', type: 'select', options: ['', 'ALLOW', 'DENY'] },
    ],
    execute: (cid, p) =>
      apiClient.removeAcl(cid, {
        principal: p.principal,
        resourceName: p.resourceName || undefined,
        resourceType: p.resourceType || undefined,
        patternType: p.patternType || undefined,
        operation: p.operation || undefined,
        permission: p.permission || undefined,
      }),
  },
  CONSUMER_GROUP_LIST: {
    displayName: 'List Consumer Groups',
    category: 'CONSUMER_GROUP',
    readOnly: true,
    fields: [],
    execute: (cid) => apiClient.listConsumerGroups(cid),
  },
  CONSUMER_GROUP_DESCRIBE: {
    displayName: 'Describe Consumer Group',
    category: 'CONSUMER_GROUP',
    readOnly: true,
    fields: [
      { name: 'groupId', label: 'Consumer Group', type: 'select', required: true, dynamicOptions: 'groups' },
    ],
    execute: (cid, p) => apiClient.describeConsumerGroup(cid, p.groupId),
  },
  CONSUMER_GROUP_DELETE: {
    displayName: 'Delete Consumer Group',
    category: 'CONSUMER_GROUP',
    readOnly: false,
    fields: [
      { name: 'groupId', label: 'Consumer Group', type: 'select', required: true, dynamicOptions: 'groups' },
    ],
    execute: (cid, p) => apiClient.deleteConsumerGroup(cid, p.groupId),
  },
  CONSUMER_GROUP_OFFSETS: {
    displayName: 'Reset Consumer Group Offsets',
    category: 'CONSUMER_GROUP',
    readOnly: false,
    fields: [
      { name: 'groupId', label: 'Consumer Group', type: 'select', required: true, dynamicOptions: 'groups' },
      { name: 'resetType', label: 'Reset Type', type: 'select', required: true, options: ['earliest', 'latest', 'specific'], defaultValue: 'earliest' },
      {
        name: 'partitionOffsets',
        label: 'Partition Offsets (JSON, e.g. {"0": 100, "1": 200})',
        type: 'textarea',
        showWhen: (params) => params.resetType === 'specific',
      },
    ],
    execute: (cid, p) => {
      const offsets = p.resetType === 'specific' && p.partitionOffsets
        ? JSON.parse(p.partitionOffsets)
        : undefined
      return apiClient.resetConsumerGroupOffsets(cid, p.groupId, p.resetType, offsets)
    },
  },
  TOPIC_DATA_DUMP: {
    displayName: 'Data Dump',
    category: 'DATA',
    readOnly: true,
    fields: [
      { name: 'topicName', label: 'Topic', type: 'select', required: true, dynamicOptions: 'topics' },
      { name: 'maxMessages', label: 'Max Messages', type: 'number', defaultValue: 10, max: 100 },
      { name: 'partition', label: 'Partition (optional)', type: 'number' },
    ],
    execute: (cid, p) =>
      apiClient.dumpTopicMessages(
        cid,
        p.topicName,
        Number(p.maxMessages) || 10,
        p.partition !== '' && p.partition !== undefined ? Number(p.partition) : undefined,
      ),
  },
}

// ── Category metadata ───────────────────────────────────────────────

export const CATEGORY_ORDER: SelfServiceCategory[] = ['TOPIC', 'ACL', 'CONSUMER_GROUP', 'DATA']

export const CATEGORY_LABELS: Record<SelfServiceCategory, string> = {
  TOPIC: 'Topic Operations',
  ACL: 'ACL Management',
  CONSUMER_GROUP: 'Consumer Groups',
  DATA: 'Data Operations',
}

export type TabId = 'topics' | 'acls' | 'consumer-groups' | 'data'

export const TAB_LABELS: Record<TabId, string> = {
  topics: 'Topics',
  acls: 'ACLs',
  'consumer-groups': 'Consumer Groups',
  data: 'Data',
}

export const TASK_TYPE_TO_TAB: Record<SelfServiceTaskType, TabId> = {
  TOPIC_LIST: 'topics',
  TOPIC_DESCRIBE: 'topics',
  TOPIC_CREATE: 'topics',
  TOPIC_DELETE: 'topics',
  TOPIC_PURGE: 'topics',
  TOPIC_INCREASE_PARTITIONS: 'topics',
  TOPIC_MESSAGE_COUNT: 'topics',
  TOPIC_CONFIG_DESCRIBE: 'topics',
  TOPIC_CONFIG_ALTER: 'topics',
  ACL_LIST: 'acls',
  ACL_DESCRIBE: 'acls',
  ACL_GRANT: 'acls',
  ACL_REMOVE: 'acls',
  CONSUMER_GROUP_LIST: 'consumer-groups',
  CONSUMER_GROUP_DESCRIBE: 'consumer-groups',
  CONSUMER_GROUP_DELETE: 'consumer-groups',
  CONSUMER_GROUP_OFFSETS: 'consumer-groups',
  TOPIC_DATA_DUMP: 'data',
}

// ── Result Renderers ──────────────────────────────────────────────────

export function renderResultForTask(taskType: SelfServiceTaskType, result: any) {
  switch (taskType) {
    case 'TOPIC_LIST':
      return renderTopicList(result as TopicListResponse)
    case 'TOPIC_DESCRIBE':
      return renderTopicDescribe(result as TopicDescribeResponse)
    case 'TOPIC_CREATE':
    case 'TOPIC_DELETE':
    case 'TOPIC_PURGE':
      return renderSuccessMessage((result as CreateTopicResponse | DeleteTopicResponse | TopicPurgeResponse).message)
    case 'TOPIC_INCREASE_PARTITIONS':
      return renderIncreasePartitions(result as IncreasePartitionsResponse)
    case 'TOPIC_MESSAGE_COUNT':
      return renderMessageCount(result as MessageCountResponse)
    case 'TOPIC_CONFIG_DESCRIBE':
      return renderTopicConfigDescribe(result as TopicConfigDescribeResponse)
    case 'TOPIC_CONFIG_ALTER':
      return renderTopicConfigAlter(result as TopicConfigAlterResponse)
    case 'ACL_LIST':
    case 'ACL_DESCRIBE':
      return renderAclList(result as AclListResponse)
    case 'ACL_GRANT':
    case 'ACL_REMOVE':
      return renderAclOperation(result as AclOperationResponse)
    case 'CONSUMER_GROUP_LIST':
      return renderConsumerGroupList(result as ConsumerGroupListResponse)
    case 'CONSUMER_GROUP_DESCRIBE':
      return renderConsumerGroupDescribe(result as ConsumerGroupDescribeResponse)
    case 'CONSUMER_GROUP_DELETE':
      return renderSuccessMessage((result as ConsumerGroupDeleteResponse).message)
    case 'CONSUMER_GROUP_OFFSETS':
      return renderOffsetReset(result as OffsetResetResponse)
    case 'TOPIC_DATA_DUMP':
      return renderDataDump(result as TopicDataDumpResponse)
    default:
      return <pre className="result-raw">{JSON.stringify(result, null, 2)}</pre>
  }
}

function renderSuccessMessage(message: string) {
  return <div className="confirm-banner">{message}</div>
}

function renderTopicList(data: TopicListResponse) {
  return (
    <table className="result-table">
      <thead>
        <tr>
          <th>Topic Name</th>
          <th>Partitions</th>
          <th>Internal</th>
        </tr>
      </thead>
      <tbody>
        {data.topics.map((t) => (
          <tr key={t.name}>
            <td>{t.name}</td>
            <td>{t.partitions}</td>
            <td>{t.internal ? 'Yes' : 'No'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function renderTopicDescribe(data: TopicDescribeResponse) {
  return (
    <div className="describe-result">
      <dl className="kv-list">
        <dt>Topic</dt><dd>{data.topicName}</dd>
        <dt>Partitions</dt><dd>{data.partitions}</dd>
        <dt>Replication Factor</dt><dd>{data.replicationFactor}</dd>
      </dl>
      {data.partitionInfos.length > 0 && (
        <>
          <h3>Partition Details</h3>
          <table className="result-table">
            <thead>
              <tr>
                <th>Partition</th>
                <th>Leader</th>
                <th>Replicas</th>
                <th>ISR</th>
              </tr>
            </thead>
            <tbody>
              {data.partitionInfos.map((p) => (
                <tr key={p.partition}>
                  <td>{p.partition}</td>
                  <td>{p.leader}</td>
                  <td>{p.replicas.join(', ')}</td>
                  <td>{p.isr.join(', ')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
      {Object.keys(data.configs).length > 0 && (
        <>
          <h3>Configs</h3>
          <table className="result-table">
            <thead>
              <tr><th>Key</th><th>Value</th></tr>
            </thead>
            <tbody>
              {Object.entries(data.configs).map(([k, v]) => (
                <tr key={k}><td>{k}</td><td>{v}</td></tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}

function renderIncreasePartitions(data: IncreasePartitionsResponse) {
  return (
    <div className="confirm-banner">
      Partitions increased from {data.previousCount} to {data.newCount} for topic {data.topicName}.
    </div>
  )
}

function renderMessageCount(data: MessageCountResponse) {
  return (
    <div className="describe-result">
      <dl className="kv-list">
        <dt>Topic</dt><dd>{data.topicName}</dd>
        <dt>Total Messages</dt><dd>{data.totalCount.toLocaleString()}</dd>
      </dl>
      <table className="result-table">
        <thead>
          <tr><th>Partition</th><th>Count</th></tr>
        </thead>
        <tbody>
          {Object.entries(data.partitionCounts).map(([p, c]) => (
            <tr key={p}><td>{p}</td><td>{(c as number).toLocaleString()}</td></tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function renderTopicConfigDescribe(data: TopicConfigDescribeResponse) {
  return (
    <table className="result-table">
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
        {data.configs.map((c) => (
          <tr key={c.name}>
            <td>{c.name}</td>
            <td>{c.isSensitive ? '******' : c.value}</td>
            <td>{c.source}</td>
            <td>{c.isDefault ? 'Yes' : 'No'}</td>
            <td>{c.isReadOnly ? 'Yes' : 'No'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function renderTopicConfigAlter(data: TopicConfigAlterResponse) {
  return (
    <div className="describe-result">
      <div className="confirm-banner">{data.message}</div>
      {Object.keys(data.updatedConfigs).length > 0 && (
        <table className="result-table">
          <thead>
            <tr><th>Config</th><th>New Value</th></tr>
          </thead>
          <tbody>
            {Object.entries(data.updatedConfigs).map(([k, v]) => (
              <tr key={k}><td>{k}</td><td>{v}</td></tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function renderAclList(data: AclListResponse) {
  if (data.acls.length === 0) {
    return <div className="empty-state"><p>No ACLs found.</p></div>
  }
  return (
    <table className="result-table">
      <thead>
        <tr>
          <th>Principal</th>
          <th>Resource Type</th>
          <th>Resource Name</th>
          <th>Pattern</th>
          <th>Operation</th>
          <th>Permission</th>
          <th>Host</th>
        </tr>
      </thead>
      <tbody>
        {data.acls.map((a, i) => (
          <tr key={i}>
            <td>{a.principal}</td>
            <td>{a.resourceType}</td>
            <td>{a.resourceName}</td>
            <td>{a.patternType}</td>
            <td>{a.operation}</td>
            <td>{a.permission}</td>
            <td>{a.host}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function renderAclOperation(data: AclOperationResponse) {
  return (
    <div className="confirm-banner">
      {data.message} (affected {data.affectedCount} entries)
    </div>
  )
}

function renderConsumerGroupList(data: ConsumerGroupListResponse) {
  if (data.groups.length === 0) {
    return <div className="empty-state"><p>No consumer groups found.</p></div>
  }
  return (
    <table className="result-table">
      <thead>
        <tr>
          <th>Group ID</th>
          <th>State</th>
          <th>Type</th>
        </tr>
      </thead>
      <tbody>
        {data.groups.map((g) => (
          <tr key={g.groupId}>
            <td>{g.groupId}</td>
            <td>{g.state}</td>
            <td>{g.type}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function renderConsumerGroupDescribe(data: ConsumerGroupDescribeResponse) {
  return (
    <div className="describe-result">
      <dl className="kv-list">
        <dt>Group ID</dt><dd>{data.groupId}</dd>
        <dt>State</dt><dd>{data.state}</dd>
        <dt>Coordinator</dt><dd>{data.coordinator}</dd>
      </dl>

      {data.members.length > 0 && (
        <>
          <h3>Members</h3>
          <table className="result-table">
            <thead>
              <tr>
                <th>Member ID</th>
                <th>Client ID</th>
                <th>Host</th>
                <th>Assignments</th>
              </tr>
            </thead>
            <tbody>
              {data.members.map((m) => (
                <tr key={m.memberId}>
                  <td>{m.memberId}</td>
                  <td>{m.clientId}</td>
                  <td>{m.host}</td>
                  <td>
                    {m.assignments.map((a) => `${a.topic}:${a.partition}`).join(', ')}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {data.offsets.length > 0 && (
        <>
          <h3>Offsets</h3>
          <table className="result-table">
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
              {data.offsets.map((o, i) => (
                <tr key={i}>
                  <td>{o.topic}</td>
                  <td>{o.partition}</td>
                  <td>{o.currentOffset.toLocaleString()}</td>
                  <td>{o.logEndOffset.toLocaleString()}</td>
                  <td>{o.lag.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}

function renderOffsetReset(data: OffsetResetResponse) {
  const entries = Object.entries(data.updatedOffsets)
  return (
    <div className="describe-result">
      <div className="confirm-banner">Offsets reset for group {data.groupId}.</div>
      {entries.map(([topic, partitions]) => (
        <div key={topic}>
          <h3>{topic}</h3>
          <table className="result-table">
            <thead>
              <tr><th>Partition</th><th>New Offset</th></tr>
            </thead>
            <tbody>
              {Object.entries(partitions).map(([p, off]) => (
                <tr key={p}><td>{p}</td><td>{(off as number).toLocaleString()}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      ))}
    </div>
  )
}

function renderDataDump(data: TopicDataDumpResponse) {
  if (data.messages.length === 0) {
    return <div className="empty-state"><p>No messages found.</p></div>
  }
  return (
    <div className="data-dump-result">
      {data.messages.map((msg, i) => (
        <MessageRow key={i} message={msg} index={i} />
      ))}
    </div>
  )
}

export function MessageRow({ message, index }: { message: DumpedMessage; index: number }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className={`message-row ${expanded ? 'message-row--expanded' : ''}`}>
      <div
        className="message-row__header"
        onClick={() => setExpanded(!expanded)}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            setExpanded(!expanded)
          }
        }}
      >
        <span className="message-row__index">#{index + 1}</span>
        <span className="message-row__partition">P{message.partition}</span>
        <span className="message-row__offset">Offset {message.offset}</span>
        <span className="message-row__ts">
          {new Date(message.timestamp).toISOString()}
        </span>
        <span className="message-row__toggle">{expanded ? '\u25BC' : '\u25B6'}</span>
      </div>
      {expanded && (
        <div className="message-row__body">
          <dl className="kv-list">
            <dt>Key</dt><dd>{message.key ?? '(null)'}</dd>
            <dt>Value</dt>
            <dd>
              <pre className="message-value">{message.value ?? '(null)'}</pre>
            </dd>
          </dl>
          {Object.keys(message.headers).length > 0 && (
            <>
              <h4>Headers</h4>
              <table className="result-table">
                <thead>
                  <tr><th>Key</th><th>Value</th></tr>
                </thead>
                <tbody>
                  {Object.entries(message.headers).map(([k, v]) => (
                    <tr key={k}><td>{k}</td><td>{v}</td></tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </div>
      )}
    </div>
  )
}
