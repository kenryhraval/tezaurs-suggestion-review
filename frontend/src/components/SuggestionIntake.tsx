import { useState } from 'react'
import type { Review, SuggestionStatus } from '../api/types'
import { suggestionStatusLabels, suggestionStatusTransitions } from '../review/statuses'
import { ConfirmationDialog } from './ConfirmationDialog'
import { DuplicateReviewCombobox } from './DuplicateReviewCombobox'

const intakeChoices: SuggestionStatus[] = ['GARBAGE', 'READY_FOR_REVIEW']

type Props = {
  status: SuggestionStatus
  defaultOpen: boolean
  readOnly: boolean
  onStatusChange: (status: SuggestionStatus) => Promise<Review | null>
  review: Review | null
  onDuplicate: (sourceId: number, caseId: string) => Promise<void>
}

export function SuggestionIntake({ status, onStatusChange, review, onDuplicate, defaultOpen, readOnly }: Props) {
  const [expanded, setExpanded] = useState(defaultOpen)
  const [pendingStatus, setPendingStatus] = useState<SuggestionStatus | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  let selectedStatus: SuggestionStatus | null = null
  if (status === 'GARBAGE') {
    selectedStatus = 'GARBAGE'
  } else if (status !== 'NEW') {
    selectedStatus = 'READY_FOR_REVIEW'
  }
  const caseSelected = review !== null && status !== 'GARBAGE' && status !== 'READY_FOR_REVIEW'

  function choose(nextStatus: SuggestionStatus) {
    if (nextStatus === selectedStatus) {
      return
    }
    setError('')
    setPendingStatus(nextStatus)
  }

  async function confirm() {
    if (!pendingStatus || saving) {
      return
    }
    setSaving(true)
    try {
      const updatedReview = await onStatusChange(pendingStatus)
      if (!updatedReview) {
        setError('Neizdevās mainīt statusu. Mēģiniet vēlreiz.')
        return
      }
      setPendingStatus(null)
      setExpanded(pendingStatus === 'READY_FOR_REVIEW')
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt statusu.')
    } finally {
      setSaving(false)
    }
  }

  async function startCase() {
    const updated = await onStatusChange('IN_PROGRESS')
    if (updated) {
      setExpanded(false)
    }
    return updated !== null
  }

  return (
    <section className="initial-review">
      <details open={expanded} onToggle={(event) => setExpanded(event.currentTarget.open)}>
        <summary className={(caseSelected || status === 'GARBAGE') && !expanded ? 'confirmed' : undefined}>
          <strong>1. Ieteikuma pirmapstrāde</strong>
        </summary>
        <p className="muted">Vai ieteikums ir skatāms vai atkritums?</p>
        <fieldset className="review-controls" disabled={readOnly}>
          <div className="tezaurs-answer-actions">
            {intakeChoices.map((value) => (
              <button key={value} type="button" aria-pressed={selectedStatus === value}
                disabled={saving || (value !== selectedStatus && !suggestionStatusTransitions[status].includes(value))}
                onClick={() => choose(value)}>
                {suggestionStatusLabels[value]}
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
                onCreate={startCase}
              />
            </div>
          )}
        </fieldset>
      </details>
      {!expanded && selectedStatus && (
        <p className="muted">
          {suggestionStatusLabels[selectedStatus]}
        </p>
      )}
      {pendingStatus && (
        <ConfirmationDialog
          message={`Ieteikums tiks atzīmēts kā “${suggestionStatusLabels[pendingStatus]}”.`}
          busy={saving} error={error}
          onConfirm={() => confirm()}
          onCancel={() => setPendingStatus(null)}
        />
      )}
    </section>
  )
}
