import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from './layouts/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { ClusterDetailPage } from './pages/ClusterDetailPage'
import { ClusterOnboardingPage } from './pages/ClusterOnboardingPage'
import { ClusterEditPage } from './pages/ClusterEditPage'
import SelfServicePage from './pages/SelfServicePage'
import SelfServiceClusterPage from './pages/SelfServiceClusterPage'
import SelfServiceTaskPage from './pages/SelfServiceTaskPage'
import TopicDetailPage from './pages/TopicDetailPage'
import ConsumerGroupDetailPage from './pages/ConsumerGroupDetailPage'
import SchemaSubjectDetailPage from './pages/SchemaSubjectDetailPage'
import AuditLogPage from './pages/AuditLogPage'

export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/clusters/new', element: <ClusterOnboardingPage /> },
      { path: '/clusters/:clusterId', element: <ClusterDetailPage /> },
      { path: '/clusters/:clusterId/edit', element: <ClusterEditPage /> },
      { path: '/self-service', element: <SelfServicePage /> },
      { path: '/self-service/:clusterId', element: <SelfServiceClusterPage /> },
      // Detail pages — must come before the :taskType wildcard
      { path: '/self-service/:clusterId/topics/:topicName', element: <TopicDetailPage /> },
      { path: '/self-service/:clusterId/consumer-groups/:groupId', element: <ConsumerGroupDetailPage /> },
      { path: '/self-service/:clusterId/schemas/:subject', element: <SchemaSubjectDetailPage /> },
      // Task execution (wildcard — matches TOPIC_CREATE, ACL_GRANT, etc.)
      { path: '/self-service/:clusterId/:taskType', element: <SelfServiceTaskPage /> },
      { path: '/audit', element: <AuditLogPage /> },
    ],
  },
])
