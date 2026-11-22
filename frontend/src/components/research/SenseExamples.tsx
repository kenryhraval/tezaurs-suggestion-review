import { useRef, useState } from 'react'
import type { Example } from '../../api/types'
import { EditButton } from '../shared/EditButton'
import { Pagination } from '../shared/Pagination'
import { UsageSourceLink } from '../usage/UsageSourceLink'
import { SideToggle } from '../shared/SideToggle'
import { ExampleText } from '../shared/ExampleText'

type Props = {
  examples: Example[]
  busy: boolean
  editable: boolean
  canApprove: boolean
  onApprovalChange: (example: Example, approved: boolean) => void
  onEdit: (example: Example) => void
}

const PAGE_SIZE = 10

export function SenseExamples({ examples, busy, editable, canApprove, onApprovalChange, onEdit }: Props) {
  const [requestedPage, setRequestedPage] = useState(0)
  const [approvedOnly, setApprovedOnly] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  const approvedExamples = examples.filter((example) => example.approved)
  const filteredExamples = approvedOnly ? approvedExamples : examples
  const totalPages = Math.ceil(filteredExamples.length / PAGE_SIZE)
  const page = Math.min(requestedPage, Math.max(0, totalPages - 1))
  const visibleExamples = filteredExamples.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE)

  function changePage(nextPage: number) {
    setRequestedPage(nextPage)
    listRef.current?.scrollTo({ top: 0 })
  }

  return <>
    {canApprove && <label className="sense-examples-filter">
      <input type="checkbox" checked={approvedOnly} disabled={busy}
        onChange={(event) => { setApprovedOnly(event.target.checked); changePage(0) }} />
      Apstiprinātie ({approvedExamples.length})
    </label>}
    <div className="example-scroll sense-dialog-examples" ref={listRef}
      role="region" aria-label="Nozīmes piemēri" tabIndex={0}>
      {filteredExamples.length === 0 && <p className="muted">
        {approvedOnly ? 'Nav apstiprinātu piemēru.' : 'Piemēri nav piesaistīti.'}
      </p>}
      {visibleExamples.map((example) => <article className={canApprove ? 'usage-example sense-example' : 'usage-example'} key={example.id}>
        {canApprove && <SideToggle color="green" selected={example.approved} disabled={busy || !editable}
          label={example.approved ? 'Atcelt piemēra apstiprinājumu' : 'Apstiprināt piemēru melnrakstam'}
          onToggle={() => onApprovalChange(example, !example.approved)} />}
        <p><ExampleText example={example} /></p>
        <div className="sense-example-actions">
          <UsageSourceLink label={example.sourceLabel} url={example.sourceUrl ?? example.url}
            originalSource={example.sourceUrl !== null} />
          <EditButton label="Pārvietot piemēru" disabled={busy || !editable} onClick={() => onEdit(example)} />
        </div>
      </article>)}
    </div>
    <Pagination page={page} totalPages={totalPages} disabled={busy}
      label="Nozīmes piemēru lapošana" onPageChange={changePage} />
  </>
}
