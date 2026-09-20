import { useState, type FormEvent } from 'react'
import {
  changeSuggestionStatus,
  correctSuggestionTerm,
  saveTezaursCheck,
} from '../api'
import type {
  CompletionBlockReason,
  Suggestion,
  SuggestionStatus,
  TezaursStatus,
} from '../types'
import { useSuggestionMeaning } from '../hooks/useSuggestionMeaning'
import { useCorpusExamples } from '../hooks/useCorpusExamples'
import { MeaningSection } from './MeaningSection'
import { TezaursCheckForm } from './TezaursCheckForm'
import { CorpusExampleLinks } from './CorpusExampleLinks'

type Props = {
  suggestion: Suggestion
  onUpdated: (suggestion: Suggestion) => void
}

const completionBlockMessages: Record<CompletionBlockReason, string> = {
  TEZAURS_CHECK_REQUIRED: 'Vispirms pabeidziet pārbaudi Tēzaurā.',
  MEANING_CHECK_REQUIRED: 'Norādiet, vai iesniegtā nozīme jau ir šķirklī.',
  CORPUS_CHECK_REQUIRED: 'Vispirms pabeidziet pārbaudi korpusā.',
}

export function SuggestionDetails({
  suggestion,
  onUpdated,
}: Props) {
  const [error, setError] = useState('')
  const meaning = useSuggestionMeaning(suggestion.id)
  const analysisAvailable = suggestion.status !== 'NEW' && suggestion.status !== 'GARBAGE'
  const tezaursEntryExists = suggestion.tezaursStatus === 'FOUND'
    || suggestion.tezaursStatus === 'MEANING_FOUND'
    || suggestion.tezaursStatus === 'MEANING_NOT_FOUND'
  const meaningEditingAvailable = analysisAvailable
    && (suggestion.tezaursStatus === 'MEANING_NOT_FOUND'
      || suggestion.tezaursStatus === 'NOT_FOUND')
  const corpusReviewAvailable = suggestion.tezaursStatus === 'NOT_FOUND'
    || suggestion.tezaursStatus === 'MEANING_NOT_FOUND'
  const corpusExamples = useCorpusExamples(suggestion.id, corpusReviewAvailable)

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

  async function handleTezaursCheck(status: TezaursStatus, entryId: number | null) {
    setError('')
    try {
      onUpdated(await saveTezaursCheck(suggestion.id, status, entryId))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt pārbaudi.')
    }
  }

  return (
    <article className="details">
      {analysisAvailable ? (
        <form
          key={suggestion.reviewedTerm}
          className="title-form"
          onSubmit={handleTermCorrection}
        >
          <label>
            <span className="visually-hidden">Pārskatītais vārds</span>
            <input
              aria-label="Pārskatītais vārds"
              name="reviewedTerm"
              defaultValue={suggestion.reviewedTerm ?? suggestion.submittedTerm}
              maxLength={255}
              required
            />
          </label>
        </form>
      ) : (
        <h2 className="suggestion-title">
          {suggestion.reviewedTerm ?? suggestion.submittedTerm}
        </h2>
      )}

      <p className="muted">
        Iesniegts {new Date(suggestion.createdAt).toLocaleDateString('lv-LV')}
      </p>

      {suggestion.reviewedTerm
        && suggestion.reviewedTerm !== suggestion.submittedTerm && (
        <p><strong>Iesniegtais vārds:</strong> {suggestion.submittedTerm}</p>
      )}
      <p>
        <strong>Iesniegtā nozīme:</strong>{' '}
        {meaning.submitted?.gloss ?? (meaning.loading ? 'Ielādē...' : 'Nav pieejama')}
      </p>
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

      {suggestion.status === 'NEW' && (
        <section className="initial-review">
          <h3>Sākotnējā izvērtēšana</h3>
          <p>Atzīmējiet ieteikumu izskatīšanai vai pārvietojiet to uz miskasti.</p>
          <div className="initial-review-actions">
            <button type="button" onClick={() => void handleStatus('IN_PROGRESS')}>
              ✓ Sākt izskatīšanu
            </button>
            <button type="button" onClick={() => void handleStatus('GARBAGE')}>
              Pārvietot uz miskasti
            </button>
          </div>
        </section>
      )}

      {suggestion.status === 'GARBAGE' && (
        <section className="trash-message">
          <p>Šis ieteikums atrodas miskastē.</p>
        </section>
      )}

      {analysisAvailable && (
        <>
          <div className="checks">
            <TezaursCheckForm
              status={suggestion.tezaursStatus}
              matchedEntryId={suggestion.matchedEntryId}
              onSave={handleTezaursCheck}
            />

            {corpusReviewAvailable && (
              <CorpusExampleLinks
                examples={corpusExamples.examples}
                loading={corpusExamples.loading}
                serverError={corpusExamples.error}
                onAdd={corpusExamples.add}
              />
            )}
          </div>

          {tezaursEntryExists && (
            <p className="existing-entry-message">
              {suggestion.tezaursStatus === 'MEANING_FOUND'
                && 'Šķirklis un ieteiktā nozīme jau ir Tēzaurā. Papildu analīze nav nepieciešama.'}
              {suggestion.tezaursStatus === 'MEANING_NOT_FOUND'
                && 'Šķirklis ir Tēzaurā, bet ieteiktās nozīmes nav. Papildināšana vēlāk jāveic Tēzaurā.'}
              {suggestion.tezaursStatus === 'FOUND'
                && 'Šķirklis ir Tēzaurā. Vēl jānorāda, vai ieteiktā nozīme tajā jau ir.'}
            </p>
          )}
        </>
      )}

      <MeaningSection
        current={meaning.current}
        history={meaning.history}
        editingAvailable={meaningEditingAvailable}
        loading={meaning.loading}
        error={meaning.error}
        onRevise={meaning.revise}
      />

      {analysisAvailable && suggestion.status !== 'COMPLETED' && (
        <div className="review-completion">
          {suggestion.completionBlockReason && (
            <p className="muted">
              {completionBlockMessages[suggestion.completionBlockReason]}
            </p>
          )}
          <button
            type="button"
            disabled={!suggestion.canComplete}
            onClick={() => void handleStatus('COMPLETED')}
          >
            Apstiprināt
          </button>
        </div>
      )}

      {(suggestion.status === 'COMPLETED' || suggestion.status === 'GARBAGE') && (
        <div className="review-return">
          <button type="button" onClick={() => void handleStatus('IN_PROGRESS')}>
            Atpakaļ uz izskatīšanu
          </button>
        </div>
      )}
    </article>
  )
}
