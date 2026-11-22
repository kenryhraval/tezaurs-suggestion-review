import { useState } from 'react'
import type { ReviewSource } from '../../api/types'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'

type Props = {
  sources: ReviewSource[]
  onDetach?: (suggestionId: number) => Promise<void>
}

function AdditionalInformation({ source }: { source: ReviewSource }) {
  if (!source.source && !source.flagInfo && !source.notes && !source.contact) return null

  return (
    <details className="suggestion-additional">
      <summary>Papildu informācija</summary>
      {source.source && <div><strong>Avots:</strong> {source.source}</div>}
      {source.flagInfo && <div><strong>Joma/stils:</strong> {source.flagInfo}</div>}
      {source.notes && <div><strong>Piezīmes:</strong> {source.notes}</div>}
      {source.contact && <div><strong>Saziņai:</strong> {source.contact}</div>}
    </details>
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
    <section className="suggestion-sources" aria-label="Iesniegtie ieteikumi">
      <table className="table table-sm suggestion-source-table">
        <thead>
          <tr>
            <th scope="col">Vārds</th>
            <th scope="col">Iesniegtais saturs</th>
          </tr>
        </thead>
        <tbody>
          {sources.map((source) => (
            <tr key={source.id}>
              <td>
                <strong>{source.submittedTerm}</strong>
                <time dateTime={source.createdAt}>{new Date(source.createdAt).toLocaleDateString('lv-LV')}</time>
              </td>
              <td>
                <div className="suggestion-source-content">
                  <div>
                    {source.submittedDefinition && <p>{source.submittedDefinition}</p>}
                    {source.usageExample && <blockquote>{source.usageExample}</blockquote>}
                    <AdditionalInformation source={source} />
                  </div>
                  {canDetach && <button type="button" className="icon-button" disabled={saving}
                    title="Atdalīt ieteikumu" aria-label={`Atdalīt ieteikumu “${source.submittedTerm}”`}
                    onClick={() => { setError(''); setDetaching(source) }}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M9 7H7a5 5 0 0 0 0 10h2M15 7h2a5 5 0 0 1 0 10h-2M8 12h2m4 0h2M13 3l-2 4m2 10-2 4" />
                    </svg>
                  </button>}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {detaching && (
        <ConfirmationDialog
          message={`Ieteikums “${detaching.submittedTerm}” tiks atdalīts un atgriezts sarakstā “Jauns”. Šī perēkļa stāvoklis un melnraksti paliks nemainīti un netiks kopēti līdzi.`}
          busy={saving}
          error={error}
          onConfirm={() => confirmDetach()}
          onCancel={() => setDetaching(null)}
        />
      )}
    </section>
  )
}
