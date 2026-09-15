import { Navigate, createBrowserRouter } from 'react-router'
import { RequireAuth, RequireRole } from './auth/guards'
import { AppShell } from './components/AppShell'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { NewTicketPage } from './pages/NewTicketPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { OrganizationsPage } from './pages/OrganizationsPage'
import { ProfilePage } from './pages/ProfilePage'
import { TicketDetailPage } from './pages/TicketDetailPage'
import { TicketsPage } from './pages/TicketsPage'
import { UsersPage } from './pages/UsersPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth><AppShell /></RequireAuth>,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      { path: '/dashboard', element: <DashboardPage /> },
      { path: '/tickets', element: <TicketsPage /> },
      { path: '/tickets/new', element: <RequireRole roles={['CUSTOMER']}><NewTicketPage /></RequireRole> },
      { path: '/tickets/:id', element: <TicketDetailPage /> },
      { path: '/users', element: <RequireRole roles={['ORG_ADMIN', 'SUPER_ADMIN']}><UsersPage /></RequireRole> },
      { path: '/organizations', element: <RequireRole roles={['SUPER_ADMIN']}><OrganizationsPage /></RequireRole> },
      { path: '/profile', element: <ProfilePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
