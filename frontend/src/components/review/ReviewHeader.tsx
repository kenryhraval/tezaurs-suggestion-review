import { useState, type ReactNode } from 'react'
import type { Review } from '../../api/types'
import { caseTypeLabels, reviewStatusLabels, reviewStatusTransitions } from '../../review/statuses'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { EditButton } from '../shared/EditButton'

type Props = {
  review: Review
  username: string | null
  changingAssignment: boolean
  onStatusChange: (status: 'IN_PROGRESS' | 'READY_FOR_REVIEW') => Promise<Review>
  onTitleChange: (title: string) => Promise<void>
}

export function ReviewHeader({
  review, username, changingAssignment, onStatusChange, onTitleChange,
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
    <>
      <div className="review-heading">
        <span className="muted">{reviewStatusLabels[status]}</span>
        {review.type !== 'UNKNOWN' && (
          <span className="muted">[{caseTypeLabels[review.type]}]</span>
        )}
        <h2 className="suggestion-title">{review.reviewedTerm}</h2>
        {reviewInProgress && (
          <EditButton label="Labot izskatīšanas nosaukumu" onClick={openTitleEditor} />
        )}
      </div>
      {statusInfo && (
        <div className="review-assignment">
          <span>{statusInfo}</span>{' '}
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
              required
            />
          </label>
        </ConfirmationDialog>
      )}
    </>
  )
}
