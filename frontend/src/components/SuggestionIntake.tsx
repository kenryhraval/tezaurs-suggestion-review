import { useState } from 'react'
import type { Review, ReviewStatus } from '../api/types'
import { reviewStatusLabels, reviewStatusTransitions } from '../review/statuses'
import { ConfirmationDialog } from './ConfirmationDialog'
import { DuplicateReviewCombobox } from './DuplicateReviewCombobox'

const intakeChoices: ReviewStatus[] = ['GARBAGE', 'READY_FOR_REVIEW']

type Props = {
  status: ReviewStatus
  expanded: boolean
  onExpandedChange: (expanded: boolean) => void
  readOnly: boolean
  onStatusChange: (status: ReviewStatus) => Promise<Review | null>
  review: Review | null
  onDuplicate: (sourceId: number, caseId: string) => Promise<void>
}

export function SuggestionIntake({ status, onStatusChange, review, onDuplicate,
  expanded, onExpandedChange, readOnly }: Props) {
  const [confirmingTrash, setConfirmingTrash] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  let selectedStatus: ReviewStatus | null = null
  if (status === 'GARBAGE') {
    selectedStatus = 'GARBAGE'
  } else if (status !== 'NEW') {
    selectedStatus = 'READY_FOR_REVIEW'
  }
  const caseSelected = review !== null && status !== 'GARBAGE' && status !== 'READY_FOR_REVIEW'

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
      const updatedReview = await onStatusChange(nextStatus)
      if (!updatedReview) {
        setError('Neizdevās mainīt statusu. Mēģiniet vēlreiz.')
        return
      }
      setConfirmingTrash(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt statusu.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="initial-review">
      <details open={expanded} onToggle={(event) => onExpandedChange(event.currentTarget.open)}>
        <summary className={(caseSelected || status === 'GARBAGE') && !expanded ? 'confirmed' : undefined}>
          <strong>1. Ieteikuma pirmapstrāde</strong>
        </summary>
        <p className="muted">Vai ieteikums ir skatāms vai atkritums?</p>
        <fieldset className="review-controls" disabled={readOnly}>
          <div className="tezaurs-answer-actions">
            {intakeChoices.map((value) => (
              <button key={value} type="button" aria-pressed={selectedStatus === value}
                disabled={saving || (value !== selectedStatus && !reviewStatusTransitions[status].includes(value))}
                onClick={() => choose(value)}>
                {reviewStatusLabels[value]}
              </button>
            ))}
          </div>
          {selectedStatus === 'READY_FOR_REVIEW' && review && (
            <div className="meaning-existence-check">
              <p><strong>Vai ieteikums attiecas uz esošu izskatīšanu, vai jāsāk jauna?</strong></p>
              <DuplicateReviewCombobox
                excludedCaseId={review.id}
                initialQuery={review.reviewedTerm}
                currentCase={review}
                onSelect={(targetId) => onDuplicate(review.sources[0].id, targetId)}
              />
            </div>
          )}
        </fieldset>
      </details>
      {!expanded && selectedStatus && (
        <p className="muted">
          {reviewStatusLabels[selectedStatus]}
        </p>
      )}
      {error && !confirmingTrash && <p className="error" role="alert">{error}</p>}
      {confirmingTrash && (
        <ConfirmationDialog
          message="Ieteikums tiks atzīmēts kā “Atkritums”."
          busy={saving} error={error}
          onConfirm={() => saveStatus('GARBAGE')}
          onCancel={() => setConfirmingTrash(false)}
        />
      )}
    </section>
  )
}
