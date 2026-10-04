import { useState, type FormEvent } from 'react'
import {
  changeSuggestionStatus,
  correctReviewTerm,
  saveTezaursCheck,
} from '../api'
import { suggestionStatusLabels, suggestionStatusTransitions } from '../labels'
import type { Suggestion, SuggestionStatus, TezaursStatus } from '../types'
import { useSuggestionReview } from '../hooks/useSuggestionReview'
import { useSuggestionMeaning } from '../hooks/useSuggestionMeaning'
import { useCorpusExamples } from '../hooks/useCorpusExamples'
import { MeaningSection } from './MeaningSection'
import { TezaursCheckForm } from './TezaursCheckForm'
import { CorpusExampleLinks } from './CorpusExampleLinks'

type Props = {
  suggestion: Suggestion
  onUpdated: (suggestion: Suggestion) => void
}

export function SuggestionDetails({ suggestion, onUpdated }: Props) {
  const [error, setError] = useState('')
  const reviewState = useSuggestionReview(suggestion.id, suggestion.status)
  const review = reviewState.review
  const reviewId = review?.id ?? null
  const reviewInProgress = suggestion.status === 'IN_PROGRESS'
  const tezaursEntryExists = review?.tezaursStatus === 'FOUND'
    || review?.tezaursStatus === 'MEANING_FOUND'
    || review?.tezaursStatus === 'MEANING_NOT_FOUND'
  const meaningEditingAvailable = reviewInProgress
    && (review?.tezaursStatus === 'MEANING_NOT_FOUND'
      || review?.tezaursStatus === 'NOT_FOUND')
  const corpusReviewAvailable = reviewInProgress
    && (review?.tezaursStatus === 'NOT_FOUND'
      || review?.tezaursStatus === 'MEANING_NOT_FOUND')
  const meaning = useSuggestionMeaning(reviewId, review !== null)
  const corpusExamples = useCorpusExamples(reviewId, corpusReviewAvailable)

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
    if (!review) return
    setError('')
    const data = new FormData(event.currentTarget)

    try {
      reviewState.setReview(await correctReviewTerm(review.id, String(data.get('reviewedTerm'))))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās labot vārdu.')
    }
  }

  async function handleTezaursCheck(status: TezaursStatus, entryId: number | null) {
    if (!review) return
    setError('')
    try {
      reviewState.setReview(await saveTezaursCheck(review.id, status, entryId))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt pārbaudi.')
    }
  }

  const displayedTerm = review?.reviewedTerm ?? suggestion.submittedTerm

  return (
    <article className="details">
      {reviewInProgress && review ? (
        <form
          key={review.reviewedTerm}
          className="title-form"
          onSubmit={handleTermCorrection}
        >
          <label>
            <span className="visually-hidden">Pārskatītais vārds</span>
            <input
              aria-label="Pārskatītais vārds"
              name="reviewedTerm"
              defaultValue={displayedTerm}
              maxLength={255}
              required
            />
          </label>
        </form>
      ) : (
        <h2 className="suggestion-title">{displayedTerm}</h2>
      )}

      <p className="muted">
        Iesniegts {new Date(suggestion.createdAt).toLocaleDateString('lv-LV')}
      </p>

      {review?.reviewedTerm && review.reviewedTerm !== suggestion.submittedTerm && (
        <p><strong>Iesniegtais vārds:</strong> {suggestion.submittedTerm}</p>
      )}
      <p><strong>Iesniegtā nozīme:</strong> {suggestion.submittedDefinition}</p>
      {suggestion.usageExample && (
        <p><strong>Lietojuma piemērs:</strong> {suggestion.usageExample}</p>
      )}
      {suggestion.notes && <p><strong>Piezīmes:</strong> {suggestion.notes}</p>}
      {suggestion.source && <p><strong>Avots:</strong> {suggestion.source}</p>}
      {suggestion.flagInfo && <p><strong>Joma/stils:</strong> {suggestion.flagInfo}</p>}
      {suggestion.contact && <p><strong>Saziņai:</strong> {suggestion.contact}</p>}

      {(error || reviewState.error) && <p className="error">{error || reviewState.error}</p>}
      {reviewState.loading && <p className="muted">Ielādē pārskatu...</p>}

      {suggestion.status === 'NEW' && (
        <section className="initial-review">
          <h3>Sākotnējā izvērtēšana</h3>
          <p>Atzīmējiet ieteikumu izskatīšanai vai pārvietojiet to uz miskasti.</p>
        </section>
      )}

      {suggestion.status === 'GARBAGE' && (
        <section className="trash-message">
          <p>Šis ieteikums atrodas miskastē.</p>
        </section>
      )}

      {reviewInProgress && review && (
        <>
          <div className="checks">
            <TezaursCheckForm
              status={review.tezaursStatus}
              matchedEntryId={review.matchedEntryId}
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
              {review.tezaursStatus === 'MEANING_FOUND'
                && 'Šķirklis un ieteiktā nozīme jau ir Tēzaurā. Papildu analīze nav nepieciešama.'}
              {review.tezaursStatus === 'MEANING_NOT_FOUND'
                && 'Šķirklis ir Tēzaurā, bet ieteiktās nozīmes nav. Papildināšana vēlāk jāveic Tēzaurā.'}
              {review.tezaursStatus === 'FOUND'
                && 'Šķirklis ir Tēzaurā. Vēl jānorāda, vai ieteiktā nozīme tajā jau ir.'}
            </p>
          )}
        </>
      )}

      {review && (
        <MeaningSection
          current={meaning.current}
          history={meaning.history}
          editingAvailable={meaningEditingAvailable}
          loading={meaning.loading}
          error={meaning.error}
          onRevise={meaning.revise}
        />
      )}

      <div className="review-return">
        {suggestionStatusTransitions[suggestion.status].map((status) => (
          <button
            key={status}
            type="button"
            onClick={() => void handleStatus(status)}
          >
            {suggestionStatusLabels[status]}
          </button>
        ))}
      </div>
    </article>
  )
}
