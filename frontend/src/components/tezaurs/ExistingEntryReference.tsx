import { useEffect, useState, type ReactNode } from 'react'
import { getTezaursEntry } from '../../api/client'
import { TezaursEntryLink } from './TezaursEntryLink'

type Props = {
  entryId: number
  defaultOpen: boolean
  canCollapse: boolean
  complete: boolean
  children: ReactNode
  onAddSense?: () => void
}

export function ExistingEntryReference({ entryId, children, onAddSense, defaultOpen, canCollapse, complete }: Props) {
  const [expanded, setExpanded] = useState(defaultOpen)
  const [entry, setEntry] = useState<{ heading: string; humanKey: string } | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let requestIsOutdated = false
    async function loadEntryReference() {
      try {
        const loadedEntry = await getTezaursEntry(entryId)
        if (!requestIsOutdated) {
          setEntry({ heading: loadedEntry.heading, humanKey: loadedEntry.humanKey })
        }
      } catch (cause) {
        if (!requestIsOutdated) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt šķirkļa saiti.')
        }
      }
    }
    loadEntryReference()
    return () => { requestIsOutdated = true }
  }, [entryId])

  return (
    <details open={expanded || !canCollapse} onToggle={(event) => setExpanded(event.currentTarget.open)}
      className="entry-draft-section review-section">
      <summary className={!expanded && complete ? 'section-title confirmed' : 'section-title'}
        aria-disabled={!canCollapse} onClick={(event) => {
          if (!canCollapse) event.preventDefault()
        }}>4. Labojumu melnraksts</summary>
      {error && <p className="error">{error}</p>}
      <div className="draft-heading">
        {entry && <span className="existing-record">
          <strong>{entry.heading}</strong> <TezaursEntryLink humanKey={entry.humanKey} />
        </span>}
        {onAddSense && <button className="btn btn-sm btn-outline-secondary" type="button" onClick={onAddSense}>
          Pievienot nozīmi
        </button>}
      </div>
      {children}
    </details>
  )
}
