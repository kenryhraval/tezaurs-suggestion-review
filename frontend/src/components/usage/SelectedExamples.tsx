import { useState } from 'react'
import type { Example } from '../../api/types'
import { SavedExample } from './SavedExample'
import { Dialog } from '../shared/Dialog'

type Props = {
  examples: Example[]
  busy: boolean
  onRemove?: (id: string) => void
}

export function SelectedExamples({ examples, busy, onRemove }: Props) {
  const [open, setOpen] = useState(false)
  if (examples.length === 0 && !open) {
    return null
  }

  const sourceCounts = new Map<string, number>()
  for (const item of examples) {
    const source = item.corpusLabel ?? new URL(item.url).hostname
    sourceCounts.set(source, (sourceCounts.get(source) ?? 0) + 1)
  }

  const sourceSummary: string[] = []
  for (const [source, count] of sourceCounts) {
    sourceSummary.push(`${source}: ${count}`)
  }

  return (
    <section className="selected-examples">
      <button className="selected-examples-toggle" type="button" disabled={busy} onClick={() => setOpen(true)}>
        Atzīmētie piemēri ({examples.length})
      </button>
      {open && <Dialog title={`Atzīmētie piemēri (${examples.length})`} busy={busy} wide
        onCancel={() => setOpen(false)}>
        {examples.length > 0 ? <>
          <p className="muted">{sourceSummary.join(' · ')}</p>
          <div className="selected-examples-list" role="region" aria-label="Atzīmētie piemēri" tabIndex={0}>
            {examples.map((item) => <SavedExample key={item.id} example={item} busy={busy}
              onRemove={onRemove && !item.inDraft ? () => onRemove(item.id) : undefined} />)}
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
