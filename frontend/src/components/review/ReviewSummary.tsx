import { useState, type ReactNode } from 'react'
import type { Review, TezaursEntryCandidate } from '../../api/types'
import { reviewTypeLabels, reviewStatusLabels, reviewStatusTransitions } from '../../review/statuses'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { EditButton } from '../shared/EditButton'
import { TezaursEntryLink } from '../tezaurs/TezaursEntryLink'

type Props = {
  review: Review
  matchedEntry: TezaursEntryCandidate | null
  username: string | null
  changingAssignment: boolean
  onStatusChange: (status: 'IN_PROGRESS' | 'READY_FOR_REVIEW') => Promise<Review>
  onTitleChange: (title: string) => Promise<void>
}

export function ReviewSummary({
  review, matchedEntry, username, changingAssignment, onStatusChange, onTitleChange,
}: Props) {
  const [assignmentError, setAssignmentError] = useState('')
  const [editingTitle, setEditingTitle] = useState(false)
  const [editedTitle, setEditedTitle] = useState('')
  const [savingTitle, setSavingTitle] = useState(false)
  const [titleError, setTitleError] = useState('')
  const status = review.status
  const reviewInProgress = status === 'IN_PROGRESS'
  const canTakeForReview = username !== null
    && (reviewStatusTransitions[status].includes('IN_PROGRESS')
      || (reviewInProgress && review.lastStatusChangedBy === null))

  const canGiveUpReview = reviewInProgress && username !== null && review.lastStatusChangedBy === username

  async function changeAssignment(nextStatus: 'IN_PROGRESS' | 'READY_FOR_REVIEW') {
    if (changingAssignment) {
      return
    }
    setAssignmentError('')
    try {
      await onStatusChange(nextStatus)
    } catch (cause) {
      setAssignmentError(cause instanceof Error ? cause.message : 'Neizdevās mainīt apstrādi.')
    }
  }

  async function handleTermCorrection() {
    if (savingTitle) {
      return
    }
    const correctedTitle = editedTitle.trim()
    if (!correctedTitle) {
      setTitleError('Ievadiet nosaukumu.')
      return
    }
    setSavingTitle(true)
    setTitleError('')
    try {
      await onTitleChange(correctedTitle)
      setEditingTitle(false)
    } catch (cause) {
      setTitleError(cause instanceof Error ? cause.message : 'Neizdevās labot nosaukumu.')
    } finally {
      setSavingTitle(false)
    }
  }

  function openTitleEditor() {
    setEditedTitle(review.reviewedTerm)
    setTitleError('')
    setEditingTitle(true)
  }

  let statusInfo: ReactNode = null
  if (status !== 'NEW') {
    if (status === 'READY_FOR_REVIEW' || (reviewInProgress && !review.lastStatusChangedBy)) {
      statusInfo = 'Perēkli neviens nav paņēmis apstrādē'
    } else if (reviewInProgress) {
      statusInfo = <>Perēkli paņēma apstrādē <strong>{review.lastStatusChangedBy}</strong></>
    } else if (review.lastStatusChangedBy) {
      statusInfo = <>Stāvokļa maiņu veica lietotājs <strong>{review.lastStatusChangedBy}</strong></>
    }
  }

  return (
    <aside className="review-summary" aria-label="Perēkļa informācija">
      <div className="review-summary-meta">
        <span className="muted">{reviewStatusLabels[status]}</span>
      </div>
      <div className="review-heading">
        <h2 className="suggestion-title">{review.reviewedTerm}</h2>
        {reviewInProgress && (
          <EditButton label="Labot perēkļa nosaukumu" onClick={openTitleEditor} />
        )}
      </div>
      {statusInfo && (
        <div className="review-assignment">
          <div>{statusInfo}</div>
          {canGiveUpReview ? (
            <button className="review-assignment-action" type="button" disabled={changingAssignment}
              title="Atteikties no apstrādes" aria-label="Atteikties no apstrādes"
              onClick={() => changeAssignment('READY_FOR_REVIEW')}>
              (Atteikties)
            </button>
          ) : canTakeForReview && (
            <button className="review-assignment-action" type="button" disabled={changingAssignment}
              title="Paņemt apstrādē" aria-label="Paņemt apstrādē"
              onClick={() => changeAssignment('IN_PROGRESS')}>
              (Paņemt)
            </button>
          )}
        </div>
      )}
      {assignmentError && <p className="error" role="alert">{assignmentError}</p>}
      {review.type !== 'UNKNOWN' && <div className="review-summary-type">
        {reviewTypeLabels[review.type]}
        {matchedEntry && matchedEntry.id === review.matchedEntryId && <div>
          {matchedEntry.heading} <TezaursEntryLink humanKey={matchedEntry.humanKey} />
        </div>}
      </div>}
      {editingTitle && (
        <ConfirmationDialog
          title="Labot perēkļa nosaukumu"
          message="Mainīsies tikai perēkļa nosaukums."
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
              required
            />
          </label>
        </ConfirmationDialog>
      )}
    </aside>
  )
}
