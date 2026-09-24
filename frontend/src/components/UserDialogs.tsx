import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { organizationsApi, usersApi } from '../api/endpoints'
import type { CreateOrganizationInput, Role } from '../api/types'
import { useCurrentUser } from '../auth/AuthContext'
import { ROLE_LABEL } from '../lib/format'
import { rolePermissions } from '../lib/permissions'
import { queryKeys } from '../lib/queryKeys'
import { notify } from '../lib/toast'
import { Dialog } from './Dialog'
import { Notice, errorMessage } from './Feedback'
import { PasswordField, SelectField, TextField } from './Fields'

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function CreateUserDialog({ open, onClose, defaultRole, defaultOrganizationId }: {
  open: boolean
  onClose: () => void
  defaultRole: Role
  defaultOrganizationId?: number
}) {
  const me = useCurrentUser()
  const queryClient = useQueryClient()
  const roles = rolePermissions.creatableRoles(me.role)
  const [form, setForm] = useState({ name: '', email: '', password: '', phoneNumber: '', role: defaultRole, organizationId: defaultOrganizationId ? String(defaultOrganizationId) : '' })
  const [errors, setErrors] = useState<Record<string, string>>({})

  const organizations = useQuery({
    queryKey: queryKeys.organizationList({ size: 100, sort: 'name,asc' }),
    queryFn: () => organizationsApi.list({ size: 100, sort: 'name,asc' }),
    enabled: open && me.role === 'SUPER_ADMIN',
  })

  const create = useMutation({
    mutationFn: () => usersApi.create({
      name: form.name.trim(),
      email: form.email.trim(),
      password: form.password,
      phoneNumber: form.phoneNumber.trim() || undefined,
      role: form.role,
      organizationId: me.role === 'SUPER_ADMIN' && form.organizationId ? Number(form.organizationId) : undefined,
    }),
    onSuccess: (user) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.users })
      notify(`${user.name} can now sign in as ${ROLE_LABEL[user.role].toLowerCase()}.`)
      close()
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors)
    },
  })

  function close() {
    setForm({ name: '', email: '', password: '', phoneNumber: '', role: defaultRole, organizationId: defaultOrganizationId ? String(defaultOrganizationId) : '' })
    setErrors({})
    create.reset()
    onClose()
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!form.name.trim()) next.name = 'Enter a name.'
    if (!EMAIL.test(form.email.trim())) next.email = 'Enter a valid email address.'
    if (form.password.length < 8) next.password = 'Use at least 8 characters.'
    if (me.role === 'SUPER_ADMIN' && form.role !== 'SUPER_ADMIN' && !form.organizationId) next.organizationId = 'Choose an organization.'
    setErrors(next)
    if (Object.keys(next).length === 0) create.mutate()
  }

  const set = (key: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  return (
    <Dialog open={open} onClose={close} title="Add a person">
      <form className="stack" onSubmit={submit} noValidate>
        {create.isError && !(create.error instanceof ApiError && Object.keys(create.error.fieldErrors).length) && (
          <Notice tone="error">{errorMessage(create.error)}</Notice>
        )}
        <div className="form-grid">
          <TextField label="Full name" value={form.name} onChange={set('name')} error={errors.name} autoComplete="off" required maxLength={150} />
          <SelectField label="Role" value={form.role} onChange={set('role')}>
            {roles.map((role) => <option key={role} value={role}>{ROLE_LABEL[role]}</option>)}
          </SelectField>
          <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} autoComplete="off" required className="span-2" maxLength={200} />
          <PasswordField label="Initial password" value={form.password} onChange={set('password')} error={errors.password} hint="Share it privately; they can change it in their profile." autoComplete="new-password" required maxLength={100} />
          <TextField label="Phone (optional)" type="tel" value={form.phoneNumber} onChange={set('phoneNumber')} error={errors.phoneNumber} autoComplete="off" />
          {me.role === 'SUPER_ADMIN' && form.role !== 'SUPER_ADMIN' && (
            <SelectField label="Organization" value={form.organizationId} onChange={set('organizationId')} error={errors.organizationId} className="span-2">
              <option value="">Choose an organization</option>
              {organizations.data?.content.map((organization) => (
                <option key={organization.id} value={organization.id}>{organization.name}</option>
              ))}
            </SelectField>
          )}
        </div>
        {me.role === 'ORG_ADMIN' && <p className="field-hint">They'll be added to {me.organization?.name}.</p>}
        <div className="form-actions">
          <button type="button" className="button button-secondary" onClick={close}>Cancel</button>
          <button type="submit" className="button" disabled={create.isPending}>{create.isPending ? 'Adding…' : 'Add person'}</button>
        </div>
      </form>
    </Dialog>
  )
}

const EMPTY_ORGANIZATION: CreateOrganizationInput = { name: '', companyEmail: '', domain: '', industry: '' }

export function CreateOrganizationDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(EMPTY_ORGANIZATION)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const create = useMutation({
    mutationFn: () => organizationsApi.create({
      name: form.name.trim(), companyEmail: form.companyEmail.trim(), domain: form.domain.trim(), industry: form.industry.trim(),
    }),
    onSuccess: (organization) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.organizations })
      notify(`${organization.name} created. Add its administrator next.`)
      close()
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors)
    },
  })

  function close() {
    setForm(EMPTY_ORGANIZATION)
    setErrors({})
    create.reset()
    onClose()
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!form.name.trim()) next.name = 'Enter the organization name.'
    if (!EMAIL.test(form.companyEmail.trim())) next.companyEmail = 'Enter a valid email address.'
    if (!form.domain.trim()) next.domain = 'Enter the domain, e.g. acme.com.'
    if (!form.industry.trim()) next.industry = 'Enter the industry.'
    setErrors(next)
    if (Object.keys(next).length === 0) create.mutate()
  }

  const set = (key: keyof CreateOrganizationInput) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  return (
    <Dialog open={open} onClose={close} title="Create an organization">
      <form className="stack" onSubmit={submit} noValidate>
        {create.isError && !(create.error instanceof ApiError && Object.keys(create.error.fieldErrors).length) && (
          <Notice tone="error">{errorMessage(create.error)}</Notice>
        )}
        <div className="form-grid">
          <TextField label="Name" value={form.name} onChange={set('name')} error={errors.name} className="span-2" maxLength={150} />
          <TextField label="Support email" type="email" value={form.companyEmail} onChange={set('companyEmail')} error={errors.companyEmail} className="span-2" maxLength={200} />
          <TextField label="Domain" value={form.domain} onChange={set('domain')} error={errors.domain} placeholder="acme.com" maxLength={150} />
          <TextField label="Industry" value={form.industry} onChange={set('industry')} error={errors.industry} maxLength={100} />
        </div>
        <div className="form-actions">
          <button type="button" className="button button-secondary" onClick={close}>Cancel</button>
          <button type="submit" className="button" disabled={create.isPending}>{create.isPending ? 'Creating…' : 'Create organization'}</button>
        </div>
      </form>
    </Dialog>
  )
}
