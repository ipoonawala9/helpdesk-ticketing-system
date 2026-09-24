import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { ticketsApi } from '../api/endpoints'
import { TICKET_CATEGORIES, type TicketCategory } from '../api/types'
import { Notice, errorMessage } from '../components/Feedback'
import { SelectField, TextAreaField, TextField } from '../components/Fields'
import { CATEGORY_HINT, CATEGORY_LABEL } from '../lib/format'
import { useDocumentTitle } from '../lib/hooks'
import { queryKeys } from '../lib/queryKeys'
import { notify } from '../lib/toast'

export function NewTicketPage() {
  useDocumentTitle('Report a problem')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState<TicketCategory | ''>('')
  const [description, setDescription] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})

  const create = useMutation({
    mutationFn: () => ticketsApi.create({ title: title.trim(), description: description.trim(), category: category as TicketCategory }),
    onSuccess: (ticket) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.tickets })
      notify(`Ticket ${ticket.ticketNumber} opened.`)
      navigate(`/tickets/${ticket.id}`)
    },
    onError: (error) => {
      if (error instanceof ApiError) setErrors(error.fieldErrors)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!title.trim()) next.title = 'Give the problem a short title.'
    if (!category) next.category = 'Choose the category that fits best.'
    if (!description.trim()) next.description = 'Describe what happened and what you expected.'
    setErrors(next)
    if (Object.keys(next).length === 0) create.mutate()
  }

  return (
    <div className="page page-narrow">
      <header className="page-header">
        <div>
          <h1>Report a problem</h1>
          <p>An administrator assigns it to a support agent, and you can follow every step from the ticket.</p>
        </div>
      </header>
      <form className="card card-body stack" onSubmit={submit} noValidate>
        {create.isError && !(create.error instanceof ApiError && Object.keys(create.error.fieldErrors).length) && (
          <Notice tone="error">{errorMessage(create.error)}</Notice>
        )}
        <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} error={errors.title} maxLength={200} placeholder="e.g. Printer on floor 3 jams on every job" autoFocus />
        {/* A menu of eight short words does not need the width of the page. */}
        <div className="form-grid">
          <SelectField
            label="Category"
            value={category}
            onChange={(e) => setCategory(e.target.value as TicketCategory)}
            error={errors.category}
            hint={category ? CATEGORY_HINT[category] : undefined}
          >
            <option value="" disabled>Choose a category</option>
            {TICKET_CATEGORIES.map((c) => <option key={c} value={c}>{CATEGORY_LABEL[c]}</option>)}
          </SelectField>
        </div>
        <TextAreaField
          label="What's happening?"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={errors.description}
          maxLength={5000}
          rows={8}
          hint="Include what you tried, any error messages, and how many people are affected. Priority is set from this description, so mention if it's urgent."
        />
        <div className="form-actions">
          <Link to="/tickets" className="button button-secondary">Cancel</Link>
          <button type="submit" className="button" disabled={create.isPending}>{create.isPending ? 'Opening…' : 'Open ticket'}</button>
        </div>
      </form>
    </div>
  )
}
