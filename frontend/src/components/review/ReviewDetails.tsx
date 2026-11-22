import { UsageCheck } from '../usage/UsageCheck'
import { Fragment, useState, type ReactNode } from 'react'
import {
  updateCaseTitle,
  detachSuggestion,
  saveReviewType,
  saveUsageDecision,
} from '../../api/client'
import type {
  Review,
  ReviewSource,
  ReviewStatus,
  SaveReviewType,
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
import { SenseResearchSection } from '../research/SenseResearchSection'

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
  const [examplesRevision, setExamplesRevision] = useState(0)
  const [draftRevision, setDraftRevision] = useState(0)
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

  async function updateTitle(title: string) {
    const updated = await updateCaseTitle(review.id, title)
    applyReviewUpdate(updated)
  }

  async function handleDetach(suggestionId: number) {
    const updated = await detachSuggestion(review.id, suggestionId)
    applyReviewUpdate(updated)
  }

  async function handleReviewTypeChange(selection: SaveReviewType) {
    const updatedReview = await saveReviewType(review.id, selection)
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
          complete={workflow.draft.complete}
          onReviewUpdated={applyReviewUpdate} onAddSense={onAddSense} defaultOpen={workflow.draft.defaultOpen}>
          {senses}
        </EntryDraftForm>
      )
    }
    if (review.matchedEntryId !== null) {
      return (
        <ExistingEntryReference entryId={review.matchedEntryId} onAddSense={onAddSense}
          defaultOpen={workflow.draft.defaultOpen}
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
        onTitleChange={updateTitle} />

      {sources.length > 0 && (
        <SuggestionSourcesTable sources={sources} onDetach={handleDetach} />
      )}

      <Fragment key={workflow.sectionResetKey}>
        <ReviewIntake status={status} onStatusChange={changeStatus}
          review={review} onDuplicate={onDuplicate}
          expanded={intakeOpen ?? workflow.intake.defaultOpen} onExpandedChange={setIntakeOpen}
          readOnly={workflow.intake.readOnly} />

        {workflow.tezaurs.visible && (
          <TezaursCheckForm reviewedTerm={review.reviewedTerm} reviewType={review.type} reviewStatus={status}
            matchedEntryId={review.matchedEntryId}
            readOnly={workflow.tezaurs.readOnly} defaultOpen={workflow.tezaurs.defaultOpen}
            onSave={handleReviewTypeChange} />
        )}

        {workflow.usage.visible && (
          <UsageCheck review={review} onSaveDecision={handleUsageDecision}
            onExamplesChange={() => setExamplesRevision((current) => current + 1)}
            defaultOpen={workflow.usage.defaultOpen} readOnly={workflow.usage.readOnly} />
        )}

        {workflow.research.visible && (
          <SenseResearchSection review={review} examplesRevision={examplesRevision}
            onSensesChanged={() => {
              setDraftRevision((current) => current + 1)
              setExamplesRevision((current) => current + 1)
            }} />
        )}

        {workflow.draft.visible && (
          <SenseDraftForm key={draftRevision} initialGloss={initialGloss} matchedEntryId={review.matchedEntryId}
            onReviewUpdated={(updatedReview) => {
              applyReviewUpdate(updatedReview)
              setExamplesRevision((current) => current + 1)
            }} reviewId={review.id}
            editable={!workflow.draft.readOnly} renderEntry={renderEntry} />
        )}
        {workflow.completion.visible && (
          <ReviewCompletion status={status} busy={changingAssignment}
            onStatusChange={changeStatus} />
        )}
      </Fragment>
    </article>
  )
}
