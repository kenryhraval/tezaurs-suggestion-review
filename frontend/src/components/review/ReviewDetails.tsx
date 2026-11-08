import { UsageCheck } from '../usage/UsageCheck'
import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react'
import {
  correctReviewTerm,
  detachSuggestion,
  getReview,
  saveTezaursCheck,
  saveUsageDecision,
} from '../../api/client'
import { reviewStatusLabels, reviewStatusTransitions } from '../../review/statuses'
import type {
  QueueItem,
  Review,
  ReviewSource,
  Suggestion,
  ReviewStatus,
  TezaursStatus,
  UsageDecision,
} from '../../api/types'
import { getReviewWorkflow } from '../../review/workflow'
import { TezaursCheckForm } from '../tezaurs/TezaursCheckForm'
import { ReviewIntake } from './ReviewIntake'
import { EntryDraftForm } from '../draft/EntryDraftForm'
import { ExistingEntryDetails } from '../tezaurs/ExistingEntryDetails'
import { SenseDraftForm } from '../draft/SenseDraftForm'
import { SuggestionSourcesTable } from './SuggestionSourcesTable'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { EditButton } from '../shared/EditButton'
import { ReviewCompletion } from './ReviewCompletion'

type Props = {
  item: QueueItem
  initialReview: Review | null
  username: string | null
  onStatusChange: (status: ReviewStatus) => Promise<Review | null>
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

function useSuggestionReview(reviewId: string | null, initialReview: Review | null) {
  const [review, setReview] = useState<Review | null>(initialReview)
  const [error, setError] = useState('')
  const reviewVersion = useRef(0)

  useEffect(() => {
    if (!reviewId || initialReview?.id === reviewId) {
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
  }, [reviewId, initialReview])

  function updateReview(saved: Review) {
    reviewVersion.current += 1
    setReview(saved)
    setError('')
  }

  return { review: review?.id === reviewId ? review : null, updateReview, error }
}

export function ReviewDetails({ item, initialReview, username, onStatusChange, onDuplicate, onReviewUpdated }: Props) {
  const [intakeOpen, setIntakeOpen] = useState<boolean | null>(null)
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
  const reviewState = useSuggestionReview(requestedReviewId, initialReview)
  const review = reviewState.review
  const reviewLoading = item.itemType === 'REVIEW' && !review && !reviewState.error
  const workflow = getReviewWorkflow(review, item.status)
  const status = workflow.status
  const reviewInProgress = status === 'IN_PROGRESS'
  const canTakeForReview = review !== null && username !== null
    && reviewStatusTransitions[status].includes('IN_PROGRESS')

  const canGiveUpReview = reviewInProgress && username !== null && review?.lastStatusChangedBy === username

  async function changeStatus(status: ReviewStatus) {
    const updatedReview = await onStatusChange(status)
    if (updatedReview) {
      setIntakeOpen(updatedReview.status === 'READY_FOR_REVIEW')
      reviewState.updateReview(updatedReview)
    }
    return updatedReview
  }

  async function changeAssignment(nextStatus: 'IN_PROGRESS' | 'READY_FOR_REVIEW') {
    if (changingStatus) {
      return
    }
    setChangingStatus(true)
    setStatusError('')
    try {
      await changeStatus(nextStatus)
    } catch (cause) {
      setStatusError(cause instanceof Error ? cause.message : 'Neizdevās mainīt apstrādi.')
    } finally {
      setChangingStatus(false)
    }
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
      setIntakeOpen(false)
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
    setIntakeOpen(false)
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
  let statusInfo: ReactNode = null
  if (review && status !== 'NEW') {
    if (status === 'READY_FOR_REVIEW') {
      statusInfo = 'Perēkli neviens nav paņēmis apstrādē'
    } else if (reviewInProgress) {
      statusInfo = <>Perēkli paņēma apstrādē <strong>{review.lastStatusChangedBy}</strong></>
    } else {
      statusInfo = <>Stāvokļa maiņu veica lietotājs <strong>{review.lastStatusChangedBy}</strong></>
    }
  }
  function renderEntry(senses: ReactNode, onAddSense?: () => void) {
    if (!review) {
      return null
    }
    if (workflow.draft.newEntry) {
      return (
        <EntryDraftForm reviewId={review.id} editable={!workflow.draft.readOnly}
          canCollapse={workflow.draft.readOnly || review.canComplete} complete={workflow.draft.complete}
          onReviewUpdated={reviewState.updateReview} onAddSense={onAddSense} defaultOpen={workflow.draft.defaultOpen}>
          {senses}
        </EntryDraftForm>
      )
    }
    if (review.matchedEntryId !== null) {
      return (
        <ExistingEntryDetails entryId={review.matchedEntryId} onAddSense={onAddSense} defaultOpen={workflow.draft.defaultOpen}
          canCollapse={workflow.draft.readOnly || review.canComplete} complete={workflow.draft.complete}>
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
        <span className="muted">{reviewStatusLabels[status]}</span>
        <h2 className="suggestion-title">{displayedTerm}</h2>
        {reviewInProgress && review && (
          <EditButton label="Labot izskatīšanas nosaukumu" onClick={openTitleEditor} />
        )}
      </div>
      {review && status !== 'NEW' && (
        <div className="review-assignment">
          {canGiveUpReview ? (
            <button className="icon-button text-danger" type="button" disabled={changingStatus}
              title="Atteikties no apstrādes" aria-label="Atteikties no apstrādes"
              onClick={() => changeAssignment('READY_FOR_REVIEW')}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <rect x="5" y="4" width="5" height="16" />
                <rect x="14" y="4" width="5" height="16" />
              </svg>
            </button>
          ) : canTakeForReview && (
            <button className="icon-button text-success" type="button" disabled={changingStatus}
              title="Paņemt apstrādē" aria-label="Paņemt apstrādē"
              onClick={() => changeAssignment('IN_PROGRESS')}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M6 3v18l15-9Z" />
              </svg>
            </button>
          )}
          <span>{statusInfo}</span>
        </div>
      )}
      {statusError && !pendingCompletionStatus && <p className="error" role="alert">{statusError}</p>}
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
            <input className="form-control form-control-sm"
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
          <ReviewIntake status={status} onStatusChange={changeStatus}
            review={review} onDuplicate={onDuplicate}
            expanded={intakeOpen ?? workflow.intake.defaultOpen} onExpandedChange={setIntakeOpen}
            readOnly={workflow.intake.readOnly} />
        )}

        {review && workflow.tezaurs.visible && (
          <TezaursCheckForm reviewId={review.id} reviewedTerm={review.reviewedTerm} status={review.tezaursStatus}
            matchedEntryId={review.matchedEntryId} caseClosed={status === 'ALREADY_EXISTS'}
            readOnly={workflow.tezaurs.readOnly} defaultOpen={workflow.tezaurs.defaultOpen}
            onSave={handleTezaursCheck} />
        )}

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
        {review && workflow.completion.visible && (
          <ReviewCompletion status={status} canComplete={review.canComplete} busy={changingStatus}
            onComplete={requestReviewCompletion} />
        )}
      </Fragment>
    </article>
  )
}
