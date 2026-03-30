import { createBrowserRouter, useRouteError, Link } from 'react-router-dom'
import { AppLayout } from './layouts/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { ClusterDetailPage } from './pages/ClusterDetailPage'
import { ClusterOnboardingPage } from './pages/ClusterOnboardingPage'
import { ClusterEditPage } from './pages/ClusterEditPage'
import ClusterMetricsPage from './pages/ClusterMetricsPage'
import AuditLogPage from './pages/AuditLogPage'

function ErrorBoundary() {
  const error = useRouteError()
  if (error instanceof Error) {
    console.error('Route error:', error)
  }
  return (
    <div style={{ padding: '2rem' }}>
      <h1>Something went wrong</h1>
      <p>An unexpected error occurred. Please try refreshing the page.</p>
      <Link to="/">Back to dashboard</Link>
    </div>
  )
}

function NotFound() {
  return (
    <div style={{ padding: '2rem' }}>
      <h1>Page not found</h1>
      <p>The page you are looking for does not exist.</p>
      <Link to="/">Back to dashboard</Link>
    </div>
  )
}

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    errorElement: <ErrorBoundary />,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/clusters/new', element: <ClusterOnboardingPage /> },
      { path: '/clusters/:clusterId', element: <ClusterDetailPage /> },
      { path: '/clusters/:clusterId/edit', element: <ClusterEditPage /> },
      { path: '/metrics', element: <ClusterMetricsPage /> },
      { path: '/audit', element: <AuditLogPage /> },
      { path: '*', element: <NotFound /> },
    ],
  },
])
