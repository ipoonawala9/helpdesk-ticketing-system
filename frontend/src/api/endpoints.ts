import { request } from './client'
import type {
  CreateOrganizationInput,
  CreateTicketInput,
  CreateUserInput,
  LoginResponse,
  Message,
  Organization,
  Page,
  Ticket,
  TicketCategory,
  TicketFilters,
  User,
  UserFilters,
} from './types'

export const authApi = {
  login: (email: string, password: string) =>
    request<LoginResponse>('POST', '/api/auth/login', { body: { email, password }, anonymous: true }),
  me: () => request<User>('GET', '/api/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('POST', '/api/auth/password', { body: { currentPassword, newPassword } }),
  forgotPassword: (email: string) =>
    request<void>('POST', '/api/auth/forgot-password', { body: { email }, anonymous: true }),
  resetPassword: (token: string, newPassword: string) =>
    request<void>('POST', '/api/auth/reset-password', { body: { token, newPassword }, anonymous: true }),
}

export const ticketsApi = {
  list: (filters: TicketFilters = {}, signal?: AbortSignal) =>
    request<Page<Ticket>>('GET', '/api/tickets', { query: { ...filters }, signal }),
  get: (id: number) => request<Ticket>('GET', `/api/tickets/${id}`),
  create: (input: CreateTicketInput) => request<Ticket>('POST', '/api/tickets', { body: input }),
  update: (id: number, input: { title: string; description: string; category: TicketCategory }) =>
    request<Ticket>('PUT', `/api/tickets/${id}`, { body: input }),
  remove: (id: number) => request<void>('DELETE', `/api/tickets/${id}`),
  assign: (id: number, agentId: number) =>
    request<Ticket>('POST', `/api/tickets/${id}/assign`, { body: { agentId } }),
  start: (id: number) => request<Ticket>('POST', `/api/tickets/${id}/start`),
  resolve: (id: number) => request<Ticket>('POST', `/api/tickets/${id}/resolve`),
  reopen: (id: number) => request<Ticket>('POST', `/api/tickets/${id}/reopen`),
  close: (id: number) => request<Ticket>('POST', `/api/tickets/${id}/close`),
}

export const messagesApi = {
  list: (ticketId: number) => request<Message[]>('GET', `/api/tickets/${ticketId}/messages`),
  post: (ticketId: number, content: string) =>
    request<Message>('POST', `/api/tickets/${ticketId}/messages`, { body: { content } }),
}

export const usersApi = {
  list: (filters: UserFilters = {}) => request<Page<User>>('GET', '/api/users', { query: { ...filters } }),
  get: (id: number) => request<User>('GET', `/api/users/${id}`),
  create: (input: CreateUserInput) => request<User>('POST', '/api/users', { body: input }),
  deactivate: (id: number) => request<User>('POST', `/api/users/${id}/deactivate`),
  activate: (id: number) => request<User>('POST', `/api/users/${id}/activate`),
}

export const organizationsApi = {
  list: (filters: { q?: string; page?: number; size?: number; sort?: string } = {}) =>
    request<Page<Organization>>('GET', '/api/organizations', { query: { ...filters } }),
  get: (id: number) => request<Organization>('GET', `/api/organizations/${id}`),
  create: (input: CreateOrganizationInput) => request<Organization>('POST', '/api/organizations', { body: input }),
}
