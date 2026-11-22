import type { Review } from '../api/types'

export function getReviewWorkflow(review: Review) {
  const status = review.status
  const isInProgress = status === 'IN_PROGRESS'
  const onlyIntakeIsVisible = status === 'NEW' || status === 'READY_FOR_REVIEW' || status === 'GARBAGE'
  const isClosed = status === 'COMPLETED' || status === 'ALREADY_EXISTS'
  const entryIsMissing = review.type === 'ENTRY'
  const usageIsConfirmed = review.usageDecision === 'USED'
  const needsSenseDraft = review.type === 'SENSE'

  const showReviewSections = !onlyIntakeIsVisible
  const needsTezaursAnswer = review.type === 'UNKNOWN'
  const canShowNewEntryDraft = entryIsMissing && usageIsConfirmed
  const showDraft = showReviewSections && status !== 'ALREADY_EXISTS'
    && (needsSenseDraft || canShowNewEntryDraft)

  const sectionResetKey = [review.id, status, review.type, review.matchedEntryId,
    review.usageDecision].join(':')

  return {
    status,
    sectionResetKey,
    intake: {
      defaultOpen: status === 'NEW' || status === 'READY_FOR_REVIEW',
      readOnly: isClosed,
    },
    tezaurs: {
      visible: showReviewSections,
      defaultOpen: isInProgress && needsTezaursAnswer,
      readOnly: !isInProgress,
    },
    usage: {
      visible: showReviewSections && (entryIsMissing || needsSenseDraft) && status !== 'ALREADY_EXISTS',
      defaultOpen: isInProgress && ((entryIsMissing && !usageIsConfirmed) || needsSenseDraft),
      readOnly: !isInProgress,
    },
    draft: {
      visible: showDraft,
      defaultOpen: isInProgress,
      readOnly: !isInProgress,
      newEntry: entryIsMissing,
      complete: status === 'NEEDS_EXPERT' || status === 'COMPLETED',
    },
    research: {
      visible: isInProgress && review.type !== 'UNKNOWN',
    },
    completion: {
      visible: status !== 'NEEDS_EXPERT' && showDraft,
    },
  }
}
