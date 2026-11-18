import { UsageCheck } from '../usage/UsageCheck'
import { Fragment, useState, type ReactNode } from 'react'
import {
  correctReviewTerm,
  detachSuggestion,
  saveCaseType,
  saveUsageDecision,
} from '../../api/client'
import type {
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
  initialReview: Review
  username: string | null
  onStatusChange: (status: ReviewStatus) => Promise<Review>
  onDuplicate: (sourceCaseId: string, targetCaseId: string) => Promise<void>
  onReviewUpdated: (review: Review) => void
}

export function ReviewDetails({ initialReview, username, onStatusChange, onDuplicate, onReviewUpdated }: Props) {
  const [intakeOpen, setIntakeOpen] = useState<boolean | null>(null)
  const [changingAssignment, setChangingAssignment] = useState(false)
  const [review, setReview] = useState(initialReview)
  const workflow = getReviewWorkflow(review)
  const status = workflow.status

  function applyReviewUpdate(updatedReview: Review) {
    setReview(updatedReview)
    onReviewUpdated(updatedReview)
  }

  async function changeStatus(status: ReviewStatus) {
    const updatedReview = await onStatusChange(status)
    setIntakeOpen(updatedReview.status === 'READY_FOR_REVIEW')
    setReview(updatedReview)
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
    const updated = await correctReviewTerm(review.id, title)
    applyReviewUpdate(updated)
  }

  async function handleDetach(sourceId: number) {
    const updated = await detachSuggestion(review.id, sourceId)
    applyReviewUpdate(updated)
  }

  async function handleCaseTypeChange(selection: SaveCaseType) {
    const updatedReview = await saveCaseType(review.id, selection)
    applyReviewUpdate(updatedReview)
  }

  async function handleUsageDecision(decision: UsageDecision) {
    const updatedReview = await saveUsageDecision(review.id, decision)
    applyReviewUpdate(updatedReview)
  }

  const sources: ReviewSource[] = review.sources
  const initialGloss = sources.length === 1 ? sources[0].submittedDefinition : ''
  function renderEntry(senses: ReactNode, onAddSense?: () => void) {
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
      <ReviewHeader review={review} username={username}
        changingAssignment={changingAssignment} onStatusChange={changeAssignment}
        onTitleChange={correctTitle} />

      {sources.length > 0 && (
        <SuggestionSourcesTable sources={sources} onDetach={handleDetach} />
      )}

      <Fragment key={workflow.sectionResetKey}>
        <ReviewIntake status={status} onStatusChange={changeStatus}
          review={review} onDuplicate={onDuplicate}
          expanded={intakeOpen ?? workflow.intake.defaultOpen} onExpandedChange={setIntakeOpen}
          readOnly={workflow.intake.readOnly} />

        {workflow.tezaurs.visible && (
          <TezaursCheckForm reviewId={review.id} reviewedTerm={review.reviewedTerm} caseType={review.type} reviewStatus={status}
            matchedEntryId={review.matchedEntryId}
            readOnly={workflow.tezaurs.readOnly} defaultOpen={workflow.tezaurs.defaultOpen}
            onSave={handleCaseTypeChange} />
        )}

        {workflow.usage.visible && (
          <UsageCheck review={review} onSaveDecision={handleUsageDecision}
            defaultOpen={workflow.usage.defaultOpen} readOnly={workflow.usage.readOnly} />
        )}

        {workflow.draft.visible && (
          <SenseDraftForm initialGloss={initialGloss} matchedEntryId={review.matchedEntryId}
            onReviewUpdated={applyReviewUpdate} reviewId={review.id}
            editable={!workflow.draft.readOnly} renderEntry={renderEntry} />
        )}
        {workflow.completion.visible && (
          <ReviewCompletion status={status} canComplete={review.canComplete} busy={changingAssignment}
            onStatusChange={changeStatus} />
        )}
      </Fragment>
    </article>
  )
}
