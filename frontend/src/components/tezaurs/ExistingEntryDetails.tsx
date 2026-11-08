import { useEffect, useState, type ReactNode } from 'react'
import { getTezaursEntry } from '../../api/client'
import type { TezaursEntryDetails } from '../../api/types'
import { EntryDisplay } from './EntryDisplay'

type Props = {
  entryId: number
  defaultOpen: boolean
  canCollapse: boolean
  complete: boolean
  children?: ReactNode
  onAddSense?: () => void
}

export function ExistingEntryDetails({ entryId, children, onAddSense, defaultOpen, canCollapse, complete }: Props) {
  const [expanded, setExpanded] = useState(defaultOpen)
  const [entry, setEntry] = useState<TezaursEntryDetails | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let requestIsOutdated = false
    async function loadEntry() {
      try {
        const loaded = await getTezaursEntry(entryId)
        if (!requestIsOutdated) {
          setEntry(loaded)
        }
      } catch (cause) {
        if (!requestIsOutdated) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt šķirkli.')
        }
      }
    }

    loadEntry()
    return () => {
      requestIsOutdated = true
    }
  }, [entryId])

  function renderSection(content: ReactNode) {
    return (
      <details open={expanded || !canCollapse} onToggle={(event) => setExpanded(event.currentTarget.open)}
        className="existing-entry review-section">
        <summary className={!expanded && complete ? 'section-title confirmed' : 'section-title'} aria-disabled={!canCollapse} onClick={(event) => {
          if (!canCollapse) event.preventDefault()
        }}>4. Labojumu melnraksts</summary>
        {content}
      </details>
    )
  }

  if (error) {
    return <p className="error">{error}</p>
  }
  if (!entry) {
    return renderSection(<p className="muted">Ielādē esošo šķirkli...</p>)
  }

  return renderSection(
    <EntryDisplay entry={entry} onAddSense={onAddSense}>{children}</EntryDisplay>
  )
}
