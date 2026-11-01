import type { Review, SuggestionStatus } from '../api/types'

export function getReviewWorkflow(review: Review | null, queueStatus: SuggestionStatus) {
  const status = review?.status ?? queueStatus
  const isInProgress = status === 'IN_PROGRESS'
  const onlyIntakeIsVisible = status === 'NEW' || status === 'READY_FOR_REVIEW' || status === 'GARBAGE'
  const isClosed = status === 'COMPLETED' || status === 'ALREADY_EXISTS'
  const entryIsMissing = review?.tezaursStatus === 'NOT_FOUND'
  const usageIsConfirmed = review?.usageDecision === 'USED'
  const needsSenseDraft = review?.tezaursStatus === 'MEANING_NOT_FOUND'
    || review?.tezaursStatus === 'MEANING_NEEDS_EDITING'

  const canEditTezaursCheck = isInProgress || status === 'ALREADY_EXISTS'
  const canEditUsageCheck = isInProgress || status === 'INVENTED' || status === 'INSUFFICIENT_DATA'
  const hasReview = review !== null
  const showReviewSections = hasReview && !onlyIntakeIsVisible
  const needsTezaursAnswer = review?.tezaursStatus === 'NOT_CHECKED' || review?.tezaursStatus === 'FOUND'
  const canShowNewEntryDraft = entryIsMissing && usageIsConfirmed

  let sectionResetKey = `source:${status}`
  if (review) {
    sectionResetKey = [review.id, status, review.tezaursStatus, review.matchedEntryId,
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
      readOnly: !canEditUsageCheck,
    },
    draft: {
      visible: showReviewSections && status !== 'ALREADY_EXISTS'
        && (needsSenseDraft || canShowNewEntryDraft),
      defaultOpen: isInProgress,
      readOnly: !isInProgress,
      newEntry: entryIsMissing,
    },
  }
}
