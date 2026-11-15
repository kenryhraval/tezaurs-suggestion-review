import type { Review, ReviewStatus } from '../api/types'

export function getReviewWorkflow(review: Review | null, queueStatus: ReviewStatus) {
  const status = review?.status ?? queueStatus
  const isInProgress = status === 'IN_PROGRESS'
  const onlyIntakeIsVisible = status === 'NEW' || status === 'READY_FOR_REVIEW' || status === 'GARBAGE'
  const isClosed = status === 'COMPLETED' || status === 'ALREADY_EXISTS'
  const entryIsMissing = review?.type === 'ENTRY'
  const usageIsConfirmed = review?.usageDecision === 'USED'
  const needsSenseDraft = review?.type === 'SENSE'

  const canEditTezaursCheck = isInProgress || status === 'ALREADY_EXISTS'
  const hasReview = review !== null
  const showReviewSections = hasReview && !onlyIntakeIsVisible
  const needsTezaursAnswer = review?.type === 'UNKNOWN'
  const canShowNewEntryDraft = entryIsMissing && usageIsConfirmed
  const showDraft = showReviewSections && status !== 'ALREADY_EXISTS'
    && (needsSenseDraft || canShowNewEntryDraft)

  let sectionResetKey = `source:${status}`
  if (review) {
    sectionResetKey = [review.id, status, review.type, review.matchedEntryId,
      review.usageDecision].join(':')
  }

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
      readOnly: !canEditTezaursCheck,
    },
    usage: {
      visible: showReviewSections && entryIsMissing,
      defaultOpen: isInProgress && entryIsMissing && !usageIsConfirmed,
      readOnly: !isInProgress,
    },
    draft: {
      visible: showDraft,
      defaultOpen: isInProgress,
      readOnly: !isInProgress,
      newEntry: entryIsMissing,
      complete: review?.canComplete === true || status === 'NEEDS_EXPERT' || status === 'COMPLETED',
    },
    completion: {
      visible: status !== 'NEEDS_EXPERT' && showDraft,
    },
  }
}
