import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { messagesApi, ticketsApi, usersApi } from '../api/endpoints'
import { TICKET_CATEGORIES, type Ticket, type TicketCategory, type User } from '../api/types'
import { useCurrentUser } from '../auth/AuthContext'
import { ConfirmDialog, Dialog } from '../components/Dialog'
import { EmptyState, ErrorState, LoadingState, Notice, SkeletonRows, errorMessage } from '../components/Feedback'
import { SelectField, TextAreaField, TextField } from '../components/Fields'
import { TicketStub } from '../components/TicketBits'
import { CATEGORY_LABEL, ROLE_LABEL, formatDateTime, formatRelative, initials } from '../lib/format'
import { useDocumentTitle } from '../lib/hooks'
import { waitingOn } from '../lib/lifecycle'
import { isAssignedAgent, isTicketCustomer, ticketPermissions } from '../lib/permissions'
import { queryKeys } from '../lib/queryKeys'
import { notify } from '../lib/toast'

type Pending = 'close' | 'delete' | 'reopen' | null

export function TicketDetailPage() {
  const { id } = useParams()
  const ticketId = Number(id)
  const user = useCurrentUser()
  const ticket = useQuery({
    queryKey: queryKeys.ticket(ticketId),
    queryFn: () => ticketsApi.get(ticketId),
    enabled: Number.isInteger(ticketId) && ticketId > 0,
    retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
  })
  useDocumentTitle(ticket.data ? ticket.data.ticketNumber : 'Ticket')

  if (!Number.isInteger(ticketId) || ticketId <= 0 || (ticket.error instanceof ApiError && ticket.error.isNotFound)) {
    return (
      <div className="page">
        <div className="card">
          <EmptyState title="Ticket not found" action={<Link className="button button-secondary" to="/tickets">Back to tickets</Link>}>
            This ticket doesn't exist, or it isn't one you have access to.
          </EmptyState>
        </div>
      </div>
    )
  }
  if (ticket.isPending) return <div className="page"><LoadingState label="Loading ticket" /></div>
  if (ticket.isError) return <div className="page"><div className="card"><ErrorState error={ticket.error} onRetry={() => ticket.refetch()} /></div></div>

  return <TicketDetail ticket={ticket.data} user={user} />
}

function TicketDetail({ ticket, user }: { ticket: Ticket; user: User }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [pending, setPending] = useState<Pending>(null)
  const [editing, setEditing] = useState(false)
  const [assigning, setAssigning] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)

  const refresh = (updated?: Ticket) => {
    if (updated) queryClient.setQueryData(queryKeys.ticket(ticket.id), updated)
    queryClient.invalidateQueries({ queryKey: queryKeys.tickets })
  }

  const action = useMutation({
    mutationFn: (kind: 'start' | 'resolve' | 'reopen' | 'close') => ticketsApi[kind](ticket.id),
    onMutate: () => setActionError(null),
    onSuccess: (updated, kind) => {
      refresh(updated)
      setPending(null)
      notify({ start: 'Work started.', resolve: 'Marked as resolved.', reopen: 'Ticket reopened.', close: 'Ticket closed.' }[kind])
    },
    onError: (error) => {
      setPending(null)
      setActionError(errorMessage(error))
    },
  })

  const remove = useMutation({
    mutationFn: () => ticketsApi.remove(ticket.id),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.ticket(ticket.id) })
      queryClient.invalidateQueries({ queryKey: queryKeys.tickets })
      notify(`Ticket ${ticket.ticketNumber} deleted.`)
      navigate('/tickets', { replace: true })
    },
    onError: (error) => {
      setPending(null)
      setActionError(errorMessage(error))
    },
  })

  const can = {
    edit: ticketPermissions.canEdit(user, ticket),
    assign: ticketPermissions.canAssign(user, ticket),
    start: ticketPermissions.canStart(user, ticket),
    resolve: ticketPermissions.canResolve(user, ticket),
    reopen: ticketPermissions.canReopen(user, ticket),
    close: ticketPermissions.canClose(user, ticket),
    delete: ticketPermissions.canDelete(user, ticket),
  }
  const anyAction = can.assign || can.start || can.resolve || can.reopen || can.close

  return (
    <div className="page">
      <nav aria-label="Breadcrumb" className="row muted" style={{ fontSize: 'var(--text-sm)' }}>
        <Link to="/tickets">Tickets</Link> <span aria-hidden="true">/</span> <span className="mono">{ticket.ticketNumber}</span>
      </nav>

      <TicketStub ticket={ticket} />

      <div className="grid-2">
        <div className="detail-main">
          <section className="card" aria-labelledby="description-title">
            <div className="card-header">
              <h2 id="description-title">Description</h2>
              {can.edit && !editing && (
                <button type="button" className="button button-secondary button-small" onClick={() => setEditing(true)}>Edit</button>
              )}
            </div>
            {editing
              ? <EditTicketForm ticket={ticket} onDone={(updated) => { if (updated) refresh(updated); setEditing(false) }} />
              : <div className="card-body"><p style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{ticket.description}</p></div>}
          </section>

          {ticketPermissions.canReadMessages(user) && <Conversation ticket={ticket} user={user} />}
        </div>

        <aside className="detail-side">
          <section className="card detail-status" aria-labelledby="actions-title">
            <div className="card-header"><h2 id="actions-title">Status</h2></div>
            <div className="card-body actions-panel">
              <p className="waiting">{waitingOn(ticket, user.id)}</p>
              {actionError && <Notice tone="error">{actionError}</Notice>}

              {can.start && (
                <button type="button" className="button button-block" disabled={action.isPending} onClick={() => action.mutate('start')}>
                  {ticket.status === 'REOPENED' ? 'Resume work' : 'Start work'}
                </button>
              )}
              {can.resolve && (
                <button type="button" className="button button-block" disabled={action.isPending} onClick={() => action.mutate('resolve')}>
                  Mark as resolved
                </button>
              )}
              {can.close && (
                <button type="button" className="button button-block" disabled={action.isPending} onClick={() => setPending('close')}>
                  {isTicketCustomer(user, ticket) ? 'Confirm fixed and close' : 'Close ticket'}
                </button>
              )}
              {can.reopen && (
                <button type="button" className="button button-secondary button-block" disabled={action.isPending} onClick={() => setPending('reopen')}>
                  It's not fixed, reopen
                </button>
              )}
              {can.assign && (
                <button type="button" className={`button button-block ${ticket.assignedAgent ? 'button-secondary' : ''}`} onClick={() => setAssigning(true)}>
                  {ticket.assignedAgent ? 'Reassign' : 'Assign an agent'}
                </button>
              )}
              {!anyAction && user.role === 'SUPER_ADMIN' && (
                <p className="field-hint">Super admins can view tickets but not change them.</p>
              )}
            </div>
          </section>

          <section className="card" aria-labelledby="details-title">
            <div className="card-header"><h2 id="details-title">Details</h2></div>
            <div className="card-body">
              <dl className="definition">
                <dt>Customer</dt><dd>{ticket.customer.name}<span className="cell-sub">{ticket.customer.email}</span></dd>
                <dt>Agent</dt><dd>{ticket.assignedAgent ? <>{ticket.assignedAgent.name}<span className="cell-sub">{ticket.assignedAgent.email}</span></> : <span className="muted">Not assigned yet</span>}</dd>
                <dt>Category</dt><dd>{CATEGORY_LABEL[ticket.category]}</dd>
                <dt>Organization</dt><dd>{ticket.organization.name}</dd>
              </dl>
            </div>
            <hr className="divider" />
            <div className="card-body">
              <dl className="definition">
                <dt>Opened</dt><dd><time dateTime={ticket.createdAt}>{formatDateTime(ticket.createdAt)}</time></dd>
                <dt>Updated</dt><dd><time dateTime={ticket.updatedAt}>{formatDateTime(ticket.updatedAt)}</time></dd>
                <dt>Resolved</dt><dd>{ticket.resolvedAt ? <time dateTime={ticket.resolvedAt}>{formatDateTime(ticket.resolvedAt)}</time> : <span className="muted">—</span>}</dd>
                <dt>Closed</dt><dd>{ticket.closedAt ? <time dateTime={ticket.closedAt}>{formatDateTime(ticket.closedAt)}</time> : <span className="muted">—</span>}</dd>
                <dt>Reopened</dt><dd className="mono">{ticket.reopenCount}×</dd>
              </dl>
            </div>
          </section>

          {can.delete && (
            <button type="button" className="button button-quiet" style={{ color: 'var(--alert)', alignSelf: 'flex-start' }} onClick={() => setPending('delete')}>
              Delete this ticket
            </button>
          )}
        </aside>
      </div>

      <ConfirmDialog
        open={pending === 'close'}
        title={`Close ${ticket.ticketNumber}?`}
        confirmLabel="Close ticket"
        busy={action.isPending}
        onCancel={() => setPending(null)}
        onConfirm={() => action.mutate('close')}
      >
        {isTicketCustomer(user, ticket)
          ? "Only close it if the problem is really fixed. You can still reopen it for a few days if it comes back."
          : "The customer hasn't confirmed the fix. Closing ends the conversation on this ticket."}
      </ConfirmDialog>

      <ConfirmDialog
        open={pending === 'reopen'}
        title={`Reopen ${ticket.ticketNumber}?`}
        confirmLabel="Reopen ticket"
        busy={action.isPending}
        onCancel={() => setPending(null)}
        onConfirm={() => action.mutate('reopen')}
      >
        {`It goes back to ${ticket.assignedAgent?.name ?? 'the agent'}. Add a message saying what still isn't working.`}
      </ConfirmDialog>

      <ConfirmDialog
        open={pending === 'delete'}
        title={`Delete ${ticket.ticketNumber}?`}
        confirmLabel="Delete permanently"
        tone="danger"
        busy={remove.isPending}
        onCancel={() => setPending(null)}
        onConfirm={() => remove.mutate()}
      >
        The ticket and its whole conversation are deleted for everyone. This can't be undone.
      </ConfirmDialog>

      {can.assign && (
        <AssignDialog
          open={assigning}
          ticket={ticket}
          onClose={() => setAssigning(false)}
          onAssigned={(updated) => { refresh(updated); setAssigning(false); notify(`Assigned to ${updated.assignedAgent?.name}.`) }}
        />
      )}
    </div>
  )
}

function EditTicketForm({ ticket, onDone }: { ticket: Ticket; onDone: (updated?: Ticket) => void }) {
  const [title, setTitle] = useState(ticket.title)
  const [category, setCategory] = useState<TicketCategory>(ticket.category)
  const [description, setDescription] = useState(ticket.description)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const save = useMutation({
    mutationFn: () => ticketsApi.update(ticket.id, { title: title.trim(), description: description.trim(), category }),
    onSuccess: (updated) => {
      notify(updated.priority !== ticket.priority ? `Saved. Priority is now ${updated.priority.toLowerCase()}.` : 'Changes saved.')
      onDone(updated)
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!title.trim()) next.title = 'Enter a title.'
    if (!description.trim()) next.description = 'Enter a description.'
    setErrors(next)
    if (Object.keys(next).length === 0) save.mutate()
  }

  return (
    <form className="card-body stack" onSubmit={submit} noValidate>
      {save.isError && !Object.keys(errors).length && <Notice tone="error">{errorMessage(save.error)}</Notice>}
      <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} error={errors.title} maxLength={200} />
      <SelectField label="Category" value={category} onChange={(e) => setCategory(e.target.value as TicketCategory)}>
        {TICKET_CATEGORIES.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
      </SelectField>
      <TextAreaField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} error={errors.description} maxLength={5000} rows={7} hint="Priority is recalculated when you save." />
      <div className="form-actions">
        <button type="button" className="button button-secondary" onClick={() => onDone()}>Cancel</button>
        <button type="submit" className="button" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save changes'}</button>
      </div>
    </form>
  )
}

function AssignDialog({ open, ticket, onClose, onAssigned }: {
  open: boolean
  ticket: Ticket
  onClose: () => void
  onAssigned: (ticket: Ticket) => void
}) {
  const [agentId, setAgentId] = useState(ticket.assignedAgent ? String(ticket.assignedAgent.id) : '')
  const agents = useQuery({
    queryKey: queryKeys.userList({ role: 'SUPPORT_AGENT', active: true, size: 100, sort: 'name,asc' }),
    queryFn: () => usersApi.list({ role: 'SUPPORT_AGENT', active: true, size: 100, sort: 'name,asc' }),
    enabled: open,
  })
  const assign = useMutation({
    mutationFn: () => ticketsApi.assign(ticket.id, Number(agentId)),
    onSuccess: onAssigned,
  })

  const inProgress = ticket.status === 'IN_PROGRESS'

  return (
    <Dialog open={open} onClose={onClose} title={ticket.assignedAgent ? 'Reassign ticket' : 'Assign an agent'}>
      <form className="stack" onSubmit={(e) => { e.preventDefault(); if (agentId) assign.mutate() }}>
        {assign.isError && <Notice tone="error">{errorMessage(assign.error)}</Notice>}
        {agents.isPending && <SkeletonRows rows={1} />}
        {agents.isError && <Notice tone="error">{errorMessage(agents.error)}</Notice>}
        {agents.data && agents.data.content.length === 0 && (
          <Notice>Your organization has no active support agents. <Link to="/users?role=SUPPORT_AGENT">Add one</Link> first.</Notice>
        )}
        {agents.data && agents.data.content.length > 0 && (
          <SelectField
            label="Support agent"
            value={agentId}
            onChange={(e) => setAgentId(e.target.value)}
            hint={inProgress ? 'Work has started. A new agent restarts it from Assigned.' : undefined}
          >
            <option value="" disabled>Choose an agent</option>
            {agents.data.content.map((agent) => (
              <option key={agent.id} value={agent.id}>
                {agent.name}{agent.id === ticket.assignedAgent?.id ? ' (current)' : ''}
              </option>
            ))}
          </SelectField>
        )}
        <div className="form-actions">
          <button type="button" className="button button-secondary" onClick={onClose}>Cancel</button>
          <button type="submit" className="button" disabled={!agentId || assign.isPending || agentId === String(ticket.assignedAgent?.id ?? '')}>
            {assign.isPending ? 'Assigning…' : 'Assign'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

function Conversation({ ticket, user }: { ticket: Ticket; user: User }) {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState('')
  const messages = useQuery({
    queryKey: queryKeys.messages(ticket.id),
    queryFn: () => messagesApi.list(ticket.id),
    // New replies appear without a reload while the ticket is open.
    refetchInterval: ticket.status === 'CLOSED' ? false : 20_000,
  })
  const post = useMutation({
    mutationFn: () => messagesApi.post(ticket.id, draft.trim()),
    onSuccess: () => {
      setDraft('')
      queryClient.invalidateQueries({ queryKey: queryKeys.messages(ticket.id) })
    },
  })

  const canPost = ticketPermissions.canPostMessage(user, ticket)
  const send = (event?: FormEvent) => {
    event?.preventDefault()
    if (draft.trim() && !post.isPending) post.mutate()
  }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) send()
  }

  let composerNote: string | null = null
  if (!canPost) {
    if (ticket.status === 'CLOSED') composerNote = isTicketCustomer(user, ticket) ? 'This ticket is closed. Reopen it to continue the conversation.' : 'This ticket is closed.'
    else if (user.role === 'ORG_ADMIN') composerNote = 'Administrators can read the conversation but not post in it.'
  }

  return (
    <section className="card" aria-labelledby="conversation-title">
      <div className="card-header">
        <div>
          <h2 id="conversation-title">Conversation</h2>
          <p>Between {ticket.customer.name} and {ticket.assignedAgent?.name ?? 'the assigned agent'}.</p>
        </div>
      </div>
      {messages.isPending && <SkeletonRows rows={3} />}
      {messages.isError && <ErrorState error={messages.error} onRetry={() => messages.refetch()} />}
      {messages.data && messages.data.length === 0 && (
        <EmptyState title="No messages yet">
          {canPost ? 'Add details or ask a question below.' : 'Messages between the customer and agent will appear here.'}
        </EmptyState>
      )}
      {messages.data && messages.data.length > 0 && (
        <ol className="thread" aria-live="polite">
          {messages.data.map((message) => {
            const mine = message.sender?.id === user.id
            const name = message.sender ? (mine ? 'You' : message.sender.name) : 'Deleted user'
            return (
              <li key={message.id} className="message" data-mine={mine}>
                <span className="avatar" aria-hidden="true">{message.sender ? initials(message.sender.name) : '?'}</span>
                <div>
                  <div className="message-head">
                    <strong>{name}</strong>
                    {message.sender && <span className="message-role">{ROLE_LABEL[message.sender.role]}</span>}
                    <time className="message-role" dateTime={message.createdAt} title={formatDateTime(message.createdAt)}>
                      · {formatRelative(message.createdAt)}
                    </time>
                  </div>
                  <p className="message-body">{message.content}</p>
                </div>
              </li>
            )
          })}
        </ol>
      )}
      {canPost ? (
        <form className="composer" onSubmit={send}>
          {post.isError && <Notice tone="error">{errorMessage(post.error)}</Notice>}
          <label className="sr-only" htmlFor="composer">Message</label>
          <textarea
            id="composer"
            className="textarea"
            style={{ minHeight: 88 }}
            placeholder={isAssignedAgent(user, ticket) ? `Reply to ${ticket.customer.name}` : 'Write a message'}
            value={draft}
            maxLength={5000}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKeyDown}
          />
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span className="field-hint">Ctrl + Enter to send</span>
            <button type="submit" className="button" disabled={!draft.trim() || post.isPending}>{post.isPending ? 'Sending…' : 'Send'}</button>
          </div>
        </form>
      ) : composerNote && (
        <div className="composer"><p className="field-hint">{composerNote}</p></div>
      )}
    </section>
  )
}
