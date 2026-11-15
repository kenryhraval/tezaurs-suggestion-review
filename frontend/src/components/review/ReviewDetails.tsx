import { UsageCheck } from '../usage/UsageCheck'
import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react'
import {
  correctReviewTerm,
  detachSuggestion,
  getReview,
  saveCaseType,
  saveUsageDecision,
} from '../../api/client'
import type {
  ReviewListItem,
  Review,
  ReviewSource,
  ReviewStatus,
  SaveCaseType,
  UsageDecision,
} from '../../api/types'
import { getReviewWorkflow } from '../../review/workflow'
import { TezaursCheckForm } from '../tezaurs/TezaursCheckForm'
import { ReviewIntake } from './ReviewIntake'
import { EntryDraftForm } from '../draft/EntryDraftForm'
import { ExistingEntryReference } from '../tezaurs/ExistingEntryReference'
import { SenseDraftForm } from '../draft/SenseDraftForm'
import { SuggestionSourcesTable } from './SuggestionSourcesTable'
import { ReviewCompletion } from './ReviewCompletion'
import { ReviewHeader } from './ReviewHeader'

type Props = {
  item: ReviewListItem
  initialReview: Review | null
  username: string | null
  onStatusChange: (status: ReviewStatus) => Promise<Review>
  onDuplicate: (sourceCaseId: string, targetCaseId: string) => Promise<void>
  onReviewUpdated: (review: Review) => void
}

function useReview(reviewId: string, initialReview: Review | null) {
  const [review, setReview] = useState<Review | null>(initialReview)
  const [error, setError] = useState('')
  const reviewUpdateVersion = useRef(0)

  useEffect(() => {
    if (initialReview?.id === reviewId) {
      return
    }
    const id = reviewId

    let requestIsOutdated = false
    reviewUpdateVersion.current += 1
    const versionBeforeLoading = reviewUpdateVersion.current

    async function loadReview() {
      try {
        const loaded = await getReview(id)
        if (!requestIsOutdated && versionBeforeLoading === reviewUpdateVersion.current) {
          setReview(loaded)
          setError('')
        }
      } catch (cause) {
        if (!requestIsOutdated && versionBeforeLoading === reviewUpdateVersion.current) {
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
    reviewUpdateVersion.current += 1
    setReview(saved)
    setError('')
  }

  return { review: review?.id === reviewId ? review : null, updateReview, error }
}

export function ReviewDetails({ item, initialReview, username, onStatusChange, onDuplicate, onReviewUpdated }: Props) {
  const [intakeOpen, setIntakeOpen] = useState<boolean | null>(null)
  const [changingAssignment, setChangingAssignment] = useState(false)
  const reviewState = useReview(item.id, initialReview)
  const review = reviewState.review
  const reviewLoading = !review && !reviewState.error
  const workflow = getReviewWorkflow(review, item.status)
  const status = workflow.status

  function applyReviewUpdate(updatedReview: Review) {
    reviewState.updateReview(updatedReview)
    onReviewUpdated(updatedReview)
  }

  async function changeStatus(status: ReviewStatus) {
    const updatedReview = await onStatusChange(status)
    setIntakeOpen(updatedReview.status === 'READY_FOR_REVIEW')
    reviewState.updateReview(updatedReview)
    return updatedReview
  }

  async function changeAssignment(nextStatus: 'IN_PROGRESS' | 'READY_FOR_REVIEW') {
    setChangingAssignment(true)
    try {
      return await changeStatus(nextStatus)
    } finally {
      setChangingAssignment(false)
    }
  }

  async function correctTitle(title: string) {
    if (!review) {
      throw new Error('Izskatīšana nav ielādēta.')
    }
    const updated = await correctReviewTerm(review.id, title)
    applyReviewUpdate(updated)
  }

  async function handleDetach(sourceId: number) {
    if (!review) {
      throw new Error('Izskatīšana nav ielādēta.')
    }
    const updated = await detachSuggestion(review.id, sourceId)
    applyReviewUpdate(updated)
  }

  async function handleCaseTypeChange(selection: SaveCaseType) {
    if (!review) {
      throw new Error('Izskatīšana nav ielādēta.')
    }
    const updatedReview = await saveCaseType(review.id, selection)
    applyReviewUpdate(updatedReview)
  }

  async function handleUsageDecision(decision: UsageDecision) {
    if (!review) {
      throw new Error('Izskatīšana nav ielādēta.')
    }
    const updatedReview = await saveUsageDecision(review.id, decision)
    applyReviewUpdate(updatedReview)
  }

  const displayedTerm = review?.reviewedTerm ?? item.reviewedTerm
  const sources: ReviewSource[] = review?.sources ?? []
  const initialGloss = sources.length === 1 ? sources[0].submittedDefinition : ''
  function renderEntry(senses: ReactNode, onAddSense?: () => void) {
    if (!review) {
      return null
    }
    if (workflow.draft.newEntry) {
      return (
        <EntryDraftForm reviewId={review.id} editable={!workflow.draft.readOnly}
          canCollapse={workflow.draft.readOnly || review.canComplete} complete={workflow.draft.complete}
          onReviewUpdated={applyReviewUpdate} onAddSense={onAddSense} defaultOpen={workflow.draft.defaultOpen}>
          {senses}
        </EntryDraftForm>
      )
    }
    if (review.matchedEntryId !== null) {
      return (
        <ExistingEntryReference entryId={review.matchedEntryId} onAddSense={onAddSense}
          defaultOpen={workflow.draft.defaultOpen} canCollapse={workflow.draft.readOnly || review.canComplete}
          complete={workflow.draft.complete}>
          {senses}
        </ExistingEntryReference>
      )
    }
    return null
  }

  return (
    <article className="details">
      <ReviewHeader title={displayedTerm} status={status} review={review} username={username}
        changingAssignment={changingAssignment} onStatusChange={changeAssignment}
        onTitleChange={correctTitle} />

      {sources.length > 0 && (
        <SuggestionSourcesTable sources={sources} onDetach={review ? handleDetach : undefined} />
      )}

      {reviewState.error && <p className="error" role="alert">{reviewState.error}</p>}
      {reviewLoading && <p className="muted">Ielādē izskatīšanas informāciju...</p>}

      <Fragment key={workflow.sectionResetKey}>
        {review && (
          <ReviewIntake status={status} onStatusChange={changeStatus}
            review={review} onDuplicate={onDuplicate}
            expanded={intakeOpen ?? workflow.intake.defaultOpen} onExpandedChange={setIntakeOpen}
            readOnly={workflow.intake.readOnly} />
        )}

        {review && workflow.tezaurs.visible && (
          <TezaursCheckForm reviewId={review.id} reviewedTerm={review.reviewedTerm} caseType={review.type} reviewStatus={status}
            matchedEntryId={review.matchedEntryId}
            readOnly={workflow.tezaurs.readOnly} defaultOpen={workflow.tezaurs.defaultOpen}
            onSave={handleCaseTypeChange} />
        )}

        {review && workflow.usage.visible && (
          <UsageCheck review={review} onSaveDecision={handleUsageDecision}
            defaultOpen={workflow.usage.defaultOpen} readOnly={workflow.usage.readOnly} />
        )}

        {review && workflow.draft.visible && (
          <SenseDraftForm initialGloss={initialGloss} matchedEntryId={review.matchedEntryId}
            onReviewUpdated={applyReviewUpdate} reviewId={review.id}
            editable={!workflow.draft.readOnly} renderEntry={renderEntry} />
        )}
        {review && workflow.completion.visible && (
          <ReviewCompletion status={status} canComplete={review.canComplete} busy={changingAssignment}
            onStatusChange={changeStatus} />
        )}
      </Fragment>
    </article>
  )
}
