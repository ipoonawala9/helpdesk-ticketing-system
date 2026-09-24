import { useCallback, useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import type { Role } from '../api/types'
import { useAuth, useCurrentUser } from '../auth/AuthContext'
import { ROLE_LABEL, initials } from '../lib/format'
import { useDrawerDrag } from '../lib/useDrawerDrag'
import { BrandMark } from './Brand'

interface NavItem {
  to: string
  label: string
  end?: boolean
}

const NAV: Record<Role, NavItem[]> = {
  CUSTOMER: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/tickets', label: 'My tickets', end: true },
    { to: '/tickets/new', label: 'New ticket' },
  ],
  SUPPORT_AGENT: [
    { to: '/dashboard', label: 'Dashboard' },
    { to: '/tickets', label: 'Assigned tickets' },
  ],
  ORG_ADMIN: [
    { to: '/dashboard', label: 'Overview' },
    { to: '/tickets', label: 'Tickets' },
    { to: '/users', label: 'Agents & customers' },
  ],
  SUPER_ADMIN: [
    { to: '/dashboard', label: 'Overview' },
    { to: '/organizations', label: 'Organizations' },
    { to: '/users', label: 'Users' },
    { to: '/tickets', label: 'All tickets' },
  ],
}

export function AppShell() {
  const user = useCurrentUser()
  const { signOut } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const closeMenu = useCallback(() => setMenuOpen(false), [])
  // On a narrow screen the drawer can also simply be pushed out of the way.
  const { drawerRef, shellRef } = useDrawerDrag(menuOpen, closeMenu)

  useEffect(() => {
    // Warm the screens this role reaches next, once the current one is idle.
    const idle = window.requestIdleCallback?.(() => {
      void import('../pages/TicketsPage')
      void import('../pages/TicketDetailPage')
      if (user.role === 'ORG_ADMIN' || user.role === 'SUPER_ADMIN') void import('../pages/UsersPage')
      if (user.role === 'CUSTOMER') void import('../pages/NewTicketPage')
    })
    return () => (idle === undefined ? undefined : window.cancelIdleCallback?.(idle))
  }, [user.role])

  return (
    <>
      <a className="skip-link" href="#main">Skip to content</a>
      <div className="shell" ref={shellRef}>
        <header className="topbar">
          <NavLink to="/dashboard" className="brand"><BrandMark /> HelpDesk</NavLink>
          <button
            type="button"
            className="button button-small"
            style={{ background: 'var(--ink-soft)', borderColor: 'var(--ink-soft)' }}
            aria-expanded={menuOpen}
            aria-controls="sidebar"
            onClick={() => setMenuOpen((open) => !open)}
          >
            Menu
          </button>
        </header>

        {menuOpen && <div className="scrim" onClick={closeMenu} aria-hidden="true" />}

        <aside id="sidebar" className="sidebar" ref={drawerRef} data-open={menuOpen}>
          <NavLink to="/dashboard" className="brand" onClick={closeMenu}><BrandMark /> HelpDesk</NavLink>
          <nav aria-label="Main">
            <div className="nav-label">{user.organization?.name ?? 'All organizations'}</div>
            <div className="nav">
              {NAV[user.role].map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className="nav-link" onClick={closeMenu}>
                  {item.label}
                </NavLink>
              ))}
            </div>
          </nav>
          <div className="sidebar-footer">
            <NavLink to="/profile" className="sidebar-user" onClick={closeMenu}>
              <span className="avatar" aria-hidden="true">{initials(user.name)}</span>
              <span>
                {user.name}
                <small>{ROLE_LABEL[user.role]}</small>
              </span>
            </NavLink>
            <button type="button" className="button button-quiet button-small" onClick={() => signOut()}>
              Sign out
            </button>
          </div>
        </aside>

        <main id="main" className="main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </>
  )
}
