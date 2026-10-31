import { useState } from 'react'
import type { Review, SuggestionStatus } from '../types'
import { suggestionStatusLabels, suggestionStatusTransitions } from '../labels'
import { ConfirmationDialog } from './ConfirmationDialog'
import { DuplicateReviewCombobox } from './DuplicateReviewCombobox'

type Props = {
  status: SuggestionStatus
  onStatusChange: (status: SuggestionStatus) => Promise<Review | null>
  review: Review | null
  needsCaseChoice: boolean
  onDuplicate: (sourceId: number, caseId: string) => Promise<void>
}

export function SuggestionIntake({ status, onStatusChange, review, needsCaseChoice, onDuplicate }: Props) {
  const [expanded, setExpanded] = useState(status === 'NEW' || needsCaseChoice)
  const [pendingStatus, setPendingStatus] = useState<SuggestionStatus | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const selectedStatus = status === 'NEW' ? null : status === 'GARBAGE' ? 'GARBAGE' : 'READY_FOR_REVIEW'
  const caseSelected = review !== null && status !== 'GARBAGE' && !needsCaseChoice

  function choose(nextStatus: SuggestionStatus) {
    if (nextStatus === selectedStatus) {
      return
    }
    setError('')
    setPendingStatus(nextStatus)
  }

  async function confirm() {
    if (!pendingStatus || saving) return
    setSaving(true)
    try {
      if (!await onStatusChange(pendingStatus)) {
        setError('Neizdevās mainīt statusu. Mēģiniet vēlreiz.')
        return
      }
      setPendingStatus(null)
      setExpanded(pendingStatus === 'READY_FOR_REVIEW')
    } finally {
      setSaving(false)
    }
  }

  async function startCase() {
    const updated = await onStatusChange('IN_PROGRESS')
    if (updated) setExpanded(false)
    return updated !== null
  }

  return (
    <section className="initial-review">
      <details open={expanded} onToggle={(event) => setExpanded(event.currentTarget.open)}>
        <summary className={(caseSelected || status === 'GARBAGE') && !expanded ? 'confirmed' : undefined}>
          <strong>1. Ieteikuma pirmapstrāde</strong>
        </summary>
        <p className="muted">Vai ieteikums ir skatāms vai atkritums?</p>
        <div className="tezaurs-answer-actions">
          {(['GARBAGE', 'READY_FOR_REVIEW'] as const).map((value) => (
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
              selectedCase={caseSelected ? review : null}
              onSelect={(targetId) => onDuplicate(review.sources[0].id, targetId)}
              onCreate={startCase}
            />
          </div>
        )}
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
          onConfirm={() => void confirm()}
          onCancel={() => setPendingStatus(null)}
        />
      )}
    </section>
  )
}
