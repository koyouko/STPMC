import type { ClusterEnvironment } from '../types/api'

export function formatTimestamp(value: string | null, emptyLabel = 'No snapshot yet') {
  if (!value) return emptyLabel
  return new Intl.DateTimeFormat('en-CA', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function formatLabel(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ')
}

export function formatEnvironment(environment: ClusterEnvironment) {
  return environment === 'PROD' ? 'Production' : 'Non-Prod'
}
