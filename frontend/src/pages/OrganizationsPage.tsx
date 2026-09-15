import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { organizationsApi } from '../api/endpoints'
import { EmptyState, ErrorState, SkeletonRows } from '../components/Feedback'
import { SelectField, TextField } from '../components/Fields'
import { Pagination } from '../components/Pagination'
import { CreateOrganizationDialog } from '../components/UserDialogs'
import { useDebounced, useDocumentTitle } from '../lib/hooks'
import { queryKeys } from '../lib/queryKeys'

export function OrganizationsPage() {
  useDocumentTitle('Organizations')
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState(params.get('q') ?? '')
  const [creating, setCreating] = useState(false)
  const q = useDebounced(search)
  const page = Number(params.get('page') ?? 0) || 0
  const sort = params.get('sort') ?? 'name,asc'

  const filters = { q: q || undefined, page, size: 20, sort }
  const organizations = useQuery({
    queryKey: queryKeys.organizationList(filters),
    queryFn: () => organizationsApi.list(filters),
    placeholderData: keepPreviousData,
  })

  function update(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)))
    if (!('page' in changes)) next.delete('page')
    setParams(next, { replace: true })
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1>Organizations</h1>
          <p>Each organization's customers, agents and tickets are kept separate from every other.</p>
        </div>
        <button type="button" className="button" onClick={() => setCreating(true)}>Create organization</button>
      </header>

      <section className="card">
        <div className="filters" role="search">
          <TextField className="field-grow" label="Search" type="search" placeholder="Name or domain" value={search} onChange={(e) => { setSearch(e.target.value); if (page) update({ page: undefined }) }} maxLength={100} />
          <SelectField label="Sort" value={sort} onChange={(e) => update({ sort: e.target.value })}>
            <option value="name,asc">Name</option>
            <option value="industry,asc">Industry</option>
            <option value="domain,asc">Domain</option>
          </SelectField>
        </div>
        {organizations.isPending && <SkeletonRows rows={5} />}
        {organizations.isError && <ErrorState error={organizations.error} onRetry={() => organizations.refetch()} />}
        {organizations.data && organizations.data.content.length === 0 && (
          <EmptyState
            title={q ? 'No organization matches that search' : 'No organizations yet'}
            action={!q ? <button type="button" className="button button-secondary" onClick={() => setCreating(true)}>Create the first organization</button> : undefined}
          >
            {!q && 'Create an organization, then add its administrator, who takes it from there.'}
          </EmptyState>
        )}
        {organizations.data && organizations.data.content.length > 0 && (
          <div style={{ opacity: organizations.isPlaceholderData ? 0.6 : 1 }}>
            <div className="table-wrap">
              <table className="table table-responsive">
                <thead>
                  <tr>
                    <th scope="col">Organization</th>
                    <th scope="col">Industry</th>
                    <th scope="col">Support email</th>
                    <th scope="col"><span className="sr-only">Links</span></th>
                  </tr>
                </thead>
                <tbody>
                  {organizations.data.content.map((organization) => (
                    <tr key={organization.id}>
                      <td data-wide>
                        <strong>{organization.name}</strong>
                        <span className="cell-sub">{organization.domain}</span>
                      </td>
                      <td>{organization.industry}</td>
                      <td data-hide-mobile style={{ overflowWrap: 'anywhere' }}>{organization.companyEmail}</td>
                      <td data-wide>
                        <div className="row" style={{ justifyContent: 'flex-end' }}>
                          <Link className="button button-quiet button-small" to={`/users?organizationId=${organization.id}`}>People</Link>
                          <Link className="button button-quiet button-small" to={`/tickets?organizationId=${organization.id}`}>Tickets</Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={organizations.data} noun="organizations" onPage={(p) => update({ page: p ? String(p) : undefined })} />
          </div>
        )}
      </section>
      <CreateOrganizationDialog open={creating} onClose={() => setCreating(false)} />
    </div>
  )
}
