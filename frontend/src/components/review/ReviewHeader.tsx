import { useState, type ReactNode } from 'react'
import type { Review, ReviewStatus } from '../../api/types'
import { reviewStatusLabels, reviewStatusTransitions } from '../../review/statuses'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { EditButton } from '../shared/EditButton'

type Props = {
  title: string
  status: ReviewStatus
  review: Review | null
  username: string | null
  changingAssignment: boolean
  onStatusChange: (status: 'IN_PROGRESS' | 'READY_FOR_REVIEW') => Promise<Review>
  onTitleChange: (title: string) => Promise<void>
}

export function ReviewHeader({
  title, status, review, username, changingAssignment, onStatusChange, onTitleChange,
}: Props) {
  const [assignmentError, setAssignmentError] = useState('')
  const [editingTitle, setEditingTitle] = useState(false)
  const [editedTitle, setEditedTitle] = useState('')
  const [savingTitle, setSavingTitle] = useState(false)
  const [titleError, setTitleError] = useState('')
  const reviewInProgress = status === 'IN_PROGRESS'
  const canTakeForReview = review !== null && username !== null
    && (reviewStatusTransitions[status].includes('IN_PROGRESS')
      || (reviewInProgress && review.lastStatusChangedBy === null))

  const canGiveUpReview = reviewInProgress && username !== null && review?.lastStatusChangedBy === username

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
    setEditedTitle(title)
    setTitleError('')
    setEditingTitle(true)
  }

  let statusInfo: ReactNode = null
  if (review && status !== 'NEW') {
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
        <h2 className="suggestion-title">{title}</h2>
        {reviewInProgress && review && (
          <EditButton label="Labot izskatīšanas nosaukumu" onClick={openTitleEditor} />
        )}
      </div>
      {statusInfo && (
        <div className="review-assignment">
          {canGiveUpReview ? (
            <button className="icon-button text-danger" type="button" disabled={changingAssignment}
              title="Atteikties no apstrādes" aria-label="Atteikties no apstrādes"
              onClick={() => changeAssignment('READY_FOR_REVIEW')}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <rect x="5" y="4" width="5" height="16" />
                <rect x="14" y="4" width="5" height="16" />
              </svg>
            </button>
          ) : canTakeForReview && (
            <button className="icon-button text-success" type="button" disabled={changingAssignment}
              title="Paņemt apstrādē" aria-label="Paņemt apstrādē"
              onClick={() => changeAssignment('IN_PROGRESS')}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M6 3v18l15-9Z" />
              </svg>
            </button>
          )}
          <span>{statusInfo}</span>
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
