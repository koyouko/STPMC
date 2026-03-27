import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from './layouts/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { ClusterDetailPage } from './pages/ClusterDetailPage'
import { ClusterOnboardingPage } from './pages/ClusterOnboardingPage'
import { ClusterEditPage } from './pages/ClusterEditPage'
import ClusterMetricsPage from './pages/ClusterMetricsPage'
import AuditLogPage from './pages/AuditLogPage'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/clusters/new', element: <ClusterOnboardingPage /> },
      { path: '/clusters/:clusterId', element: <ClusterDetailPage /> },
      { path: '/clusters/:clusterId/edit', element: <ClusterEditPage /> },
      { path: '/metrics', element: <ClusterMetricsPage /> },
      { path: '/audit', element: <AuditLogPage /> },
    ],
  },
])
