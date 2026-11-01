import { useState } from 'react'
import type { UsageEvidence } from '../api/types'
import { SavedUsageEvidence } from './SavedUsageEvidence'

type Props = {
  evidence: UsageEvidence[]
  busy: boolean
  onRemove?: (id: string) => void
}

const PAGE_SIZE = 5

export function SelectedUsageEvidence({ evidence, busy, onRemove }: Props) {
  const [page, setPage] = useState(0)
  if (evidence.length === 0) {
    return null
  }

  const pageCount = Math.ceil(evidence.length / PAGE_SIZE)
  const currentPage = Math.min(page, pageCount - 1)
  const visibleEvidence = evidence.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE)
  const sourceCounts = new Map<string, number>()
  for (const item of evidence) {
    const source = item.corpusLabel ?? new URL(item.url).hostname
    sourceCounts.set(source, (sourceCounts.get(source) ?? 0) + 1)
  }

  const sourceSummary: string[] = []
  for (const [source, count] of sourceCounts) {
    sourceSummary.push(`${source}: ${count}`)
  }

  return (
    <section className="selected-evidence">
      <h4>Atzīmētie piemēri ({evidence.length})</h4>
      <p className="muted">{sourceSummary.join(' · ')}</p>
      {visibleEvidence.map((item) => <SavedUsageEvidence key={item.id} evidence={item} busy={busy}
        onRemove={onRemove ? () => onRemove(item.id) : undefined} />)}
      {pageCount > 1 && <nav className="corpus-pagination" aria-label="Atzīmēto piemēru lapošana">
        <button type="button" title="Iepriekšējā lapa" aria-label="Iepriekšējā lapa" disabled={currentPage === 0}
          onClick={() => setPage(currentPage - 1)}>‹</button>
        <small aria-live="polite">{currentPage + 1} / {pageCount}</small>
        <button type="button" title="Nākamā lapa" aria-label="Nākamā lapa" disabled={currentPage + 1 === pageCount}
          onClick={() => setPage(currentPage + 1)}>›</button>
      </nav>}
    </section>
  )
}
