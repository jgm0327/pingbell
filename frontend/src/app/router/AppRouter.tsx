import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '../../shared/components/ProtectedRoute'
import { AppLayout } from '../../shared/layout/AppLayout'
import { LoginPage } from '../../pages/LoginPage'
import { SignupPage } from '../../pages/SignupPage'
import { DashboardPage } from '../../pages/DashboardPage'
import { MonitorListPage } from '../../pages/MonitorListPage'
import { MonitorFormPage } from '../../pages/MonitorFormPage'
import { MonitorDetailPage } from '../../pages/MonitorDetailPage'
import { IncidentListPage } from '../../pages/IncidentListPage'
import { NotificationChannelPage } from '../../pages/NotificationChannelPage'
import { SettingsPage } from '../../pages/SettingsPage'

export function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/monitors" element={<MonitorListPage />} />
          <Route path="/monitors/new" element={<MonitorFormPage />} />
          <Route path="/monitors/:monitorId" element={<MonitorDetailPage />} />
          <Route path="/monitors/:monitorId/edit" element={<MonitorFormPage />} />
          <Route path="/incidents" element={<IncidentListPage />} />
          <Route path="/notification-channels" element={<NotificationChannelPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
