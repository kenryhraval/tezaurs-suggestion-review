import { useState } from 'react'
import type { ReviewSource } from '../api/types'
import { ConfirmationDialog } from './ConfirmationDialog'

type Props = {
  sources: ReviewSource[]
  onDetach?: (sourceId: number) => Promise<void>
}

function AdditionalInformation({ source }: { source: ReviewSource }) {
  return (
    <>
      {source.source && <div><strong>Avots:</strong> {source.source}</div>}
      {source.flagInfo && <div><strong>Joma/stils:</strong> {source.flagInfo}</div>}
      {source.notes && <div><strong>Piezīmes:</strong> {source.notes}</div>}
      {source.contact && <div><strong>Saziņai:</strong> {source.contact}</div>}
    </>
  )
}

export function SuggestionSourcesTable({ sources, onDetach }: Props) {
  const [detaching, setDetaching] = useState<ReviewSource | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const canDetach = sources.length > 1 && onDetach !== undefined

  async function confirmDetach() {
    if (!detaching || !onDetach || saving) {
      return
    }
    setSaving(true)
    setError('')
    try {
      await onDetach(detaching.id)
      setDetaching(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās atdalīt ieteikumu.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="suggestion-sources">
      <h3>{sources.length === 1 ? 'Iesniegtais ieteikums' : `Iesniegtie ieteikumi (${sources.length})`}</h3>
      <div className="suggestion-sources-table">
        <table>
          <thead>
            <tr>
              <th>Vārds</th>
              <th>Nozīme</th>
              <th>Piemērs</th>
              <th>Papildu informācija</th>
              {canDetach && <th>Darbības</th>}
            </tr>
          </thead>
          <tbody>
            {sources.map((source) => (
              <tr key={source.id}>
                <td>
                  <strong>{source.submittedTerm}</strong>
                  <small>{new Date(source.createdAt).toLocaleDateString('lv-LV')}</small>
                </td>
                <td>{source.submittedDefinition}</td>
                <td>{source.usageExample || '—'}</td>
                <td><AdditionalInformation source={source} /></td>
                {canDetach && (
                  <td><button type="button" className="icon-button" title="Atdalīt ieteikumu"
                    aria-label={`Atdalīt ieteikumu “${source.submittedTerm}”`} onClick={() => {
                    setError('')
                    setDetaching(source)
                  }}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M9 7H7a5 5 0 0 0 0 10h2M15 7h2a5 5 0 0 1 0 10h-2M8 12h2m4 0h2M13 3l-2 4m2 10-2 4" />
                    </svg>
                  </button></td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {detaching && (
        <ConfirmationDialog
          message={`Ieteikums “${detaching.submittedTerm}” tiks atdalīts un atgriezts sarakstā “Jauns”. Šīs izskatīšanas statuss un melnraksti paliks nemainīti un netiks kopēti līdzi.`}
          busy={saving}
          error={error}
          onConfirm={() => confirmDetach()}
          onCancel={() => setDetaching(null)}
        />
      )}
    </section>
  )
}
