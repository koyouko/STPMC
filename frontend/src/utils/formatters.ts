import type { ClusterEnvironment } from '../types/api'

export function formatTimestamp(value: string | null, emptyLabel = 'No snapshot yet') {
  if (!value) return emptyLabel
  return new Intl.DateTimeFormat('en-CA', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

const LABEL_OVERRIDES: Record<string, string> = {
  KRAFT: 'KRaft',
  MDS: 'MDS',
  SCHEMA_REGISTRY: 'Schema Registry',
  CONTROL_CENTER: 'Control Center',
}

export function formatLabel(value: string) {
  if (LABEL_OVERRIDES[value]) return LABEL_OVERRIDES[value]
  return value
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ')
}

export function formatEnvironment(environment: ClusterEnvironment) {
  return environment === 'PROD' ? 'Production' : 'Non-Prod'
}
