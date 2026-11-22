import { useState, type ReactNode } from 'react'
import type { TezaursEntryCandidate } from '../../api/types'
import { TezaursEntryLink } from './TezaursEntryLink'

type Props = {
  entry: TezaursEntryCandidate | null
  loading: boolean
  defaultOpen: boolean
  complete: boolean
  children: ReactNode
}

export function ExistingEntryReference({ entry, loading, children, defaultOpen, complete }: Props) {
  const [expanded, setExpanded] = useState(defaultOpen)
  return <details open={expanded} onToggle={(event) => setExpanded(event.currentTarget.open)}
    className="entry-draft-section review-section">
    <summary className={!expanded && complete ? 'section-title confirmed' : 'section-title'}>5. Labojumu melnraksts</summary>
    {loading && <p className="muted">Ielādē šķirkli...</p>}
    {entry && <div className="draft-heading">
      <span className="existing-record"><strong>{entry.heading}</strong> <TezaursEntryLink humanKey={entry.humanKey} /></span>
    </div>}
    {children}
  </details>
}
