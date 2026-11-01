import { UsageCheck } from './UsageCheck'
import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react'
import {
  correctReviewTerm,
  detachSuggestion,
  getReview,
  saveTezaursCheck,
  saveUsageDecision,
} from '../api/client'
import { suggestionStatusLabels } from '../review/statuses'
import type {
  QueueItem,
  Review,
  ReviewSource,
  Suggestion,
  SuggestionStatus,
  TezaursStatus,
  UsageDecision,
} from '../api/types'
import { getReviewWorkflow } from '../review/workflow'
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

function useSuggestionReview(reviewId: string | null) {
  const [review, setReview] = useState<Review | null>(null)
  const [error, setError] = useState('')
  const reviewVersion = useRef(0)

  useEffect(() => {
    if (!reviewId) {
      return
    }
    const id = reviewId

    let requestIsOutdated = false
    reviewVersion.current += 1
    const versionBeforeLoading = reviewVersion.current

    async function loadReview() {
      try {
        const loaded = await getReview(id)
        if (!requestIsOutdated && versionBeforeLoading === reviewVersion.current) {
          setReview(loaded)
          setError('')
        }
      } catch (cause) {
        if (!requestIsOutdated && versionBeforeLoading === reviewVersion.current) {
          setError(cause instanceof Error ? cause.message : 'Pārskatu neizdevās ielādēt.')
        }
      }
    }

    loadReview()
    return () => {
      requestIsOutdated = true
    }
  }, [reviewId])

  function updateReview(saved: Review) {
    reviewVersion.current += 1
    setReview(saved)
    setError('')
  }

  return { review: review?.id === reviewId ? review : null, updateReview, error }
}

export function SuggestionDetails({ item, onStatusChange, onDuplicate, onReviewUpdated }: Props) {
  const [error, setError] = useState('')
  const [pendingCompletionStatus, setPendingCompletionStatus] = useState<'NEEDS_EXPERT' | 'COMPLETED' | null>(null)
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
  const workflow = getReviewWorkflow(review, item.status)
  const status = workflow.status
  const reviewInProgress = status === 'IN_PROGRESS'

  async function changeStatus(status: SuggestionStatus) {
    const updatedReview = await onStatusChange(status)
    if (updatedReview) {
      reviewState.updateReview(updatedReview)
    }
    return updatedReview
  }

  function requestReviewCompletion(nextStatus: 'NEEDS_EXPERT' | 'COMPLETED') {
    setStatusError('')
    setPendingCompletionStatus(nextStatus)
  }

  async function confirmReviewCompletion() {
    if (!pendingCompletionStatus || changingStatus) {
      return
    }
    setChangingStatus(true)
    setStatusError('')
    try {
      const updatedReview = await changeStatus(pendingCompletionStatus)
      if (updatedReview) {
        setPendingCompletionStatus(null)
      } else {
        setStatusError('Neizdevās mainīt statusu. Mēģiniet vēlreiz.')
      }
    } catch (cause) {
      setStatusError(cause instanceof Error ? cause.message : 'Neizdevās mainīt statusu.')
    } finally {
      setChangingStatus(false)
    }
  }

  async function handleTermCorrection() {
    if (!review || savingTitle) {
      return
    }
    const title = editedTitle.trim()
    if (!title) {
      setTitleError('Ievadiet nosaukumu.')
      return
    }
    setSavingTitle(true)
    setTitleError('')
    try {
      const updated = await correctReviewTerm(review.id, title)
      reviewState.updateReview(updated)
      onReviewUpdated(updated)
      setEditingTitle(false)
    } catch (cause) {
      setTitleError(cause instanceof Error ? cause.message : 'Neizdevās labot nosaukumu.')
    } finally {
      setSavingTitle(false)
    }
  }

  async function handleDetach(sourceId: number) {
    if (!review) {
      return
    }
    const updated = await detachSuggestion(review.id, sourceId)
    reviewState.updateReview(updated)
    onReviewUpdated(updated)
  }

  async function handleTezaursCheck(status: TezaursStatus, entryId: number | null) {
    if (!review) {
      return false
    }
    setError('')
    try {
      const updatedReview = await saveTezaursCheck(review.id, status, entryId)
      reviewState.updateReview(updatedReview)
      onReviewUpdated(updatedReview)
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt pārbaudi.')
      throw cause
    }
  }

  async function handleUsageDecision(decision: UsageDecision) {
    if (!review) {
      return false
    }
    const updated = await saveUsageDecision(review.id, decision)
    reviewState.updateReview(updated)
    onReviewUpdated(updated)
    return true
  }

  function openTitleEditor() {
    setEditedTitle(displayedTerm)
    setTitleError('')
    setEditingTitle(true)
  }

  const listTerm = item.itemType === 'SUGGESTION'
    ? item.submittedTerm : item.reviewedTerm
  const displayedTerm = review?.reviewedTerm ?? listTerm
  let sources: ReviewSource[] = []
  if (suggestion) {
    sources = [sourceFromSuggestion(suggestion)]
  } else if (Array.isArray(review?.sources)) {
    sources = review.sources
  }
  const initialGloss = sources.length === 1 ? sources[0].submittedDefinition : ''
  const completionQuestion = review && (
    <div className="draft-actions">
      <p><strong>Vai uzskatāt, ka nepieciešams eksperta apstiprinājums?</strong></p>
      <div className="tezaurs-answer-actions">
        <button type="button" disabled={!reviewInProgress}
          onClick={() => requestReviewCompletion('NEEDS_EXPERT')}>Jā, vajag</button>
        <button type="button" disabled={!reviewInProgress || !review.canComplete}
          onClick={() => requestReviewCompletion('COMPLETED')}>Nē, nevajag</button>
      </div>
    </div>
  )

  function renderEntry(senses: ReactNode, onAddSense?: () => void) {
    if (!review) {
      return null
    }
    if (workflow.draft.newEntry) {
      return (
        <EntryDraftForm reviewId={review.id} editable={!workflow.draft.readOnly}
          onReviewUpdated={reviewState.updateReview} onAddSense={onAddSense} footer={completionQuestion} defaultOpen={workflow.draft.defaultOpen}>
          {senses}
        </EntryDraftForm>
      )
    }
    if (review.matchedEntryId !== null) {
      return (
        <ExistingEntryDetails entryId={review.matchedEntryId} onAddSense={onAddSense} footer={completionQuestion} defaultOpen={workflow.draft.defaultOpen}>
          {senses}
        </ExistingEntryDetails>
      )
    }
    return null
  }

  return (
    <article className="details">
      {pendingCompletionStatus && (
        <ConfirmationDialog
          message={pendingCompletionStatus === 'NEEDS_EXPERT'
            ? 'Izskatīšana pāries statusā “Vajag ekspertu”. Saglabātais melnraksts paliks pieejams.'
            : 'Izskatīšana tiks pabeigta un atzīmēta kā “Gatavs”.'}
          busy={changingStatus}
          error={statusError}
          onConfirm={confirmReviewCompletion}
          onCancel={() => setPendingCompletionStatus(null)}
        />
      )}
      <div className="review-heading">
        <span className="muted">{suggestionStatusLabels[status]}</span>
        <h2 className="suggestion-title">{displayedTerm}</h2>
        {reviewInProgress && review && (
          <EditButton label="Labot izskatīšanas nosaukumu" onClick={openTitleEditor} />
        )}
      </div>
      {editingTitle && (
        <ConfirmationDialog
          title="Labot izskatīšanas nosaukumu"
          message="Mainīsies tikai izskatīšanas nosaukums."
          busy={savingTitle}
          error={titleError}
          onConfirm={() => handleTermCorrection()}
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

      <Fragment key={workflow.sectionResetKey}>
        {(suggestion || review) && (
          <SuggestionIntake status={status} onStatusChange={changeStatus}
            review={review} onDuplicate={onDuplicate}
            defaultOpen={workflow.intake.defaultOpen} readOnly={workflow.intake.readOnly} />
        )}

        {review && workflow.tezaurs.visible && (
          <TezaursCheckForm reviewId={review.id} status={review.tezaursStatus}
            matchedEntryId={review.matchedEntryId} caseClosed={status === 'ALREADY_EXISTS'}
            readOnly={workflow.tezaurs.readOnly} defaultOpen={workflow.tezaurs.defaultOpen}
            onSave={handleTezaursCheck} />
        )}

        {status === 'ALREADY_EXISTS' && completionQuestion}

        {review && workflow.usage.visible && (
          <UsageCheck review={review} onSaveDecision={handleUsageDecision}
            defaultOpen={workflow.usage.defaultOpen} readOnly={workflow.usage.readOnly} />
        )}

        {review && workflow.draft.visible && (
          <SenseDraftForm initialGloss={initialGloss} matchedEntryId={review.matchedEntryId}
            onReviewUpdated={reviewState.updateReview} reviewId={review.id}
            editExistingSenses={review.tezaursStatus === 'MEANING_NEEDS_EDITING'}
            editable={!workflow.draft.readOnly} renderEntry={renderEntry} />
        )}
      </Fragment>
    </article>
  )
}
