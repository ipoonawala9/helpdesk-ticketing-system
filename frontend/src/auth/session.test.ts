import { describe, expect, it } from 'vitest'
import { clearSession, loadSession, saveSession, type Session } from './session'

const session = (expiresAt: string): Session => ({
  token: 't',
  expiresAt,
  user: { id: 1, name: 'Dana', email: 'd@x.test', role: 'CUSTOMER', phoneNumber: null, active: true, organization: null },
})

describe('session storage', () => {
  it('restores a valid session and discards an expired one', () => {
    const now = Date.UTC(2026, 8, 14, 12)
    saveSession(session('2026-09-14T13:00:00Z'))
    expect(loadSession(now)?.token).toBe('t')

    saveSession(session('2026-09-14T11:00:00Z'))
    expect(loadSession(now)).toBeNull()
    expect(sessionStorage.getItem('helpdesk.session')).toBeNull()
  })

  it('uses sessionStorage, never localStorage', () => {
    saveSession(session('2099-01-01T00:00:00Z'))
    expect(localStorage.length).toBe(0)
    clearSession()
    expect(loadSession()).toBeNull()
  })

  it('ignores corrupt data', () => {
    sessionStorage.setItem('helpdesk.session', '{not json')
    expect(loadSession()).toBeNull()
  })
})
