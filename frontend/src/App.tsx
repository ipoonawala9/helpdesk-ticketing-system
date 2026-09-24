import { lazy, Suspense, type ReactNode } from 'react'
import { createBrowserRouter } from 'react-router'
import { RequireAuth, RequireRole } from './auth/guards'
import { AppShell } from './components/AppShell'
import { LoadingState } from './components/Feedback'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { LandingPage } from './pages/LandingPage'
import { LoginPage } from './pages/LoginPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'

// Screens behind sign-in are fetched the first time they are opened, so a
// visitor who only sees the landing or sign-in page never downloads them.
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const TicketsPage = lazy(() => import('./pages/TicketsPage').then((m) => ({ default: m.TicketsPage })))
const TicketDetailPage = lazy(() => import('./pages/TicketDetailPage').then((m) => ({ default: m.TicketDetailPage })))
const NewTicketPage = lazy(() => import('./pages/NewTicketPage').then((m) => ({ default: m.NewTicketPage })))
const UsersPage = lazy(() => import('./pages/UsersPage').then((m) => ({ default: m.UsersPage })))
const OrganizationsPage = lazy(() => import('./pages/OrganizationsPage').then((m) => ({ default: m.OrganizationsPage })))
const ProfilePage = lazy(() => import('./pages/ProfilePage').then((m) => ({ default: m.ProfilePage })))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })))

function Screen({ children }: { children: ReactNode }) {
  return <Suspense fallback={<div className="page"><LoadingState /></div>}>{children}</Suspense>
}

export const router = createBrowserRouter([
  { path: '/', element: <LandingPage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset-password', element: <ResetPasswordPage /> },
  {
    element: <RequireAuth><AppShell /></RequireAuth>,
    children: [
      { path: '/dashboard', element: <Screen><DashboardPage /></Screen> },
      { path: '/tickets', element: <Screen><TicketsPage /></Screen> },
      { path: '/tickets/new', element: <RequireRole roles={['CUSTOMER']}><Screen><NewTicketPage /></Screen></RequireRole> },
      { path: '/tickets/:id', element: <Screen><TicketDetailPage /></Screen> },
      { path: '/users', element: <RequireRole roles={['ORG_ADMIN', 'SUPER_ADMIN']}><Screen><UsersPage /></Screen></RequireRole> },
      { path: '/organizations', element: <RequireRole roles={['SUPER_ADMIN']}><Screen><OrganizationsPage /></Screen></RequireRole> },
      { path: '/profile', element: <Screen><ProfilePage /></Screen> },
      { path: '*', element: <Screen><NotFoundPage /></Screen> },
    ],
  },
])
