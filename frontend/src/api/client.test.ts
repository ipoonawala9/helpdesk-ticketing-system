import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, buildQuery, configureApiClient, request } from './client'

function respond(status: number, body?: unknown) {
  return vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
}

describe('api client', () => {
  const onUnauthorized = vi.fn()

  beforeEach(() => {
    configureApiClient({ getToken: () => 'token-123', onUnauthorized })
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    onUnauthorized.mockReset()
  })

  it('sends the bearer token and JSON body', async () => {
    const fetchMock = respond(200, { id: 1 })
    vi.stubGlobal('fetch', fetchMock)

    await request('POST', '/api/tickets', { body: { title: 't' } })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/tickets')
    expect(init.headers.Authorization).toBe('Bearer token-123')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"title":"t"}')
  })

  it('does not send the token on anonymous requests', async () => {
    const fetchMock = respond(200, {})
    vi.stubGlobal('fetch', fetchMock)

    await request('POST', '/api/auth/login', { body: {}, anonymous: true })

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it("turns the backend's error body into an ApiError with field errors", async () => {
    vi.stubGlobal('fetch', respond(400, {
      status: 400, error: 'Bad Request', message: 'Validation failed', path: '/api/tickets',
      fieldErrors: { title: 'Title is required' },
    }))

    const error = (await request('POST', '/api/tickets', { body: {} }).catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.message).toBe('Validation failed')
    expect(error.fieldErrors).toEqual({ title: 'Title is required' })
  })

  it('reports an expired session when an authenticated request gets 401', async () => {
    vi.stubGlobal('fetch', respond(401, { status: 401, message: 'Invalid or expired access token' }))

    await expect(request('GET', '/api/auth/me')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not treat a failed login (401 without a token) as an expired session', async () => {
    vi.stubGlobal('fetch', respond(401, { status: 401, message: 'Invalid email or password' }))

    await expect(request('POST', '/api/auth/login', { anonymous: true })).rejects.toMatchObject({
      message: 'Invalid email or password',
    })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('returns undefined for 204 and explains network failures', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    await expect(request('DELETE', '/api/tickets/1')).resolves.toBeUndefined()

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    await expect(request('GET', '/api/tickets')).rejects.toMatchObject({ status: 0 })
  })

  it('builds query strings with repeated values and skips empty ones', () => {
    expect(buildQuery({ status: ['OPEN', 'ASSIGNED'], q: '', page: 0, unassigned: undefined }))
      .toBe('?status=OPEN&status=ASSIGNED&page=0')
    expect(buildQuery({})).toBe('')
  })
})
