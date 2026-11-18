import { useState } from 'react'
import type { UsageEvidence } from '../../api/types'
import { SavedUsageEvidence } from './SavedUsageEvidence'
import { Dialog } from '../shared/Dialog'

type Props = {
  evidence: UsageEvidence[]
  busy: boolean
  onRemove?: (id: string) => void
}

export function SelectedUsageEvidence({ evidence, busy, onRemove }: Props) {
  const [open, setOpen] = useState(false)
  if (evidence.length === 0 && !open) {
    return null
  }

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
      <button className="selected-evidence-toggle" type="button" onClick={() => setOpen(true)}>
        Atzīmētie piemēri ({evidence.length})
      </button>
      {open && <Dialog title={`Atzīmētie piemēri (${evidence.length})`} busy={busy} wide
        onCancel={() => setOpen(false)}>
        {evidence.length > 0 ? <>
          <p className="muted">{sourceSummary.join(' · ')}</p>
          <div className="selected-evidence-list" role="region" aria-label="Atzīmētie piemēri" tabIndex={0}>
            {evidence.map((item) => <SavedUsageEvidence key={item.id} evidence={item} busy={busy}
              onRemove={onRemove ? () => onRemove(item.id) : undefined} />)}
          </div>
        </> : <p className="muted">Nav atzīmētu piemēru.</p>}
        <div className="modal-footer">
          <button className="btn btn-sm btn-light border" type="button" disabled={busy}
            onClick={() => setOpen(false)}>Aizvērt</button>
        </div>
      </Dialog>}
    </section>
  )
}
