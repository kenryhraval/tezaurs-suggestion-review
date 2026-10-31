import { useState, type ReactNode } from 'react'
import {
  correctReviewTerm,
  detachSuggestion,
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
import { SuggestionIntake } from './SuggestionIntake'
import { EntryDraftForm } from './EntryDraftForm'
import { ExistingEntryDetails } from './ExistingEntryDetails'
import { SenseDraftForm } from './SenseDraftForm'
import { SuggestionSourcesTable } from './SuggestionSourcesTable'
import { ConfirmationDialog } from './ConfirmationDialog'
import { EditButton } from './EditButton'

type Props = {
  item: QueueItem
  onStatusChange: (status: SuggestionStatus) => Promise<Review | null>
  onDuplicate: (sourceSuggestionId: number, targetReviewId: string) => Promise<void>
  onReviewUpdated: (review: Review) => void
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

export function SuggestionDetails({ item, onStatusChange, onDuplicate, onReviewUpdated }: Props) {
  const [error, setError] = useState('')
  const [pendingStatus, setPendingStatus] = useState<SuggestionStatus | null>(null)
  const [changingStatus, setChangingStatus] = useState(false)
  const [statusError, setStatusError] = useState('')
  const [editingTitle, setEditingTitle] = useState(false)
  const [editedTitle, setEditedTitle] = useState('')
  const [savingTitle, setSavingTitle] = useState(false)
  const [titleError, setTitleError] = useState('')
  const suggestion = item.itemType === 'SUGGESTION' ? item : null
  const requestedReviewId = item.itemType === 'REVIEW' ? item.id : null
  const reviewState = useSuggestionReview(requestedReviewId)
  const review = reviewState.review
  const reviewLoading = item.itemType === 'REVIEW' && !review && !reviewState.error
  const reviewInProgress = item.status === 'IN_PROGRESS'
  const needsCaseChoice = item.status === 'READY_FOR_REVIEW'
    && review?.sources.length === 1 && review.tezaursStatus === 'NOT_CHECKED'
  const tezaursEntryExists = review?.tezaursStatus === 'FOUND'
    || review?.tezaursStatus === 'MEANING_ALREADY_EXISTS'
    || review?.tezaursStatus === 'MEANING_NEEDS_EDITING'
    || review?.tezaursStatus === 'MEANING_NOT_FOUND'
  const senseDraftAvailable = review?.tezaursStatus === 'NOT_FOUND'
    || review?.tezaursStatus === 'MEANING_NOT_FOUND' || review?.tezaursStatus === 'MEANING_NEEDS_EDITING'
  const entryDraftAvailable = review?.tezaursStatus === 'NOT_FOUND'
  const visibleTransitions = suggestionStatusTransitions[item.status].filter((status) => {
    if (status === 'ALREADY_EXISTS') return review?.tezaursStatus === 'MEANING_ALREADY_EXISTS'
    if (status === 'COMPLETED') {
      return review?.canComplete === true
        && review.tezaursStatus !== 'MEANING_ALREADY_EXISTS'
    }
    return true
  })

  async function changeStatus(status: SuggestionStatus) {
    const updatedReview = await onStatusChange(status)
    if (updatedReview) reviewState.setReview(updatedReview)
    return updatedReview
  }

  function requestStatusChange(status: SuggestionStatus) {
    setStatusError('')
    setPendingStatus(status)
  }

  async function confirmStatusChange() {
    if (!pendingStatus || changingStatus) return
    setChangingStatus(true)
    setStatusError('')
    try {
      const updatedReview = await changeStatus(pendingStatus)
      if (updatedReview) {
        setPendingStatus(null)
      } else {
        setStatusError('Neizdevās mainīt statusu. Mēģiniet vēlreiz.')
      }
    } finally {
      setChangingStatus(false)
    }
  }

  async function handleTermCorrection() {
    if (!review || savingTitle) return
    const title = editedTitle.trim()
    if (!title) {
      setTitleError('Ievadiet nosaukumu.')
      return
    }
    setSavingTitle(true)
    setTitleError('')
    try {
      const updated = await correctReviewTerm(review.id, title)
      reviewState.setReview(updated)
      onReviewUpdated(updated)
      setEditingTitle(false)
    } catch (cause) {
      setTitleError(cause instanceof Error ? cause.message : 'Neizdevās labot nosaukumu.')
    } finally {
      setSavingTitle(false)
    }
  }

  async function handleDetach(sourceId: number) {
    if (!review) return
    const updated = await detachSuggestion(review.id, sourceId)
    reviewState.setReview(updated)
    onReviewUpdated(updated)
  }

  async function handleTezaursCheck(status: TezaursStatus, entryId: number | null) {
    if (!review) return false
    setError('')
    try {
      const updatedReview = await saveTezaursCheck(review.id, status, entryId)
      reviewState.setReview(updatedReview)
      onReviewUpdated(updatedReview)
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
  function renderEntry(senses: ReactNode, onAddSense?: () => void) {
    if (!review) return null
    if (entryDraftAvailable) {
      return (
        <EntryDraftForm reviewId={review.id} editable={reviewInProgress}
          onReviewUpdated={reviewState.setReview} onAddSense={onAddSense}>
          {senses}
        </EntryDraftForm>
      )
    }
    if (tezaursEntryExists && review.matchedEntryId !== null) {
      return (
        <ExistingEntryDetails entryId={review.matchedEntryId} onAddSense={onAddSense}>
          {senses}
        </ExistingEntryDetails>
      )
    }
    return null
  }

  return (
    <article className="details">
      {pendingStatus && (
        <ConfirmationDialog
          message={pendingStatus === 'ALREADY_EXISTS'
            ? 'Ieteiktā nozīme jau ir Tēzaurā. Izskatīšana tiks slēgta ar statusu “Jau bija”.'
            : `Ieteikuma “${displayedTerm}” statuss tiks mainīts no “${suggestionStatusLabels[item.status]}” uz “${suggestionStatusLabels[pendingStatus]}”.`}
          busy={changingStatus}
          error={statusError}
          onConfirm={() => void confirmStatusChange()}
          onCancel={() => setPendingStatus(null)}
        />
      )}
      <div className="review-heading">
        <span className="muted">{suggestionStatusLabels[item.status]}</span>
        <h2 className="suggestion-title">{displayedTerm}</h2>
        {reviewInProgress && review && (
          <EditButton label="Labot izskatīšanas nosaukumu" onClick={() => {
            setEditedTitle(displayedTerm)
            setTitleError('')
            setEditingTitle(true)
          }} />
        )}
      </div>
      {editingTitle && (
        <ConfirmationDialog
          title="Labot izskatīšanas nosaukumu"
          message="Mainīsies tikai izskatīšanas nosaukums. Iesniegtie ieteikumi un šķirkļa melnraksts paliks nemainīti."
          busy={savingTitle}
          error={titleError}
          onConfirm={() => void handleTermCorrection()}
          onCancel={() => setEditingTitle(false)}
        >
          <label>
            Nosaukums
            <input
              autoFocus
              value={editedTitle}
              onChange={(event) => setEditedTitle(event.target.value)}
              disabled={savingTitle}
              maxLength={255}
              required
            />
          </label>
        </ConfirmationDialog>
      )}

      {sources.length > 0 && (
        <SuggestionSourcesTable sources={sources} onDetach={review ? handleDetach : undefined} />
      )}

      {(error || reviewState.error) && <p className="error">{error || reviewState.error}</p>}
      {reviewLoading && <p className="muted">Ielādē izskatīšanas informāciju...</p>}

      {(suggestion || review) && (
        <SuggestionIntake status={item.status} onStatusChange={changeStatus}
          review={review} needsCaseChoice={needsCaseChoice} onDuplicate={onDuplicate} />
      )}

      {(reviewInProgress || item.status === 'ALREADY_EXISTS') && review && (
        <TezaursCheckForm
          key={review.reviewedTerm}
          reviewId={review.id}
          status={review.tezaursStatus}
          matchedEntryId={review.matchedEntryId}
          caseClosed={item.status === 'ALREADY_EXISTS'}
          onSave={handleTezaursCheck}
        />
      )}

      {item.status !== 'ALREADY_EXISTS' && (senseDraftAvailable && review ? (
        <SenseDraftForm
          key={`${review.id}:${review.matchedEntryId}:${review.tezaursStatus}`}
          initialGloss={initialGloss}
          matchedEntryId={review.matchedEntryId}
          onReviewUpdated={reviewState.setReview}
          reviewId={review.id}
          editExistingSenses={review.tezaursStatus === 'MEANING_NEEDS_EDITING'}
          editable={reviewInProgress}
          renderEntry={renderEntry}
        />
      ) : renderEntry(null))}

      {review && !needsCaseChoice && item.status !== 'GARBAGE' && (
        <div className="review-return">
          {visibleTransitions.map((status) => (
            <button
              key={status}
              type="button"
              onClick={() => requestStatusChange(status)}
            >
              {item.status === 'READY_FOR_REVIEW' && status === 'IN_PROGRESS'
                ? 'Sākt izskatīšanu'
                : status === 'ALREADY_EXISTS' ? 'Jau bija' : suggestionStatusLabels[status]}
            </button>
          ))}
        </div>
      )}
    </article>
  )
}
