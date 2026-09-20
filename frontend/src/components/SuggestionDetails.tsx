import { useState, type FormEvent } from 'react'
import {
  changeSuggestionStatus,
  correctSuggestionTerm,
  saveCorpusCheck,
  saveTezaursCheck,
} from '../api'
import {
  checkStatuses,
  checkStatusLabels,
  suggestionStatuses,
  suggestionStatusLabels,
} from '../labels'
import type { CheckStatus, Suggestion, SuggestionStatus } from '../types'
import { MeaningSection } from './MeaningSection'
import { TezaursCheckForm } from './TezaursCheckForm'

type Props = {
  suggestion: Suggestion
  onUpdated: (suggestion: Suggestion) => void
}

export function SuggestionDetails({ suggestion, onUpdated }: Props) {
  const [error, setError] = useState('')

  async function handleStatus(status: SuggestionStatus) {
    setError('')
    try {
      onUpdated(await changeSuggestionStatus(suggestion.id, status))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt statusu.')
    }
  }

  async function handleTermCorrection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const data = new FormData(event.currentTarget)

    try {
      onUpdated(
        await correctSuggestionTerm(suggestion.id, String(data.get('reviewedTerm'))),
      )
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās labot vārdu.')
    }
  }

  async function handleTezaursCheck(status: CheckStatus, entryId: number | null) {
    setError('')
    try {
      onUpdated(await saveTezaursCheck(suggestion.id, status, entryId))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt pārbaudi.')
    }
  }

  async function handleCorpusCheck(status: CheckStatus) {
    setError('')
    try {
      onUpdated(await saveCorpusCheck(suggestion.id, status))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt pārbaudi.')
    }
  }

  return (
    <article className="details">
      <h2>{suggestion.reviewedTerm ?? suggestion.submittedTerm}</h2>
      <p className="muted">
        Iesniegts {new Date(suggestion.createdAt).toLocaleDateString('lv-LV')}
      </p>

      {suggestion.reviewedTerm && (
        <p><strong>Iesniegtais vārds:</strong> {suggestion.submittedTerm}</p>
      )}
      {suggestion.usageExample && (
        <p><strong>Lietojuma piemērs:</strong> {suggestion.usageExample}</p>
      )}
      {suggestion.notes && <p><strong>Piezīmes:</strong> {suggestion.notes}</p>}
      {(suggestion.submitterName || suggestion.submitterEmail) && (
        <p>
          <strong>Iesniedzējs:</strong>{' '}
          {suggestion.submitterName ?? suggestion.submitterEmail}
          {suggestion.submitterName && suggestion.submitterEmail
            ? ` (${suggestion.submitterEmail})`
            : ''}
        </p>
      )}

      {error && <p className="error">{error}</p>}

      <div className="review-fields">
        <label>
          Izskatīšanas statuss
          <select
            value={suggestion.status}
            onChange={(event) =>
              void handleStatus(event.target.value as SuggestionStatus)
            }
          >
            {suggestionStatuses.map((status) => (
              <option key={status} value={status}>
                {suggestionStatusLabels[status]}
              </option>
            ))}
          </select>
        </label>

        <form
          key={suggestion.reviewedTerm}
          className="term-form"
          onSubmit={handleTermCorrection}
        >
          <label>
            Pārskatītais vārds
            <input
              name="reviewedTerm"
              defaultValue={suggestion.reviewedTerm ?? suggestion.submittedTerm}
              maxLength={255}
              required
            />
          </label>
          <button type="submit">Saglabāt vārdu</button>
        </form>
      </div>

      <div className="checks">
        <TezaursCheckForm
          key={`${suggestion.tezaursStatus}-${suggestion.matchedEntryId}`}
          initialStatus={suggestion.tezaursStatus}
          initialEntryId={suggestion.matchedEntryId}
          onSave={handleTezaursCheck}
        />

        <div>
          <h3>Pārbaude korpusā</h3>
          <label>
            Rezultāts
            <select
              value={suggestion.corpusStatus}
              onChange={(event) =>
                void handleCorpusCheck(event.target.value as CheckStatus)
              }
            >
              {checkStatuses.map((status) => (
                <option key={status} value={status}>
                  {checkStatusLabels[status]}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <MeaningSection suggestionId={suggestion.id} />
    </article>
  )
}
