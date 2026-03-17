interface StatCardProps {
  label: string
  value: string | number
  tone?: 'default' | 'healthy' | 'degraded' | 'down'
  active?: boolean
  onClick?: () => void
}

export function StatCard({ label, value, tone = 'default', active = false, onClick }: StatCardProps) {
  const className = `stat-card stat-card--${tone} ${active ? 'stat-card--active' : ''}`

  if (onClick) {
    return (
      <button type="button" className={`${className} stat-card--interactive`} onClick={onClick}>
        <span>{label}</span>
        <strong>{value}</strong>
      </button>
    )
  }

  return (
    <article className={className}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}
