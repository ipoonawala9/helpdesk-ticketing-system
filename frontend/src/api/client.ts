import type { ApiErrorBody } from './types'

/**
 * The one place the frontend talks to the backend. Every request goes through
 * {@link request}, which adds the access token, builds query strings, parses
 * the backend's standard error body, and reports an expired or revoked session.
 */

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }

  get isNotFound() {
    return this.status === 404
  }
}

interface ClientHooks {
  getToken: () => string | null
  /** Called when an authenticated request is rejected with 401. */
  onUnauthorized: () => void
}

let hooks: ClientHooks = { getToken: () => null, onUnauthorized: () => {} }

export function configureApiClient(next: ClientHooks) {
  hooks = next
}

type QueryValue = string | number | boolean | undefined | null | Array<string | number>

export interface RequestOptions {
  body?: unknown
  query?: Record<string, QueryValue>
  signal?: AbortSignal
  /** Send without the access token, e.g. for login. */
  anonymous?: boolean
}

export function buildQuery(query: Record<string, QueryValue> = {}): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    if (Array.isArray(value)) {
      value.forEach((item) => params.append(key, String(item)))
    } else {
      params.append(key, String(value))
    }
  }
  const text = params.toString()
  return text ? `?${text}` : ''
}

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const token = options.anonymous ? null : hooks.getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}${buildQuery(options.query)}`, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, "Can't reach the HelpDesk server. Check your connection and try again.")
  }

  if (response.status === 401 && token) {
    hooks.onUnauthorized()
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiErrorBody>
    if (typeof body.message === 'string') {
      return new ApiError(response.status, body.message, body.fieldErrors ?? {})
    }
  } catch {
    // Not the standard error body; fall through to a generic message.
  }
  return new ApiError(response.status, `The server responded with ${response.status}.`)
}
