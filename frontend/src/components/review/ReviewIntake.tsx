import { useState } from 'react'
import type { Review, ReviewStatus } from '../../api/types'
import { reviewStatusLabels, reviewStatusTransitions } from '../../review/statuses'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { DuplicateReviewCombobox } from './DuplicateReviewCombobox'

const intakeChoices: ReviewStatus[] = ['GARBAGE', 'READY_FOR_REVIEW']

type Props = {
  status: ReviewStatus
  expanded: boolean
  onExpandedChange: (expanded: boolean) => void
  readOnly: boolean
  complete: boolean
  onStatusChange: (status: ReviewStatus) => Promise<Review>
  review: Review | null
  onDuplicate: (sourceReviewId: string, targetReviewId: string) => Promise<void>
}

export function ReviewIntake({ status, onStatusChange, review, onDuplicate,
  expanded: expandedByUser, onExpandedChange, readOnly, complete }: Props) {
  const [confirmingTrash, setConfirmingTrash] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  let selectedStatus: ReviewStatus | null = null
  if (status === 'GARBAGE') {
    selectedStatus = 'GARBAGE'
  } else if (status !== 'NEW') {
    selectedStatus = 'READY_FOR_REVIEW'
  }
  const canCollapse = readOnly || complete || status === 'GARBAGE'
  const expanded = expandedByUser || !canCollapse

  function choose(nextStatus: ReviewStatus) {
    if (nextStatus === selectedStatus) {
      return
    }
    setError('')
    if (nextStatus === 'GARBAGE') {
      setConfirmingTrash(true)
    } else {
      saveStatus(nextStatus)
    }
  }

  async function saveStatus(nextStatus: ReviewStatus) {
    if (saving) {
      return
    }
    setSaving(true)
    try {
      await onStatusChange(nextStatus)
      setConfirmingTrash(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt stāvokli.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className={`initial-review review-section${expanded ? ' is-open' : ''}`}>
      <details open={expanded} onToggle={(event) => onExpandedChange(event.currentTarget.open)}>
        <summary className={(complete || status === 'GARBAGE') && !expanded ? 'section-title confirmed' : 'section-title'}
          aria-disabled={!canCollapse} onClick={(event) => {
            if (!canCollapse) event.preventDefault()
          }}>
          1. Ieteikuma pirmapstrāde
        </summary>
        <p className="muted">Vai ieteikums ir skatāms vai atkritums?</p>
        <fieldset className="review-controls" disabled={readOnly}>
          <div className="tezaurs-answer-actions">
            {intakeChoices.map((value) => (
              <button className="btn btn-sm btn-outline-secondary" key={value} type="button" aria-pressed={selectedStatus === value}
                disabled={saving || (value !== selectedStatus && !reviewStatusTransitions[status].includes(value))}
                onClick={() => choose(value)}>
                {reviewStatusLabels[value]}
              </button>
            ))}
          </div>
        </fieldset>
        {selectedStatus === 'READY_FOR_REVIEW' && review && (
          <div className="meaning-existence-check">
            <p><strong>Vai ieteikums attiecas uz esošu perēkli, vai jāsāk jauns?</strong></p>
            <DuplicateReviewCombobox
              currentReview={review}
              onSelect={(targetId) => onDuplicate(review.id, targetId)}
            />
          </div>
        )}
      </details>
      {error && !confirmingTrash && <p className="error" role="alert">{error}</p>}
      {confirmingTrash && (
        <ConfirmationDialog
          message="Ieteikums tiks atzīmēts kā “Atkritums”"
          busy={saving} error={error}
          onConfirm={() => saveStatus('GARBAGE')}
          onCancel={() => setConfirmingTrash(false)}
        />
      )}
    </section>
  )
}
