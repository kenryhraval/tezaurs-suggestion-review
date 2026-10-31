import { useState, type FormEvent } from 'react'
import {
  correctReviewTerm,
  saveTezaursCheck,
} from '../api'
import { suggestionStatusLabels, suggestionStatusTransitions } from '../labels'
import type {
  QueueItem,
  Review,
  ReviewSource,
  Suggestion,
  SuggestionStatus,
  TezaursStatus,
} from '../types'
import { useSuggestionReview } from '../hooks/useSuggestionReview'
import { TezaursCheckForm } from './TezaursCheckForm'
import { DuplicateReviewCombobox } from './DuplicateReviewCombobox'
import { EntryDraftForm } from './EntryDraftForm'
import { ExistingEntryDetails } from './ExistingEntryDetails'
import { SenseDraftForm } from './SenseDraftForm'
import { SuggestionSourcesTable } from './SuggestionSourcesTable'

type Props = {
  item: QueueItem
  onStatusChange: (status: SuggestionStatus) => Promise<Review | null>
  onDuplicate: (sourceSuggestionId: number, reviewId: string) => Promise<void>
}

function sourceFromSuggestion(suggestion: Suggestion): ReviewSource {
  return {
    id: suggestion.id,
    submittedTerm: suggestion.submittedTerm,
    submittedDefinition: suggestion.submittedDefinition,
    usageExample: suggestion.usageExample,
    source: suggestion.source,
    flagInfo: suggestion.flagInfo,
    notes: suggestion.notes,
    contact: suggestion.contact,
    createdAt: suggestion.createdAt,
  }
}

export function SuggestionDetails({ item, onStatusChange, onDuplicate }: Props) {
  const [error, setError] = useState('')
  const suggestion = item.itemType === 'SUGGESTION' ? item : null
  const requestedReviewId = item.itemType === 'REVIEW' ? item.id : null
  const reviewState = useSuggestionReview(requestedReviewId)
  const review = reviewState.review
  const reviewLoading = item.itemType === 'REVIEW' && !review && !reviewState.error
  const reviewInProgress = item.status === 'IN_PROGRESS'
  const tezaursEntryExists = review?.tezaursStatus === 'FOUND'
    || review?.tezaursStatus === 'MEANING_FOUND'
    || review?.tezaursStatus === 'MEANING_NOT_FOUND'
  const senseEditingAvailable = reviewInProgress
    && (review?.tezaursStatus === 'NOT_FOUND'
      || review?.tezaursStatus === 'MEANING_NOT_FOUND')
  const visibleTransitions = suggestionStatusTransitions[item.status].filter((status) => {
    if (status === 'ALREADY_EXISTS') return review?.tezaursStatus === 'MEANING_FOUND'
    if (status === 'COMPLETED') {
      return review?.canComplete === true
        && review.tezaursStatus !== 'MEANING_FOUND'
    }
    return true
  })

  async function handleStatus(status: SuggestionStatus) {
    if (status === 'ALREADY_EXISTS'
      && !window.confirm('Vai ieteiktā nozīme tiešām jau ir Tēzaurā un ieteikumu var slēgt kā “Jau bija”?')) {
      return
    }
    const updatedReview = await onStatusChange(status)
    if (updatedReview) reviewState.setReview(updatedReview)
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
    if (!review) return false
    setError('')
    try {
      reviewState.setReview(await saveTezaursCheck(review.id, status, entryId))
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt pārbaudi.')
      return false
    }
  }

  const listTerm = item.itemType === 'SUGGESTION'
    ? item.submittedTerm : item.reviewedTerm
  const displayedTerm = review?.reviewedTerm ?? listTerm
  const sources = suggestion
    ? [sourceFromSuggestion(suggestion)]
    : Array.isArray(review?.sources) ? review.sources : []
  const initialGloss = sources.length === 1 ? sources[0].submittedDefinition : ''

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

      {sources.length > 0 && <SuggestionSourcesTable sources={sources} />}

      {(error || reviewState.error) && <p className="error">{error || reviewState.error}</p>}
      {reviewLoading && <p className="muted">Ielādē izskatīšanas informāciju...</p>}

      {suggestion && (
        <section className="initial-review">
          <h3>Sākotnējā izvērtēšana</h3>
          <p>Atzīmējiet ieteikumu izskatīšanai, pārvietojiet to uz miskasti vai piesaistiet dublikātu.</p>
          <DuplicateReviewCombobox
            initialQuery={suggestion.submittedTerm}
            onSelect={(reviewId) => onDuplicate(suggestion.id, reviewId)}
          />
        </section>
      )}

      {item.status === 'GARBAGE' && (
        <section className="trash-message">
          <p>Šis ieteikums atrodas miskastē.</p>
        </section>
      )}

      {reviewInProgress && review && (
        <>
          <div className="checks">
            <TezaursCheckForm
              key={review.reviewedTerm}
              reviewId={review.id}
              status={review.tezaursStatus}
              matchedEntryId={review.matchedEntryId}
              onSave={handleTezaursCheck}
            />

          </div>

          {tezaursEntryExists && (
            <p className="existing-entry-message">
              {review.tezaursStatus === 'MEANING_FOUND'
                && 'Šķirklis un ieteiktā nozīme jau ir Tēzaurā. Pabeidziet ieteikumu ar pogu “Jau bija”.'}
              {review.tezaursStatus === 'MEANING_NOT_FOUND'
                && 'Šķirklis ir Tēzaurā, bet ieteiktās nozīmes nav. Esošie dati zemāk ir tikai lasāmi.'}
              {review.tezaursStatus === 'FOUND'
                && 'Šķirklis ir Tēzaurā. Vēl jānorāda, vai ieteiktā nozīme tajā jau ir.'}
            </p>
          )}
        </>
      )}

      {review?.tezaursStatus === 'NOT_FOUND' && reviewInProgress && (
        <EntryDraftForm
          reviewId={review.id}
          onReviewUpdated={reviewState.setReview}
        />
      )}

      {tezaursEntryExists && review?.matchedEntryId !== null && (
        <ExistingEntryDetails entryId={review.matchedEntryId} />
      )}

      {senseEditingAvailable && review && (
        <SenseDraftForm
          initialGloss={initialGloss}
          matchedEntryId={review.matchedEntryId}
          onReviewUpdated={reviewState.setReview}
          reviewId={review.id}
        />
      )}

      {(suggestion || review) && (
        <div className="review-return">
          {visibleTransitions.map((status) => (
            <button
              key={status}
              type="button"
              onClick={() => void handleStatus(status)}
            >
              {status === 'ALREADY_EXISTS' ? 'Jau bija' : suggestionStatusLabels[status]}
            </button>
          ))}
        </div>
      )}
    </article>
  )
}
