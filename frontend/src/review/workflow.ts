import type { Review } from '../api/types'

export function getReviewWorkflow(review: Review) {
  const status = review.status
  const editable = status === 'IN_PROGRESS'
  const hasKnownType = review.type !== 'UNKNOWN'
  const entryIsMissing = review.type === 'ENTRY'
  const usageIsConfirmed = review.usageDecision === 'USED'
  const intakeOnly = status === 'NEW' || status === 'GARBAGE'
    || (status === 'READY_FOR_REVIEW' && !hasKnownType)
  const showWork = !intakeOnly
  const readyForDrafting = review.type === 'SENSE' || (entryIsMissing && usageIsConfirmed)
  const showDraftSections = showWork && (readyForDrafting || (!editable && hasKnownType))

  const sectionResetKey = [review.id, status, review.type, review.matchedEntryId,
    review.usageDecision].join(':')

  return {
    status,
    editable,
    sectionResetKey,
    intake: {
      defaultOpen: status === 'NEW' || (status === 'READY_FOR_REVIEW' && !hasKnownType),
      readOnly: status === 'COMPLETED' || status === 'ALREADY_EXISTS',
      complete: hasKnownType || (status !== 'NEW' && status !== 'READY_FOR_REVIEW' && status !== 'GARBAGE'),
    },
    tezaurs: {
      visible: showWork,
      defaultOpen: editable && !hasKnownType,
    },
    usage: {
      visible: showWork && hasKnownType,
      defaultOpen: editable && ((entryIsMissing && !usageIsConfirmed) || review.type === 'SENSE'),
    },
    research: {
      visible: showDraftSections,
      defaultOpen: editable,
    },
    draft: {
      visible: showDraftSections,
      defaultOpen: editable,
      newEntry: entryIsMissing,
      complete: status === 'NEEDS_EXPERT' || status === 'COMPLETED',
    },
    completion: {
      visible: editable && readyForDrafting,
    },
  }
}
