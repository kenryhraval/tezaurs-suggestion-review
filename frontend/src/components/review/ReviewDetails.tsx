import { UsageCheck } from '../usage/UsageCheck'
import { Fragment, useState } from 'react'
import {
  updateReviewTitle,
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
import { useReviewData } from '../../review/useReviewData'
import { TezaursCheckForm } from '../tezaurs/TezaursCheckForm'
import { ReviewIntake } from './ReviewIntake'
import { EntryDraftForm } from '../draft/EntryDraftForm'
import { ExistingEntryReference } from '../tezaurs/ExistingEntryReference'
import { SenseDraftForm } from '../draft/SenseDraftForm'
import { SuggestionSourcesTable } from './SuggestionSourcesTable'
import { ReviewCompletion } from './ReviewCompletion'
import { ReviewSummary } from './ReviewSummary'
import { SenseResearchSection } from '../research/SenseResearchSection'

type Props = {
  initialReview: Review
  username: string | null
  onStatusChange: (status: ReviewStatus) => Promise<Review>
  onDuplicate: (sourceReviewId: string, targetReviewId: string) => Promise<void>
  onReviewUpdated: (review: Review) => void
}

export function ReviewDetails({ initialReview, username, onStatusChange, onDuplicate, onReviewUpdated }: Props) {
  const [intakeOpen, setIntakeOpen] = useState<boolean | null>(null)
  const [changingAssignment, setChangingAssignment] = useState(false)
  const [review, setReview] = useState(initialReview)
  const data = useReviewData(review)
  const workflow = getReviewWorkflow(review)
  const status = workflow.status

  function applyReviewUpdate(updatedReview: Review) {
    if (updatedReview.status !== review.status) setIntakeOpen(null)
    setReview(updatedReview)
    onReviewUpdated(updatedReview)
  }

  async function changeStatus(status: ReviewStatus) {
    const updatedReview = await onStatusChange(status)
    setIntakeOpen(null)
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
    const updated = await updateReviewTitle(review.id, title)
    applyReviewUpdate(updated)
  }

  async function handleDetach(suggestionId: number) {
    const updated = await detachSuggestion(review.id, suggestionId)
    applyReviewUpdate(updated)
  }

  async function handleReviewTypeChange(selection: SaveReviewType) {
    const updated = await saveReviewType(review.id, selection)
    applyReviewUpdate(updated)
  }

  async function handleUsageDecision(decision: UsageDecision) {
    const updated = await saveUsageDecision(review.id, decision)
    applyReviewUpdate(updated)
  }

  const sources: ReviewSource[] = review.sources

  return (
    <article className="details review-workspace">
      <ReviewSummary review={review} username={username} matchedEntry={data.matchedEntry}
        changingAssignment={changingAssignment} onStatusChange={changeAssignment}
        onTitleChange={updateTitle} />

      <div className="review-content">
        {sources.length > 0 && (
          <SuggestionSourcesTable sources={sources} onDetach={handleDetach} />
        )}

        {(data.error || data.optionsError) && <p className="error" role="alert">{data.error || data.optionsError}</p>}
        <Fragment key={workflow.sectionResetKey}>
          <ReviewIntake status={status} onStatusChange={changeStatus}
            review={review} onDuplicate={onDuplicate}
            expanded={intakeOpen ?? workflow.intake.defaultOpen} onExpandedChange={setIntakeOpen}
            readOnly={workflow.intake.readOnly} complete={workflow.intake.complete} />

          {workflow.tezaurs.visible && (
            <TezaursCheckForm reviewedTerm={review.reviewedTerm} reviewType={review.type} reviewStatus={status}
              matchedEntryId={review.matchedEntryId} matchedEntry={data.matchedEntry} entryLoading={data.loading}
              readOnly={!workflow.editable} defaultOpen={workflow.tezaurs.defaultOpen}
              onSave={handleReviewTypeChange} />
          )}

          {workflow.usage.visible && (
            <UsageCheck review={review} examples={data.examples} loading={data.loading}
              onSaveDecision={handleUsageDecision} onExampleSaved={data.saveExample}
              onExampleRemoved={data.removeExample} onExamplesReplaced={data.replaceExamples}
              defaultOpen={workflow.usage.defaultOpen} readOnly={!workflow.editable} />
          )}

          {workflow.research.visible && (
            <SenseResearchSection review={review} senses={data.senses} examples={data.examples}
              loading={data.loading} readError={data.error} editable={workflow.editable} defaultOpen={workflow.research.defaultOpen}
              onSenseSaved={data.saveSense} onSenseRemoved={data.removeSense}
              onSensesReplaced={data.replaceSenses} onExampleSaved={data.saveExample} />
          )}

          {workflow.draft.visible && workflow.draft.newEntry && (
            <EntryDraftForm reviewId={review.id} editable={workflow.editable}
              complete={workflow.draft.complete} defaultOpen={workflow.draft.defaultOpen}
              options={data.formOptions}>
              <SenseDraftForm reviewId={review.id} senses={data.senses} loading={data.loading}
                options={data.formOptions} editable={workflow.editable} onSenseSaved={data.saveSense} />
            </EntryDraftForm>
          )}
          {workflow.draft.visible && !workflow.draft.newEntry && review.matchedEntryId !== null && (
            <ExistingEntryReference entry={data.matchedEntry} loading={data.loading}
              defaultOpen={workflow.draft.defaultOpen} complete={workflow.draft.complete}>
              <SenseDraftForm reviewId={review.id} senses={data.senses} loading={data.loading}
                options={data.formOptions} editable={workflow.editable} onSenseSaved={data.saveSense} />
            </ExistingEntryReference>
          )}
          {workflow.completion.visible && (
            <ReviewCompletion busy={changingAssignment}
              onStatusChange={changeStatus} />
          )}
        </Fragment>
      </div>
    </article>
  )
}
