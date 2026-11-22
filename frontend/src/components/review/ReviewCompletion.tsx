import { useState } from 'react'
import type { Review } from '../../api/types'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'

type CompletionStatus = 'NEEDS_EXPERT' | 'COMPLETED'

type Props = {
  busy: boolean
  onStatusChange: (status: CompletionStatus) => Promise<Review>
}

export function ReviewCompletion({ busy, onStatusChange }: Props) {
  const [statusAwaitingConfirmation, setStatusAwaitingConfirmation] = useState<CompletionStatus | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const isBusy = busy || saving

  function requestStatusChange(nextStatus: CompletionStatus) {
    setError('')
    setStatusAwaitingConfirmation(nextStatus)
  }

  function cancelConfirmation() {
    setStatusAwaitingConfirmation(null)
    setError('')
  }

  async function confirmStatusChange() {
    if (!statusAwaitingConfirmation || isBusy) {
      return
    }
    setSaving(true)
    setError('')
    try {
      await onStatusChange(statusAwaitingConfirmation)
      setStatusAwaitingConfirmation(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt stāvokli.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <details className="completion-section review-section" open>
        <summary className="section-title" aria-disabled="true" onClick={(event) => event.preventDefault()}>
          6. Darba pabeigšana
        </summary>
        <p className="muted">Vai uzskatāt, ka nepieciešams eksperta apstiprinājums?</p>
        <div className="tezaurs-answer-actions">
          <button className="btn btn-sm btn-outline-secondary" type="button" disabled={isBusy}
            onClick={() => requestStatusChange('NEEDS_EXPERT')}>Jā, vajag</button>
          <button className="btn btn-sm btn-outline-secondary" type="button" disabled={isBusy}
            onClick={() => requestStatusChange('COMPLETED')}>Nē, nevajag</button>
        </div>
      </details>
      {statusAwaitingConfirmation && <ConfirmationDialog
        message={statusAwaitingConfirmation === 'NEEDS_EXPERT'
          ? 'Perēklis pāries stāvoklī “Vajag ekspertu”. Saglabātais melnraksts paliks pieejams.'
          : 'Perēklis tiks pabeigts un atzīmēta kā “Gatavs”.'}
        busy={isBusy} error={error}
        onConfirm={confirmStatusChange} onCancel={cancelConfirmation} />}
    </>
  )
}
